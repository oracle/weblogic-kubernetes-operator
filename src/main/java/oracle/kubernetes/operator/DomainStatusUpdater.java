// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.joda.time.DateTime;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodStatus;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainCondition;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainStatus;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Updates for status of Domain.  This class has two modes: 1) Watching for Pod state changes by listening to events from {@link PodWatcher}
 * and 2) Factory for {@link Step}s that the main processing flow can use to explicitly set the condition to Progressing or Failed.
 * 
 */
public class DomainStatusUpdater implements PodStateListener {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private static final String AVAILABLE_TYPE = "Available";
  private static final String PROGRESSING_TYPE = "Progressing";
  private static final String FAILED_TYPE = "Failed";
  
  private static final String TRUE = "True";
  private static final String FALSE = "False";
  private static final String UNKNOWN = "Unknown";
  
  private final Engine engine;
  private final DomainPresenceInfo info;
  
  private final ReentrantLock lock = new ReentrantLock();
  private Fiber fiber = null;
  private State state = null;

  /**
   * Constructs status updater for given domain presence.  All status updates are done in the background
   * using the supplied engine.
   * @param engine Engine
   * @param info Domain presence
   */
  public DomainStatusUpdater(Engine engine, DomainPresenceInfo info) {
    this.engine = engine;
    this.info = info;
  }
  
  private static class State {
    private final Map<String, Boolean> knownReadyState;
    private final Map<String, V1PodStatus> failedPods;
    
    private State(Map<String, Boolean> knownReadyState, Map<String, V1PodStatus> failedPods) {
      this.knownReadyState = knownReadyState;
      this.failedPods = failedPods;
    }
  }
  
  @Override
  public void onStateChange(Map<String, Boolean> knownReadyState, Map<String, V1PodStatus> failedPods) {
    lock.lock();
    try {
      if (fiber == null) {
        fiber = engine.createFiber();
        fiber.start(new StatusUpdateStep(knownReadyState, failedPods, null), new Packet(), new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            lock.lock();
            try {
              fiber = null;
              State s = state;
              state = null;
              if (s != null) {
                onStateChange(s.knownReadyState, s.failedPods);
              }
            } finally {
              lock.unlock();
            }
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);
            onCompletion(packet);
          }
        });

      } else {
        state = new State(knownReadyState, failedPods);
      }
    } finally {
      lock.unlock();
    }
  }
  
  private class StatusUpdateStep extends Step {
    private final Map<String, Boolean> knownReadyState;
    private final Map<String, V1PodStatus> failedPods;
    
    public StatusUpdateStep(Map<String, Boolean> knownReadyState, Map<String, V1PodStatus> failedPods, Step next) {
      super(next);
      this.knownReadyState = knownReadyState;
      this.failedPods = failedPods;
    }
    
    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      DateTime now = DateTime.now();
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        // If this is the first time, create status
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
      }
  
      // Based on the servers we intend to start and the current Pods known to be Ready or Failed,
      // we will build the list of available & unavailable servers and clusters
      List<String> availableServers = new ArrayList<>();
      List<String> unavailableServers = new ArrayList<>();
      List<String> availableClusters = new ArrayList<>();
      List<String> unavailableClusters = new ArrayList<>();
      
      // Known ready servers are available
      for (Map.Entry<String, Boolean> entry : knownReadyState.entrySet()) {
        if (Boolean.TRUE.equals(entry.getValue())) {
          availableServers.add(entry.getKey());
        }
      }
      
      String asName = spec.getAsName();
      if (asName != null) {
        if (Boolean.TRUE.equals(knownReadyState.get(asName))) {
          if (!availableServers.contains(asName)) {
            availableServers.add(asName);
          }
        } else {
          unavailableServers.add(asName);
        }
      }
      
      // Iterate over servers we current intend to start (actual start has already begun asynchronously)
      Collection<ServerStartupInfo> ssic = info.getServerStartupInfo();
      if (ssic != null) {
        for (ServerStartupInfo ssi : ssic) {
          String serverName = ssi.serverConfig.getName();
          if (Boolean.TRUE.equals(knownReadyState.get(serverName))) {
            if (!availableServers.contains(serverName)) {
              availableServers.add(serverName);
            }
            if (ssi.clusterConfig != null) {
              String clusterName = ssi.clusterConfig.getClusterName();
              if (!availableClusters.contains(clusterName)) {
                availableClusters.add(clusterName);
              }
            }
          } else {
            unavailableServers.add(serverName);
            if (ssi.clusterConfig != null) {
              String clusterName = ssi.clusterConfig.getClusterName();
              if (!unavailableClusters.contains(clusterName)) {
                unavailableClusters.add(clusterName);
              }
              if (availableClusters.contains(clusterName)) {
                availableClusters.remove(clusterName);
              }
            }
          }
        }
      }
      
      status.setAvailableServers(availableServers);
      status.setUnavailableServers(unavailableServers);
      status.setAvailableClusters(availableClusters);
      status.setUnavailableClusters(unavailableClusters);
      
      // Now, we'll build the conditions.
      // Possible condition types are Progressing, Available, and Failed
      // Each condition is either True, False, or Unknown
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }
      
      if (failedPods != null && !failedPods.isEmpty()) {
        // If we have failed pods, then the domain status is Failed
        ListIterator<DomainCondition> it = conditions.listIterator();
        boolean foundFailed = false;
        while (it.hasNext()) {
          DomainCondition dc = it.next();
          switch (dc.getType()) {
          case FAILED_TYPE:
            foundFailed = true;
            if (!TRUE.equals(dc.getStatus())) {
              dc.setStatus(TRUE);
              dc.setReason("PodFailed");
              dc.setLastTransitionTime(now);
            }
            break;
          case PROGRESSING_TYPE:
          case AVAILABLE_TYPE:
          default:
            it.remove();
          }
        }
        if (!foundFailed) {
          DomainCondition dc = new DomainCondition();
          dc.setType(FAILED_TYPE);
          dc.setStatus(TRUE);
          dc.setReason("PodFailed");
          dc.setLastTransitionTime(now);
          conditions.add(dc);
        }
      } else if (ssic != null && !availableServers.isEmpty() && unavailableServers.isEmpty() && unavailableClusters.isEmpty()) {
        // Next see if we have available servers, but no unavailable servers or clusters -- if so, we are Available=True
        ListIterator<DomainCondition> it = conditions.listIterator();
        boolean foundAvailable = false;
        while (it.hasNext()) {
          DomainCondition dc = it.next();
          switch (dc.getType()) {
          case AVAILABLE_TYPE:
            foundAvailable = true;
            if (!TRUE.equals(dc.getStatus())) {
              dc.setStatus(TRUE);
              dc.setReason("ServersReady");
              dc.setLastTransitionTime(now);
            }
            break;
          case PROGRESSING_TYPE:
          case FAILED_TYPE:
          default:
            it.remove();
          }
        }
        if (!foundAvailable) {
          DomainCondition dc = new DomainCondition();
          dc.setType(AVAILABLE_TYPE);
          dc.setStatus(TRUE);
          dc.setReason("ServersReady");
          dc.setLastTransitionTime(now);
          conditions.add(dc);
        }
      } else {
        // Else, we are Progressing
        ListIterator<DomainCondition> it = conditions.listIterator();
        boolean foundProgressing = false;
        while (it.hasNext()) {
          DomainCondition dc = it.next();
          switch (dc.getType()) {
          case PROGRESSING_TYPE:
            foundProgressing = true;
            if (!TRUE.equals(dc.getStatus())) {
              dc.setStatus(TRUE);
              dc.setReason(availableServers.isEmpty() ? "AdminServerStarting" : "ManagedServersStarting");
              dc.setLastTransitionTime(now);
            }
            break;
          case AVAILABLE_TYPE:
          case FAILED_TYPE:
          default:
            it.remove();
          }
        }
        if (!foundProgressing) {
          DomainCondition dc = new DomainCondition();
          dc.setType(PROGRESSING_TYPE);
          dc.setStatus(TRUE);
          dc.setReason(availableServers.isEmpty() ? "AdminServerStarting" : "ManagedServersStarting");
          dc.setLastTransitionTime(now);
          conditions.add(dc);
        }
      }
  
      LOGGER.info(MessageKeys.DOMAIN_STATUS, spec.getDomainUID(), availableServers, availableClusters, unavailableServers, unavailableClusters, conditions);
      Step s = CallBuilder.create().replaceDomainAsync(meta.getName(), meta.getNamespace(), dom, new ResponseStep<Domain>(null) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND || statusCode == CallBuilder.CONFLICT) {
            return doNext(packet); // Just ignore update
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }
        
        @Override
        public NextAction onSuccess(Packet packet, Domain result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          info.setDomain(result);
          return doNext(packet);
        }
      });
      
      LOGGER.exiting();
      return doNext(s, packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Progressing
   * @param next Next step
   * @param isPreserveAvailable true, if existing Available=True condition should be preserved
   * @return Step
   */
  public static Step createProgressingStep(Step next, boolean isPreserveAvailable) {
    return new ProgressingHookStep(next, isPreserveAvailable);
  }
  
  private static class ProgressingHookStep extends Step {
    private final boolean isPreserveAvailable;
    
    private ProgressingHookStep(Step next, boolean isPreserveAvailable) {
      super(next);
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().owner.createFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(new ProgressingStep(isPreserveAvailable), p, new CompletionCallback() {
        @Override
        public void onCompletion(Packet packet) {
        }

        @Override
        public void onThrowable(Packet packet, Throwable throwable) {
          LOGGER.severe(MessageKeys.EXCEPTION, throwable);
        }
      });
      
      return doNext(packet);
    }
  }
  
  private static class ProgressingStep extends Step {
    private final boolean isPreserveAvailable;

    private ProgressingStep(boolean isPreserveAvailable) {
      super(null);
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
      }
      
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }

      ListIterator<DomainCondition> it = conditions.listIterator();
      boolean foundProgressing = false;
      while (it.hasNext()) {
        DomainCondition dc = it.next();
        switch (dc.getType()) {
        case PROGRESSING_TYPE:
          foundProgressing = true;
          if (!TRUE.equals(dc.getStatus())) {
            dc.setStatus(TRUE);
            dc.setLastTransitionTime(now);
          }
          break;
        case AVAILABLE_TYPE:
          if (isPreserveAvailable) {
            break;
          }
        case FAILED_TYPE:
        default:
          it.remove();
        }
      }
      if (!foundProgressing) {
        DomainCondition dc = new DomainCondition();
        dc.setType(PROGRESSING_TYPE);
        dc.setStatus(TRUE);
        dc.setLastTransitionTime(now);
        conditions.add(dc);
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      
      return doNext(CallBuilder.create().replaceDomainAsync(meta.getName(), meta.getNamespace(), dom, new ResponseStep<Domain>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND || statusCode == CallBuilder.CONFLICT) {
            return doNext(packet); // Just ignore update
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }
        
        @Override
        public NextAction onSuccess(Packet packet, Domain result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          info.setDomain(result);
          return doNext(packet);
        }
      }), packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Failed
   * @param throwable Throwable that caused failure
   * @param next Next step
   * @return Step
   */
  public static Step createFailedStep(Throwable throwable, Step next) {
    return new FailedHookStep(throwable, next);
  }
  
  private static class FailedHookStep extends Step {
    private final Throwable throwable;
    
    private FailedHookStep(Throwable throwable, Step next) {
      super(next);
      this.throwable = throwable;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().owner.createFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(new FailedStep(throwable), p, new CompletionCallback() {
        @Override
        public void onCompletion(Packet packet) {
        }

        @Override
        public void onThrowable(Packet packet, Throwable throwable) {
          LOGGER.severe(MessageKeys.EXCEPTION, throwable);
        }
      });
      
      return doNext(packet);
    }
  }
  
  private static class FailedStep extends Step {
    private final Throwable throwable;

    private FailedStep(Throwable throwable) {
      super(null);
      this.throwable = throwable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
      }
      
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }

      ListIterator<DomainCondition> it = conditions.listIterator();
      boolean foundFailed = false;
      while (it.hasNext()) {
        DomainCondition dc = it.next();
        switch (dc.getType()) {
        case FAILED_TYPE:
          foundFailed = true;
          if (!TRUE.equals(dc.getStatus())) {
            dc.setStatus(TRUE);
            dc.setReason("Exception");
            dc.setMessage(throwable.getMessage());
            dc.setLastTransitionTime(now);
          }
          break;
        case AVAILABLE_TYPE:
        case PROGRESSING_TYPE:
        default:
          it.remove();
        }
      }
      if (!foundFailed) {
        DomainCondition dc = new DomainCondition();
        dc.setType(FAILED_TYPE);
        dc.setStatus(TRUE);
        dc.setReason("Exception");
        dc.setMessage(throwable.getMessage());
        dc.setLastTransitionTime(now);
        conditions.add(dc);
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      
      return doNext(CallBuilder.create().replaceDomainAsync(meta.getName(), meta.getNamespace(), dom, new ResponseStep<Domain>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND || statusCode == CallBuilder.CONFLICT) {
            return doNext(packet); // Just ignore update
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }
        
        @Override
        public NextAction onSuccess(Packet packet, Domain result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          info.setDomain(result);
          return doNext(packet);
        }
      }), packet);
    }
  }
}
