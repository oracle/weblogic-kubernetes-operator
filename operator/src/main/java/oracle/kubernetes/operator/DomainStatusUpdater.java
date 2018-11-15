// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainCondition;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.DomainStatus;
import oracle.kubernetes.weblogic.domain.v2.ServerHealth;
import oracle.kubernetes.weblogic.domain.v2.ServerStatus;
import org.joda.time.DateTime;

/**
 * Updates for status of Domain. This class has two modes: 1) Watching for Pod state changes by
 * listening to events from {@link PodWatcher} and 2) Factory for {@link Step}s that the main
 * processing flow can use to explicitly set the condition to Progressing or Failed.
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

  private DomainStatusUpdater() {}

  /**
   * Asynchronous step to set Domain status to indicate WebLogic server status
   *
   * @param timeoutSeconds Timeout in seconds
   * @param next Next step
   * @return Step
   */
  public static Step createStatusStep(int timeoutSeconds, Step next) {
    return new StatusUpdateHookStep(timeoutSeconds, next);
  }

  private static class StatusUpdateHookStep extends Step {
    private final int timeoutSeconds;

    public StatusUpdateHookStep(int timeoutSeconds, Step next) {
      super(next);
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      return doNext(
          ServerStatusReader.createDomainStatusReaderStep(
              info, timeoutSeconds, new StatusUpdateStep(getNext())),
          packet);
    }
  }

  private static class StatusUpdateStep extends Step {
    public StatusUpdateStep(Step next) {
      super(next);
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

      List<ServerStatus> existingServerStatuses = null;
      if (status == null) {
        // If this is the first time, create status
        status = new DomainStatus();
        status.setStartTime(now);
        dom.setStatus(status);
        madeChange = true;
      } else {
        existingServerStatuses = status.getServers();
      }

      // Acquire current state
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverState =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);

      @SuppressWarnings("unchecked")
      ConcurrentMap<String, ServerHealth> serverHealth =
          (ConcurrentMap<String, ServerHealth>) packet.get(ProcessingConstants.SERVER_HEALTH_MAP);

      Map<String, ServerStatus> serverStatuses = new TreeMap<>();
      WlsDomainConfig scan = info.getScan();
      if (scan != null) {
        for (Map.Entry<String, WlsServerConfig> entry : scan.getServerConfigs().entrySet()) {
          String serverName = entry.getKey();
          ServerStatus ss =
              new ServerStatus()
                  .withState(serverState.getOrDefault(serverName, WebLogicConstants.SHUTDOWN_STATE))
                  .withServerName(serverName)
                  .withHealth(serverHealth.get(serverName));
          outer:
          for (Map.Entry<String, WlsClusterConfig> cluster : scan.getClusterConfigs().entrySet()) {
            for (WlsServerConfig sic : cluster.getValue().getServerConfigs()) {
              if (serverName.equals(sic.getName())) {
                ss.setClusterName(cluster.getKey());
                break outer;
              }
            }
          }
          ServerKubernetesObjects sko = info.getServers().get(serverName);
          if (sko != null) {
            V1Pod pod = sko.getPod().get();
            if (pod != null) {
              ss.setNodeName(pod.getSpec().getNodeName());
            }
          }
          serverStatuses.put(serverName, ss);
        }
      }
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        String serverName = entry.getKey();
        if (!serverStatuses.containsKey(serverName)) {
          V1Pod pod = entry.getValue().getPod().get();
          if (pod != null) {
            ServerStatus ss =
                new ServerStatus()
                    .withState(
                        serverState.getOrDefault(serverName, WebLogicConstants.SHUTDOWN_STATE))
                    .withClusterName(
                        pod.getMetadata().getLabels().get(LabelConstants.CLUSTERNAME_LABEL))
                    .withNodeName(pod.getSpec().getNodeName())
                    .withServerName(serverName)
                    .withHealth(serverHealth.get(serverName));
            serverStatuses.put(serverName, ss);
          }
        }
      }

      if (existingServerStatuses != null) {
        if (!compare(existingServerStatuses, serverStatuses)) {
          status.setServers(new ArrayList<>(serverStatuses.values()));
          madeChange = true;
        }
      } else if (!serverStatuses.isEmpty()) {
        status.setServers(new ArrayList<>(serverStatuses.values()));
        madeChange = true;
      }

      // Now, we'll build the conditions.
      // Possible condition types are Progressing, Available, and Failed
      // Each condition is either True, False, or Unknown
      List<DomainCondition> conditions = status.getConditions();
      if (conditions == null) {
        conditions = new ArrayList<>();
        status.setConditions(conditions);
      }

      boolean haveFailedPod = false;
      boolean allIntendedPodsToRunning = true;
      Collection<String> serversIntendedToRunning = new ArrayList<>();
      Collection<ServerStartupInfo> ssic = info.getServerStartupInfo();
      if (ssic != null) {
        for (ServerStartupInfo ssi : ssic) {
          if (!"ADMIN".equals(ssi.getDesiredState())) {
            continue;
          }
          serversIntendedToRunning.add(ssi.serverConfig.getName());
        }
      }
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        ServerKubernetesObjects sko = entry.getValue();
        if (sko != null) {
          V1Pod existingPod = sko.getPod().get();
          if (existingPod != null) {
            if (PodWatcher.isFailed(existingPod)) {
              haveFailedPod = true;
            }
            String serverName = entry.getKey();
            if (serversIntendedToRunning.contains(serverName)) {
              ServerStatus ss = serverStatuses.get(serverName);
              if (ss == null || !WebLogicConstants.RUNNING_STATE.equals(ss.getState())) {
                allIntendedPodsToRunning = false;
              }
            }
          }
        }
      }

      boolean foundFailed = false;
      boolean foundAvailable = false;
      ListIterator<DomainCondition> it = conditions.listIterator();
      while (it.hasNext()) {
        DomainCondition dc = it.next();
        switch (dc.getType()) {
          case FAILED_TYPE:
            if (haveFailedPod) {
              foundFailed = true;
              if (!TRUE.equals(dc.getStatus())) {
                dc.setStatus(TRUE);
                dc.setReason("PodFailed");
                dc.setLastTransitionTime(now);
                madeChange = true;
              }
            } else {
              it.remove();
              madeChange = true;
            }
            break;
          case PROGRESSING_TYPE:
            if (haveFailedPod || allIntendedPodsToRunning) {
              it.remove();
              madeChange = true;
            }
            break;
          case AVAILABLE_TYPE:
            if (haveFailedPod) {
              it.remove();
              madeChange = true;
            } else if (allIntendedPodsToRunning) {
              foundAvailable = true;
              if (!TRUE.equals(dc.getStatus())
                  || !SERVERS_READY_AVAILABLE_REASON.equals(dc.getReason())) {
                dc.setStatus(TRUE);
                dc.setReason(SERVERS_READY_AVAILABLE_REASON);
                dc.setLastTransitionTime(now);
                madeChange = true;
              }
            }
            break;
          default:
            it.remove();
            madeChange = true;
            break;
        }
      }
      if (haveFailedPod && !foundFailed) {
        DomainCondition dc = new DomainCondition();
        dc.setType(FAILED_TYPE);
        dc.setStatus(TRUE);
        dc.setReason("PodFailed");
        dc.setLastTransitionTime(now);
        conditions.add(dc);
        madeChange = true;
      }
      if (allIntendedPodsToRunning && !foundAvailable) {
        DomainCondition dc = new DomainCondition();
        dc.setType(AVAILABLE_TYPE);
        dc.setStatus(TRUE);
        dc.setReason(SERVERS_READY_AVAILABLE_REASON);
        dc.setLastTransitionTime(now);
        conditions.add(dc);
        madeChange = true;
      }

      // This will control if we need to re-check states soon or if we can slow down
      // checks
      packet.put(ProcessingConstants.STATUS_UNCHANGED, Boolean.valueOf(!madeChange));

      if (madeChange) {
        LOGGER.info(MessageKeys.DOMAIN_STATUS, spec.getDomainUID(), status);
      }
      LOGGER.exiting();

      return madeChange == true
          ? doDomainUpdate(dom, info, packet, StatusUpdateStep.this, getNext())
          : doNext(packet);
    }
  }

  private static boolean compare(
      List<ServerStatus> currentServerStatuses, Map<String, ServerStatus> serverStatuses) {
    if (currentServerStatuses.size() == serverStatuses.size()) {
      for (ServerStatus c : currentServerStatuses) {
        String serverName = c.getServerName();
        ServerStatus u = serverStatuses.get(serverName);
        if (u == null || !c.equals(u)) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  /**
   * Asynchronous step to set Domain condition to Progressing
   *
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
      f.start(
          new ProgressingStep(reason, isPreserveAvailable),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

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

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status);
      LOGGER.exiting();

      return madeChange == true
          ? doDomainUpdate(dom, info, packet, ProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition end Progressing and set Available, if needed
   *
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

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status);
      LOGGER.exiting();

      return madeChange == true
          ? doDomainUpdate(dom, info, packet, EndProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Available
   *
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
      f.start(
          new AvailableStep(reason),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

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

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status);
      LOGGER.exiting();
      return madeChange == true
          ? doDomainUpdate(dom, info, packet, AvailableStep.this, getNext())
          : doNext(packet);
    }
  }

  private static NextAction doDomainUpdate(
      Domain dom, DomainPresenceInfo info, Packet packet, Step conflictStep, Step next) {
    V1ObjectMeta meta = dom.getMetadata();
    NextAction na = new NextAction();
    na.invoke(
        new CallBuilder()
            .replaceDomainAsync(
                meta.getName(),
                meta.getNamespace(),
                dom,
                new DefaultResponseStep<Domain>(next) {
                  @Override
                  public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
                    if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
                      return doNext(packet); // Just ignore update
                    }
                    return super.onFailure(
                        getRereadDomainConflictStep(info, meta, conflictStep),
                        packet,
                        callResponse);
                  }

                  @Override
                  public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                    info.setDomain(callResponse.getResult());
                    return doNext(packet);
                  }
                }),
        packet);
    return na;
  }

  private static Step getRereadDomainConflictStep(
      DomainPresenceInfo info, V1ObjectMeta meta, Step next) {
    return new CallBuilder()
        .readDomainAsync(
            meta.getName(),
            meta.getNamespace(),
            new DefaultResponseStep<Domain>(next) {
              @Override
              public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                info.setDomain(callResponse.getResult());
                return doNext(packet);
              }
            });
  }

  /**
   * Asynchronous step to set Domain condition to Failed
   *
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
      f.start(
          new FailedStep(throwable),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

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

      LOGGER.info(MessageKeys.DOMAIN_STATUS, dom.getSpec().getDomainUID(), status);
      LOGGER.exiting();

      return madeChange == true
          ? doDomainUpdate(dom, info, packet, FailedStep.this, getNext())
          : doNext(packet);
    }
  }
}
