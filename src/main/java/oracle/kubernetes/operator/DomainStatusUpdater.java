// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.joda.time.DateTime;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainCondition;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.DomainStatus;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
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
public class DomainStatusUpdater {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  public static final String INSPECTING_DOMAIN_PROGRESS_REASON = "InspectingDomainPrescence";
  public static final String ADMIN_SERVER_STARTING_PROGRESS_REASON = "AdminServerStarting";
  public static final String MANAGED_SERVERS_STARTING_PROGRESS_REASON = "ManagedServersStarting";

  public static final String SERVERS_READY_AVAILABLE_REASON = "ServersReady";
  public static final String ALL_STOPPED_AVAILABLE_REASON = "AllServersStopped";

  private static final String AVAILABLE_TYPE = "Available";
  private static final String PROGRESSING_TYPE = "Progressing";
  private static final String FAILED_TYPE = "Failed";
  
  private static final String TRUE = "True";
  private static final String FALSE = "False";
  
  private DomainStatusUpdater() {
  }
    
  /**
   * Asynchronous step to set Domain status to indicate pod availability
   * @param pod The pod
   * @param isDelete true, if the pod was just deleted
   * @param next Next step
   * @return Step
   */
  public static Step createStatusStep(V1Pod pod, boolean isDelete, Step next) {
    return new StatusUpdateStep(pod, isDelete, next);
  }
  
  private static class StatusUpdateStep extends Step {
    private final V1Pod pod;
    private final boolean isDelete;
    
    public StatusUpdateStep(V1Pod pod, boolean isDelete, Step next) {
      super(next);
      this.pod = pod;
      this.isDelete = isDelete;
    }
    
    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      boolean madeChange = false;
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        // If this is the first time, create status
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
      }
      
      V1ObjectMeta metadata = pod.getMetadata();
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
  
      List<String> availableServers = status.getAvailableServers();
      if (availableServers == null) {
        availableServers = new ArrayList<>();
      }
      List<String> unavailableServers = status.getUnavailableServers();
      if (unavailableServers == null) {
        unavailableServers = new ArrayList<>();
      }
      List<String> availableClusters = status.getAvailableClusters();
      if (availableClusters == null) {
        availableClusters = new ArrayList<>();
      }
      List<String> unavailableClusters = status.getUnavailableClusters();
      if (unavailableClusters == null) {
        unavailableClusters = new ArrayList<>();
      }
      
      boolean failedPod = false;
      if (isDelete) {
        madeChange = availableServers.remove(serverName) || madeChange;
        madeChange = unavailableServers.remove(serverName) || madeChange;
      } else if (PodWatcher.isReady(pod)) {
        if (!availableServers.contains(serverName)) {
          availableServers.add(serverName);
          madeChange = true;
        }
        madeChange = unavailableServers.remove(serverName) || madeChange;
      } else {
        if (PodWatcher.isFailed(pod)) {
          failedPod = true;
        }
        madeChange = availableServers.remove(serverName) || madeChange;
        if (!unavailableServers.contains(serverName)) {
          unavailableServers.add(serverName);
          madeChange = true;
        }
      }
      if (clusterName != null) {
        boolean clusterAvailable = false;
        WlsDomainConfig scan = info.getScan();
        if (scan != null) {
          WlsClusterConfig clusterConfig = scan.getClusterConfig(clusterName);
          if (clusterConfig != null) {
            // if at least two cluster servers are available, then cluster is available
            int target = 2;
            // also, if only configured to start one server and at least one is available, then cluster is available
            String sc = spec.getStartupControl();
            if (sc == null) {
              sc = StartupControlConstants.AUTO_STARTUPCONTROL;
            } else {
              sc = sc.toUpperCase();
            }
            cluster:
            switch (sc) {
            case StartupControlConstants.AUTO_STARTUPCONTROL:
            case StartupControlConstants.SPECIFIED_STARTUPCONTROL:
              List<ClusterStartup> lcs = spec.getClusterStartup();
              if (lcs != null) {
                for (ClusterStartup cs : lcs) {
                  if (clusterName.equals(cs.getClusterName())) {
                    if (cs.getReplicas() < target) {
                      target = cs.getReplicas();
                    }
                  }
                  break cluster;
                }
              }
              if (StartupControlConstants.AUTO_STARTUPCONTROL.equals(sc)) {
                if (spec.getReplicas() < target) {
                  target = spec.getReplicas();
                }
              }
              break;
            default:
              break;
            }
            
            int count = 0;
            for (WlsServerConfig server : clusterConfig.getServerConfigs()) {
              if (availableServers.contains(server.getName())) {
                if (++count >= target) {
                  clusterAvailable = true;
                  break;
                }
              }
            }
          }
        }
        if (clusterAvailable) {
          if (!availableClusters.contains(clusterName)) {
            availableClusters.add(clusterName);
            madeChange = true;
          }
          madeChange = unavailableClusters.remove(clusterName) || madeChange;
        } else {
          madeChange = availableClusters.remove(clusterName) || madeChange;
          if (!unavailableClusters.contains(clusterName)) {
            unavailableClusters.add(clusterName);
            madeChange = true;
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
      
      if (isDelete) {
        // If we have a Failed condition, then we might need to clear it
        ListIterator<DomainCondition> it = conditions.listIterator();
        while (it.hasNext()) {
          DomainCondition dc = it.next();
          switch (dc.getType()) {
          case FAILED_TYPE:
            if (TRUE.equals(dc.getStatus())) {
              boolean failedFound = false;
              for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
                if (serverName.equals(entry.getKey())) {
                  continue;
                }
                if (entry.getValue() != null) {
                  V1Pod existingPod = entry.getValue().getPod().get();
                  if (existingPod != null && PodWatcher.isFailed(existingPod)) {
                    failedFound = true;
                    break;
                  }
                }
              }
              if (!failedFound) {
                it.remove();
                madeChange = true;
              }
            }
            break;
          case PROGRESSING_TYPE:
          case AVAILABLE_TYPE:
          default:
            break;
          }
        }
      } else if (failedPod) {
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
              madeChange = true;
            }
            break;
          case PROGRESSING_TYPE:
          case AVAILABLE_TYPE:
          default:
            it.remove();
            madeChange = true;
          }
        }
        if (!foundFailed) {
          DomainCondition dc = new DomainCondition();
          dc.setType(FAILED_TYPE);
          dc.setStatus(TRUE);
          dc.setReason("PodFailed");
          dc.setLastTransitionTime(now);
          conditions.add(dc);
          madeChange = true;
        }
      } else if (!availableServers.isEmpty() && unavailableServers.isEmpty()) {
        Collection<ServerStartupInfo> ssic = info.getServerStartupInfo();
        if (ssic != null) {
          boolean allServersAvailable = true;
          for (ServerStartupInfo ssi : ssic) {
            if (!availableServers.contains(ssi.serverConfig.getName())) {
              allServersAvailable = false;
              break;
            }
          }
          if (allServersAvailable) {
            ListIterator<DomainCondition> it = conditions.listIterator();
            boolean foundAvailable = false;
            while (it.hasNext()) {
              DomainCondition dc = it.next();
              switch (dc.getType()) {
              case AVAILABLE_TYPE:
                foundAvailable = true;
                if (!TRUE.equals(dc.getStatus())) {
                  dc.setStatus(TRUE);
                  dc.setReason(SERVERS_READY_AVAILABLE_REASON);
                  dc.setLastTransitionTime(now);
                  madeChange = true;
                }
                break;
              case PROGRESSING_TYPE:
              case FAILED_TYPE:
              default:
                it.remove();
                madeChange = true;
              }
            }
            if (!foundAvailable) {
              DomainCondition dc = new DomainCondition();
              dc.setType(AVAILABLE_TYPE);
              dc.setStatus(TRUE);
              dc.setReason(SERVERS_READY_AVAILABLE_REASON);
              dc.setLastTransitionTime(now);
              conditions.add(dc);
              madeChange = true;
            }
          }
        }
      }
  
      LOGGER.info(MessageKeys.DOMAIN_STATUS, spec.getDomainUID(), availableServers, availableClusters, unavailableServers, unavailableClusters, conditions);
      LOGGER.exiting();
      
      return madeChange == true ? doDomainUpdate(dom, info, packet, StatusUpdateStep.this, next) : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Progressing
   * @param reason Progressing reason
   * @param isPreserveAvailable true, if existing Available=True condition should be preserved
   * @param next Next step
   * @return Step
   */
  public static Step createProgressingStep(String reason, boolean isPreserveAvailable, Step next) {
    return new ProgressingHookStep(reason, isPreserveAvailable, next);
  }
  
  private static class ProgressingHookStep extends Step {
    private final String reason;
    private final boolean isPreserveAvailable;
    
    private ProgressingHookStep(String reason, boolean isPreserveAvailable, Step next) {
      super(next);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().createChildFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(new ProgressingStep(reason, isPreserveAvailable), p, new CompletionCallback() {
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
    private final String reason;
    private final boolean isPreserveAvailable;

    private ProgressingStep(String reason, boolean isPreserveAvailable) {
      super(null);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      boolean madeChange = false;
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
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
            madeChange = true;
          }
          if (!reason.equals(dc.getReason())) {
            dc.setReason(reason);
            madeChange = true;
          }
          break;
        case AVAILABLE_TYPE:
          if (isPreserveAvailable) {
            break;
          }
        case FAILED_TYPE:
        default:
          it.remove();
          madeChange = true;
        }
      }
      if (!foundProgressing) {
        DomainCondition dc = new DomainCondition();
        dc.setType(PROGRESSING_TYPE);
        dc.setStatus(TRUE);
        dc.setLastTransitionTime(now);
        dc.setReason(reason);
        conditions.add(dc);
        madeChange = true;
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      
      return madeChange == true ? doDomainUpdate(dom, info, packet, ProgressingStep.this, next) : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition end Progressing and set Available, if needed
   * @param next Next step
   * @return Step
   */
  public static Step createEndProgressingStep(Step next) {
    return new EndProgressingStep(next);
  }
  
  private static class EndProgressingStep extends Step {

    public EndProgressingStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      boolean madeChange = false;
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
      }
      
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }

      ListIterator<DomainCondition> it = conditions.listIterator();
      while (it.hasNext()) {
        DomainCondition dc = it.next();
        switch (dc.getType()) {
        case PROGRESSING_TYPE:
          if (TRUE.equals(dc.getStatus())) {
            it.remove();
            madeChange = true;
          }
          break;
        case AVAILABLE_TYPE:
        case FAILED_TYPE:
          break;
        default:
          it.remove();
          madeChange = true;
        }
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      
      return madeChange == true ? doDomainUpdate(dom, info, packet, EndProgressingStep.this, next) : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Available
   * @param reason Available reason
   * @param next Next step
   * @return Step
   */
  public static Step createAvailableStep(String reason, Step next) {
    return new AvailableHookStep(reason, next);
  }
  
  private static class AvailableHookStep extends Step {
    private final String reason;
    
    private AvailableHookStep(String reason, Step next) {
      super(next);
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().createChildFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(new AvailableStep(reason), p, new CompletionCallback() {
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
  
  private static class AvailableStep extends Step {
    private final String reason;

    private AvailableStep(String reason) {
      super(null);
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();
      
      boolean madeChange = false;
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
      }
      
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }

      ListIterator<DomainCondition> it = conditions.listIterator();
      boolean foundAvailable = false;
      while (it.hasNext()) {
        DomainCondition dc = it.next();
        switch (dc.getType()) {
        case AVAILABLE_TYPE:
          foundAvailable = true;
          if (!TRUE.equals(dc.getStatus())) {
            dc.setStatus(TRUE);
            dc.setLastTransitionTime(now);
            madeChange = true;
          }
          if (!reason.equals(dc.getReason())) {
            dc.setReason(reason);
            madeChange = true;
          }
          break;
        case PROGRESSING_TYPE:
          break;
        case FAILED_TYPE:
        default:
          it.remove();
          madeChange = true;
        }
      }
      if (!foundAvailable) {
        DomainCondition dc = new DomainCondition();
        dc.setType(AVAILABLE_TYPE);
        dc.setStatus(TRUE);
        dc.setLastTransitionTime(now);
        dc.setReason(reason);
        conditions.add(dc);
        madeChange = true;
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      return madeChange == true ? doDomainUpdate(dom, info, packet, AvailableStep.this, next) : doNext(packet);
    }
  }
  
  private static NextAction doDomainUpdate(Domain dom, DomainPresenceInfo info, Packet packet, Step conflictStep, Step next) {
    V1ObjectMeta meta = dom.getMetadata();
    NextAction na = new NextAction();
    na.invoke(CallBuilder.create().replaceDomainAsync(meta.getName(), meta.getNamespace(), dom, new ResponseStep<Domain>(next) {
      @Override
      public NextAction onFailure(Packet packet, ApiException e, int statusCode,
          Map<String, List<String>> responseHeaders) {
        if (statusCode == CallBuilder.NOT_FOUND) {
          return doNext(packet); // Just ignore update
        }
        return super.onFailure(conflictStep, packet, e, statusCode, responseHeaders);
      }
      
      @Override
      public NextAction onSuccess(Packet packet, Domain result, int statusCode,
          Map<String, List<String>> responseHeaders) {
        info.setDomain(result);
        return doNext(packet);
      }
    }), packet);
    return na;
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
      Fiber f = Fiber.current().createChildFiber();
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
      
      boolean madeChange = false;
      
      DateTime now = DateTime.now();
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      DomainStatus status = dom.getStatus();
      if (status == null) {
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
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
            madeChange = true;
          }
          break;
        case PROGRESSING_TYPE:
          if (!FALSE.equals(dc.getStatus())) {
            dc.setStatus(FALSE);
            dc.setLastTransitionTime(now);
            madeChange = true;
          }
          break;
        case AVAILABLE_TYPE:
          break;
        default:
          it.remove();
          madeChange = true;
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
        madeChange = true;
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status.getAvailableServers(), status.getAvailableClusters(), status.getUnavailableServers(), status.getUnavailableClusters(), conditions);
      LOGGER.exiting();
      
      return madeChange == true ? doDomainUpdate(dom, info, packet, FailedStep.this, next) : doNext(packet);
    }
  }
}
