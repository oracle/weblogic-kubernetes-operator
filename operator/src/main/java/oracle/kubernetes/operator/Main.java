// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CRDHelper;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfoManager;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjectsManager;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.steps.ConfigMapAfterStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.DomainPrescenceStep;
import oracle.kubernetes.operator.steps.ExternalAdminChannelsStep;
import oracle.kubernetes.operator.steps.ListPersistentVolumeClaimStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

/** A Kubernetes Operator for WebLogic. */
public class Main {

  private static ThreadFactory getThreadFactory() {
    return ThreadFactorySingleton.getInstance();
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final TuningParameters tuningAndConfig;

  static {
    try {
      TuningParameters.initializeInstance(getThreadFactory(), "/operator/config");
      tuningAndConfig = TuningParameters.getInstance();
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      throw new RuntimeException(e);
    }
  }

  private static final CallBuilderFactory callBuilderFactory = new CallBuilderFactory();

  private static final Container container = new Container();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("operator", container);

  static {
    container
        .getComponents()
        .put(
            ProcessingConstants.MAIN_COMPONENT_NAME,
            Component.createFor(
                ScheduledExecutorService.class,
                wrappedExecutorService,
                TuningParameters.class,
                tuningAndConfig,
                ThreadFactory.class,
                getThreadFactory(),
                callBuilderFactory));
  }

  private static final Engine engine = new Engine(wrappedExecutorService);
  private static final FiberGate FIBER_GATE = new FiberGate(engine);

  private static final ConcurrentMap<String, Boolean> initialized = new ConcurrentHashMap<>();
  private static final AtomicBoolean stopping = new AtomicBoolean(false);

  private static String principal;
  private static RestServer restServer = null;
  private static Thread livenessThread = null;
  private static Map<String, ConfigMapWatcher> configMapWatchers = new HashMap<>();
  private static Map<String, DomainWatcher> domainWatchers = new HashMap<>();
  private static Map<String, PodWatcher> podWatchers = new HashMap<>();
  private static Map<String, EventWatcher> eventWatchers = new HashMap<>();
  private static Map<String, ServiceWatcher> serviceWatchers = new HashMap<>();
  private static Map<String, IngressWatcher> ingressWatchers = new HashMap<>();
  private static KubernetesVersion version = null;

  static final String READINESS_PROBE_FAILURE_EVENT_FILTER =
      "reason=Unhealthy,type=Warning,involvedObject.fieldPath=spec.containers{weblogic-server}";

  static Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
    return DomainPresenceInfoManager.getDomainPresenceInfos();
  }

  static ServerKubernetesObjects getKubernetesObjects(String serverLegalName) {
    return ServerKubernetesObjectsManager.lookup(serverLegalName);
  }

  /**
   * Entry point
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    try (final InputStream stream = Main.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);

      String operatorVersion = buildProps.getProperty("git.build.version");
      String operatorImpl =
          buildProps.getProperty("git.branch")
              + "."
              + buildProps.getProperty("git.commit.id.abbrev");
      String operatorBuildTime = buildProps.getProperty("git.build.time");

      // print startup log message
      LOGGER.info(MessageKeys.OPERATOR_STARTED, operatorVersion, operatorImpl, operatorBuildTime);
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }

    // start liveness thread
    startLivenessThread();

    engine.getExecutor().execute(Main::begin);

    // now we just wait until the pod is terminated
    waitForDeath();

    // stop the REST server
    stopRestServer();
  }

  private static void begin() {
    // read the operator configuration
    String namespace = getOperatorNamespace();

    Collection<String> targetNamespaces =
        getTargetNamespaces(tuningAndConfig.get("targetNamespaces"), namespace);

    String serviceAccountName = tuningAndConfig.get("serviceaccount");
    if (serviceAccountName == null) {
      serviceAccountName = "default";
    }
    principal = "system:serviceaccount:" + namespace + ":" + serviceAccountName;

    LOGGER.info(MessageKeys.OP_CONFIG_NAMESPACE, namespace);
    StringBuilder tns = new StringBuilder();
    Iterator<String> it = targetNamespaces.iterator();
    while (it.hasNext()) {
      tns.append(it.next());
      if (it.hasNext()) {
        tns.append(", ");
      }
    }
    LOGGER.info(MessageKeys.OP_CONFIG_TARGET_NAMESPACES, tns.toString());
    LOGGER.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);

    try {
      // Initialize logging factory with JSON serializer for later logging
      // that includes k8s objects
      LoggingFactory.setJSON(new JSON());

      // start the REST server
      startRestServer(principal, targetNamespaces);

      // create the Custom Resource Definitions if they are not already there
      CRDHelper.checkAndCreateCustomResourceDefinition();

      try {
        HealthCheckHelper healthCheck = new HealthCheckHelper(namespace, targetNamespaces);
        version = healthCheck.performK8sVersionCheck();
        healthCheck.performNonSecurityChecks();
        healthCheck.performSecurityChecks(version);
      } catch (ApiException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
      }

      // check for any existing resources and add the watches on them
      // this would happen when the Domain was running BEFORE the Operator starts up
      LOGGER.info(MessageKeys.LISTING_DOMAINS);
      Step resourceSteps = null;
      for (String ns : targetNamespaces) {
        initialized.put(ns, Boolean.TRUE);
        resourceSteps = Step.chain(resourceSteps, readExistingResources(namespace, ns));
      }
      runSteps(resourceSteps, Main::deleteStrandedResources);

      // start periodic retry and recheck
      int recheckInterval = tuningAndConfig.getMainTuning().domainPresenceRecheckIntervalSeconds;
      engine
          .getExecutor()
          .scheduleWithFixedDelay(
              updateDomainPresenceInfos(
                  DomainPresenceInfoManager.getDomainPresenceInfos().values()),
              recheckInterval,
              recheckInterval,
              TimeUnit.SECONDS);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  static void deleteStrandedResources() {
    for (Map.Entry<String, DomainPresenceInfo> entry :
        DomainPresenceInfoManager.getDomainPresenceInfos().entrySet()) {
      String domainUID = entry.getKey();
      DomainPresenceInfo info = entry.getValue();
      if (info != null && info.getDomain() == null) {
        deleteDomainPresence(info.getNamespace(), domainUID);
      }
    }
  }

  private static void runSteps(Step firstStep) {
    runSteps(firstStep, null);
  }

  private static void runSteps(Step firstStep, Runnable completionAction) {
    engine.createFiber().start(firstStep, new Packet(), andThenDo(completionAction));
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  private static Runnable updateDomainPresenceInfos(Collection<DomainPresenceInfo> infos) {
    return () -> {
      for (DomainPresenceInfo info : infos) {
        checkAndCreateDomainPresence(info, false);
      }
    };
  }

  static Step readExistingResources(String operatorNamespace, String ns) {
    return Step.chain(
        ConfigMapHelper.createScriptConfigMapStep(operatorNamespace, ns),
        createConfigMapStep(ns),
        readExistingPods(ns),
        readExistingEvents(ns),
        readExistingServices(ns),
        readExistingIngresses(ns),
        readExistingDomains(ns));
  }

  private static Step readExistingDomains(String ns) {
    return callBuilderFactory.create().listDomainAsync(ns, new DomainListStep(ns));
  }

  private static Step readExistingIngresses(String ns) {
    return new CallBuilder()
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listIngressAsync(ns, new IngressListStep(ns));
  }

  private static Step readExistingServices(String ns) {
    return new CallBuilder()
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listServiceAsync(ns, new ServiceListStep(ns));
  }

  private static Step readExistingEvents(String ns) {
    return new CallBuilder()
        .withFieldSelector(Main.READINESS_PROBE_FAILURE_EVENT_FILTER)
        .listEventAsync(ns, new EventListStep(ns));
  }

  private static Step readExistingPods(String ns) {
    return new CallBuilder()
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listPodAsync(ns, new PodListStep(ns));
  }

  private static ConfigMapAfterStep createConfigMapStep(String ns) {
    return new ConfigMapAfterStep(ns, configMapWatchers, stopping, Main::dispatchConfigMapWatch);
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

  /**
   * Restarts the admin server, if already running
   *
   * @param principal Service principal
   * @param domainUID Domain UID
   */
  public static void doRestartAdmin(String principal, String domainUID) {
    DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, true, null, null);
      }
    }
  }

  /**
   * Restarts the listed servers, if already running. Singleton servers will be immediately
   * restarted. Clustered servers will be rolled so that the cluster maintains minimal availability,
   * if possible.
   *
   * @param principal Service principal
   * @param domainUID Domain UID
   * @param servers Servers to roll
   */
  public static void doRollingRestartServers(
      String principal, String domainUID, List<String> servers) {
    DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, false, servers, null);
      }
    }
  }

  /**
   * Restarts the listed clusters, if member servers are running. Member servers will be restarted
   * in a rolling fashion in order to maintain minimal availability, if possible.
   *
   * @param principal Service principal
   * @param domainUID Domain UID
   * @param clusters Clusters to roll
   */
  public static void doRollingRestartClusters(
      String principal, String domainUID, List<String> clusters) {
    DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
    if (info != null) {
      Domain dom = info.getDomain();
      if (dom != null) {
        doCheckAndCreateDomainPresence(dom, false, false, null, clusters);
      }
    }
  }

  private static void scheduleDomainStatusUpdating(DomainPresenceInfo info) {
    AtomicInteger unchangedCount = new AtomicInteger(0);
    AtomicReference<ScheduledFuture<?>> statusUpdater = info.getStatusUpdater();
    Runnable command =
        new Runnable() {
          public void run() {
            try {
              Runnable r = this; // resolve visibility
              Packet packet = new Packet();
              packet
                  .getComponents()
                  .put(
                      ProcessingConstants.DOMAIN_COMPONENT_NAME,
                      Component.createFor(info, version));
              MainTuning main = tuningAndConfig.getMainTuning();
              Step strategy =
                  DomainStatusUpdater.createStatusStep(main.statusUpdateTimeoutSeconds, null);
              engine
                  .createFiber()
                  .start(
                      strategy,
                      packet,
                      new CompletionCallback() {
                        @Override
                        public void onCompletion(Packet packet) {
                          Boolean isStatusUnchanged =
                              (Boolean) packet.get(ProcessingConstants.STATUS_UNCHANGED);
                          ScheduledFuture<?> existing = null;
                          if (Boolean.TRUE.equals(isStatusUnchanged)) {
                            if (unchangedCount.incrementAndGet()
                                == main.unchangedCountToDelayStatusRecheck) {
                              // slow down retries because of sufficient unchanged statuses
                              existing =
                                  statusUpdater.getAndSet(
                                      engine
                                          .getExecutor()
                                          .scheduleWithFixedDelay(
                                              r,
                                              main.eventualLongDelay,
                                              main.eventualLongDelay,
                                              TimeUnit.SECONDS));
                            }
                          } else {
                            // reset to trying after shorter delay because of changed status
                            unchangedCount.set(0);
                            existing =
                                statusUpdater.getAndSet(
                                    engine
                                        .getExecutor()
                                        .scheduleWithFixedDelay(
                                            r,
                                            main.initialShortDelay,
                                            main.initialShortDelay,
                                            TimeUnit.SECONDS));
                            if (existing != null) {
                              existing.cancel(false);
                            }
                          }
                          if (existing != null) {
                            existing.cancel(false);
                          }
                        }

                        @Override
                        public void onThrowable(Packet packet, Throwable throwable) {
                          LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                          // retry to trying after shorter delay because of exception
                          unchangedCount.set(0);
                          ScheduledFuture<?> existing =
                              statusUpdater.getAndSet(
                                  engine
                                      .getExecutor()
                                      .scheduleWithFixedDelay(
                                          r,
                                          main.initialShortDelay,
                                          main.initialShortDelay,
                                          TimeUnit.SECONDS));
                          if (existing != null) {
                            existing.cancel(false);
                          }
                        }
                      });
            } catch (Throwable t) {
              LOGGER.severe(MessageKeys.EXCEPTION, t);
            }
          }
        };

    MainTuning main = tuningAndConfig.getMainTuning();
    ScheduledFuture<?> existing =
        statusUpdater.getAndSet(
            engine
                .getExecutor()
                .scheduleWithFixedDelay(
                    command, main.initialShortDelay, main.initialShortDelay, TimeUnit.SECONDS));

    if (existing != null) {
      existing.cancel(false);
    }
  }

  private static void doCheckAndCreateDomainPresence(Domain dom) {
    doCheckAndCreateDomainPresence(dom, false, false, null, null);
  }

  @SuppressWarnings("SameParameterValue")
  private static void doCheckAndCreateDomainPresence(Domain dom, boolean explicitRecheck) {
    doCheckAndCreateDomainPresence(dom, explicitRecheck, false, null, null);
  }

  private static void doCheckAndCreateDomainPresence(
      Domain dom,
      boolean explicitRecheck,
      boolean explicitRestartAdmin,
      List<String> explicitRestartServers,
      List<String> explicitRestartClusters) {
    LOGGER.entering();

    boolean hasExplicitRestarts =
        explicitRestartAdmin || explicitRestartServers != null || explicitRestartClusters != null;

    DomainSpec spec = dom.getSpec();
    DomainPresenceControl.normalizeDomainSpec(spec);
    String domainUID = spec.getDomainUID();

    boolean existingDomain = DomainPresenceInfoManager.lookup(domainUID) != null;
    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(dom);
    // Has the spec actually changed? We will get watch events for status updates
    Domain current = info.getDomain();
    if (existingDomain && current != null) {
      if (!explicitRecheck && !hasExplicitRestarts && spec.equals(current.getSpec())) {
        // nothing in the spec has changed
        LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
        return;
      }
    }
    info.setDomain(dom);

    if (explicitRestartAdmin) {
      LOGGER.info(MessageKeys.RESTART_ADMIN_STARTING, domainUID);
      info.getExplicitRestartAdmin().set(true);
    }
    if (explicitRestartServers != null) {
      LOGGER.info(MessageKeys.RESTART_SERVERS_STARTING, domainUID, explicitRestartServers);
      info.getExplicitRestartServers().addAll(explicitRestartServers);
    }
    if (explicitRestartClusters != null) {
      LOGGER.info(MessageKeys.ROLLING_CLUSTERS_STARTING, domainUID, explicitRestartClusters);
      info.getExplicitRestartClusters().addAll(explicitRestartClusters);
    }

    checkAndCreateDomainPresence(info);
  }

  private static void checkAndCreateDomainPresence(DomainPresenceInfo info) {
    checkAndCreateDomainPresence(info, true);
  }

  private static void checkAndCreateDomainPresence(
      DomainPresenceInfo info, boolean isCausedByWatch) {
    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();
    String domainUID = spec.getDomainUID();

    String ns = dom.getMetadata().getNamespace();
    if (initialized.getOrDefault(ns, Boolean.FALSE) && !stopping.get()) {
      LOGGER.info(MessageKeys.PROCESSING_DOMAIN, domainUID);
      Step managedServerStrategy =
          bringManagedServersUp(DomainStatusUpdater.createEndProgressingStep(null));
      Step adminServerStrategy =
          bringAdminServerUp(connectToAdminAndInspectDomain(managedServerStrategy));

      Step strategy =
          DomainStatusUpdater.createProgressingStep(
              DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON,
              true,
              new DomainPrescenceStep(adminServerStrategy, managedServerStrategy));

      Packet p = new Packet();

      PodWatcher pw = podWatchers.get(ns);
      p.getComponents()
          .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info, version, pw));
      p.put(ProcessingConstants.PRINCIPAL, principal);

      CompletionCallback cc =
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {
              info.complete();
            }

            @Override
            public void onThrowable(Packet packet, Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);

              FIBER_GATE.startFiberIfLastFiberMatches(
                  domainUID,
                  Fiber.getCurrentIfSet(),
                  DomainStatusUpdater.createFailedStep(throwable, null),
                  p,
                  new CompletionCallback() {
                    @Override
                    public void onCompletion(Packet packet) {
                      // no-op
                    }

                    @Override
                    public void onThrowable(Packet packet, Throwable throwable) {
                      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                    }
                  });

              FIBER_GATE
                  .getExecutor()
                  .schedule(
                      () -> checkAndCreateDomainPresence(info, false),
                      DomainPresence.getDomainPresenceFailureRetrySeconds(),
                      TimeUnit.SECONDS);
            }
          };

      if (isCausedByWatch) {
        FIBER_GATE.startFiber(domainUID, strategy, p, cc);
      } else {
        FIBER_GATE.startFiberIfNoCurrentFiber(domainUID, strategy, p, cc);
      }

      scheduleDomainStatusUpdating(info);
    }
    LOGGER.exiting();
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  private static Step bringAdminServerUp(Step next) {
    return new ListPersistentVolumeClaimStep(
        PodHelper.createAdminPodStep(
            new BeforeAdminServiceStep(ServiceHelper.createForServerStep(next))));
  }

  private static Step connectToAdminAndInspectDomain(Step next) {
    return new WatchPodReadyAdminStep(
        podWatchers, WlsRetriever.readConfigStep(new ExternalAdminChannelsStep(next)));
  }

  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }

  private static void deleteDomainPresence(Domain dom) {
    V1ObjectMeta meta = dom.getMetadata();
    DomainSpec spec = dom.getSpec();
    String namespace = meta.getNamespace();

    String domainUID = spec.getDomainUID();

    deleteDomainPresence(namespace, domainUID);
  }

  private static void deleteDomainPresence(String namespace, String domainUID) {
    LOGGER.entering();

    DomainPresenceInfo info = DomainPresenceInfoManager.remove(domainUID);
    if (info != null) {
      DomainPresenceControl.cancelDomainStatusUpdating(info);
    }
    FIBER_GATE.startFiber(
        domainUID,
        new DeleteDomainStep(namespace, domainUID),
        new Packet(),
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            // no-op
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);
          }
        });

    LOGGER.exiting();
  }

  /**
   * Obtain the list of target namespaces
   *
   * @return the collection of target namespace names
   */
  private static Collection<String> getTargetNamespaces(String tnValue, String namespace) {
    Collection<String> targetNamespaces = new ArrayList<>();

    if (tnValue != null) {
      StringTokenizer st = new StringTokenizer(tnValue, ",");
      while (st.hasMoreTokens()) {
        targetNamespaces.add(st.nextToken().trim());
      }
    }

    // If no namespaces were found, default to the namespace of the operator
    if (targetNamespaces.isEmpty()) {
      targetNamespaces.add(namespace);
    }

    return targetNamespaces;
  }

  private static void startRestServer(String principal, Collection<String> targetNamespaces)
      throws Exception {
    restServer = new RestServer(new RestConfigImpl(principal, targetNamespaces));
    restServer.start(container);
  }

  private static void stopRestServer() {
    restServer.stop();
    restServer = null;
  }

  private static void startLivenessThread() {
    LOGGER.info(MessageKeys.STARTING_LIVENESS_THREAD);
    livenessThread = new OperatorLiveness();
    livenessThread.setDaemon(true);
    livenessThread.start();
  }

  private static void waitForDeath() {

    try {
      livenessThread.join();
    } catch (InterruptedException ignore) {
      // ignoring
    }

    stopping.set(true);
  }

  /**
   * True, if the operator is stopping
   *
   * @return Is operator stopping
   */
  public static boolean getStopping() {
    return stopping.get();
  }

  private static EventWatcher createEventWatcher(String namespace, String initialResourceVersion) {
    return EventWatcher.create(
        getThreadFactory(),
        namespace,
        READINESS_PROBE_FAILURE_EVENT_FILTER,
        initialResourceVersion,
        Main::dispatchEventWatch,
        stopping);
  }

  private static void dispatchEventWatch(Watch.Response<V1Event> item) {
    V1Event e = item.object;
    if (e != null) {
      switch (item.type) {
        case "ADDED":
        case "MODIFIED":
          onEvent(e);
          break;
        case "DELETED":
        case "ERROR":
        default:
      }
    }
  }

  private static void onEvent(V1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();
    if (ref != null) {
      String name = ref.getName();
      String message = event.getMessage();
      if (message != null) {
        if (message.contains(WebLogicConstants.READINESS_PROBE_NOT_READY_STATE)) {
          ServerKubernetesObjects sko = ServerKubernetesObjectsManager.lookup(name);
          if (sko != null) {
            int idx = message.lastIndexOf(':');
            sko.getLastKnownStatus().set(message.substring(idx + 1).trim());
          }
        }
      }
    }
  }

  private static PodWatcher createPodWatcher(String namespace, String initialResourceVersion) {
    return PodWatcher.create(
        getThreadFactory(), namespace, initialResourceVersion, Main::dispatchPodWatch, stopping);
  }

  private static void dispatchPodWatch(Watch.Response<V1Pod> item) {
    V1Pod p = item.object;
    if (p != null) {
      V1ObjectMeta metadata = p.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
        if (info != null && serverName != null) {
          ServerKubernetesObjects sko =
              ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
          if (sko != null) {
            switch (item.type) {
              case "ADDED":
                sko.getPod().set(p);
                break;
              case "MODIFIED":
                V1Pod skoPod = sko.getPod().get();
                if (skoPod != null) {
                  // If the skoPod is null then the operator deleted this pod
                  // and modifications are to the terminating pod
                  sko.getPod().compareAndSet(skoPod, p);
                }
                break;
              case "DELETED":
                sko.getLastKnownStatus().set(WebLogicConstants.SHUTDOWN_STATE);
                V1Pod oldPod = sko.getPod().getAndSet(null);
                if (oldPod != null) {
                  // Pod was deleted, but sko still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.POD_DELETED, domainUID, metadata.getNamespace(), serverName);
                  doCheckAndCreateDomainPresence(info.getDomain(), true);
                }
                break;

              case "ERROR":
              default:
            }
          }
        }
      }
    }
  }

  private static ServiceWatcher createServiceWatcher(
      String namespace, String initialResourceVersion) {
    return ServiceWatcher.create(
        getThreadFactory(),
        namespace,
        initialResourceVersion,
        Main::dispatchServiceWatch,
        stopping);
  }

  private static void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service s = item.object;
    if (s != null) {
      V1ObjectMeta metadata = s.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String channelName = metadata.getLabels().get(LabelConstants.CHANNELNAME_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
        ServerKubernetesObjects sko = null;
        if (info != null) {
          if (serverName != null) {
            sko = ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
          }
          switch (item.type) {
            case "ADDED":
              if (sko != null) {
                if (channelName != null) {
                  sko.getChannels().put(channelName, s);
                } else {
                  sko.getService().set(s);
                }
              } else if (clusterName != null) {
                info.getClusters().put(clusterName, s);
              }
              break;
            case "MODIFIED":
              if (sko != null) {
                if (channelName != null) {
                  V1Service skoService = sko.getChannels().get(channelName);
                  if (skoService != null) {
                    sko.getChannels().replace(channelName, skoService, s);
                  }
                } else {
                  V1Service skoService = sko.getService().get();
                  if (skoService != null) {
                    sko.getService().compareAndSet(skoService, s);
                  }
                }
              } else if (clusterName != null) {
                V1Service clusterService = info.getClusters().get(clusterName);
                if (clusterService != null) {
                  info.getClusters().replace(clusterName, clusterService, s);
                }
              }
              break;
            case "DELETED":
              if (sko != null) {
                if (channelName != null) {
                  V1Service oldService = sko.getChannels().remove(channelName);
                  if (oldService != null) {
                    // Service was deleted, but sko still contained a non-null entry
                    LOGGER.info(
                        MessageKeys.SERVER_SERVICE_DELETED,
                        domainUID,
                        metadata.getNamespace(),
                        serverName);
                    doCheckAndCreateDomainPresence(info.getDomain(), true);
                  }
                } else {
                  V1Service oldService = sko.getService().getAndSet(null);
                  if (oldService != null) {
                    // Service was deleted, but sko still contained a non-null entry
                    LOGGER.info(
                        MessageKeys.SERVER_SERVICE_DELETED,
                        domainUID,
                        metadata.getNamespace(),
                        serverName);
                    doCheckAndCreateDomainPresence(info.getDomain(), true);
                  }
                }
              } else if (clusterName != null) {
                V1Service oldService = info.getClusters().put(clusterName, null);
                if (oldService != null) {
                  // Service was deleted, but clusters still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.CLUSTER_SERVICE_DELETED,
                      domainUID,
                      metadata.getNamespace(),
                      clusterName);
                  doCheckAndCreateDomainPresence(info.getDomain(), true);
                }
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }

  private static IngressWatcher createIngressWatcher(
      String namespace, String initialResourceVersion) {
    return IngressWatcher.create(
        getThreadFactory(),
        namespace,
        initialResourceVersion,
        Main::dispatchIngressWatch,
        stopping);
  }

  private static void dispatchIngressWatch(Watch.Response<V1beta1Ingress> item) {
    V1beta1Ingress i = item.object;
    if (i != null) {
      V1ObjectMeta metadata = i.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo info = DomainPresenceInfoManager.lookup(domainUID);
        if (info != null && clusterName != null) {
          switch (item.type) {
            case "ADDED":
              info.getIngresses().put(clusterName, i);
              break;
            case "MODIFIED":
              V1beta1Ingress skoIngress = info.getIngresses().get(clusterName);
              if (skoIngress != null) {
                info.getIngresses().replace(clusterName, skoIngress, i);
              }
              break;
            case "DELETED":
              V1beta1Ingress oldIngress = info.getIngresses().remove(clusterName);
              if (oldIngress != null) {
                // Ingress was deleted, but sko still contained a non-null entry
                LOGGER.info(
                    MessageKeys.INGRESS_DELETED, domainUID, metadata.getNamespace(), clusterName);
                doCheckAndCreateDomainPresence(info.getDomain(), true);
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }

  private static void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item) {
    V1ConfigMap c = item.object;
    if (c != null) {
      switch (item.type) {
        case "MODIFIED":
        case "DELETED":
          runSteps(
              ConfigMapHelper.createScriptConfigMapStep(
                  getOperatorNamespace(), c.getMetadata().getNamespace()));
          break;

        case "ERROR":
        default:
      }
    }
  }

  /**
   * Dispatch the Domain event to the appropriate handler.
   *
   * @param item An item received from a Watch response.
   */
  private static void dispatchDomainWatch(Watch.Response<Domain> item) {
    Domain d;
    String domainUID;
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        doCheckAndCreateDomainPresence(d);
        break;

      case "DELETED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domainUID);
        deleteDomainPresence(d);
        break;

      case "ERROR":
      default:
    }
  }

  private static String getOperatorNamespace() {
    String namespace = System.getenv("OPERATOR_NAMESPACE");
    if (namespace == null) {
      namespace = "default";
    }
    return namespace;
  }

  private static class IngressListStep extends ResponseStep<V1beta1IngressList> {
    private final String ns;

    IngressListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1beta1IngressList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1beta1IngressList> callResponse) {
      V1beta1IngressList result = callResponse.getResult();
      if (result != null) {
        for (V1beta1Ingress ingress : result.getItems()) {
          String domainUID = IngressWatcher.getIngressDomainUID(ingress);
          String clusterName = IngressWatcher.getIngressClusterName(ingress);
          if (domainUID != null && clusterName != null) {
            DomainPresenceInfoManager.getOrCreate(ns, domainUID)
                .getIngresses()
                .put(clusterName, ingress);
          }
        }
      }
      ingressWatchers.put(ns, createIngressWatcher(ns, getInitialResourceVersion(result)));
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1beta1IngressList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class DomainListStep extends ResponseStep<DomainList> {
    private final String ns;

    DomainListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainList> callResponse) {
      if (callResponse.getResult() != null) {
        for (Domain dom : callResponse.getResult().getItems()) {
          doCheckAndCreateDomainPresence(dom);
        }
      }

      domainWatchers.put(ns, createDomainWatcher(ns, getResourceVersion(callResponse.getResult())));
      return doNext(packet);
    }

    String getResourceVersion(DomainList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }

    private static DomainWatcher createDomainWatcher(
        String namespace, String initialResourceVersion) {
      return DomainWatcher.create(
          getThreadFactory(),
          namespace,
          initialResourceVersion,
          Main::dispatchDomainWatch,
          stopping);
    }
  }

  private static class ServiceListStep extends ResponseStep<V1ServiceList> {
    private final String ns;

    ServiceListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1ServiceList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ServiceList> callResponse) {
      V1ServiceList result = callResponse.getResult();
      if (result != null) {
        for (V1Service service : result.getItems()) {
          String domainUID = ServiceWatcher.getServiceDomainUID(service);
          String serverName = ServiceWatcher.getServiceServerName(service);
          String channelName = ServiceWatcher.getServiceChannelName(service);
          if (domainUID != null && serverName != null) {
            DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(ns, domainUID);
            ServerKubernetesObjects sko =
                ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
            if (channelName != null) {
              sko.getChannels().put(channelName, service);
            } else {
              sko.getService().set(service);
            }
          }
        }
      }
      serviceWatchers.put(ns, createServiceWatcher(ns, getInitialResourceVersion(result)));
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1ServiceList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class EventListStep extends ResponseStep<V1EventList> {
    private final String ns;

    EventListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1EventList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1EventList> callResponse) {
      V1EventList result = callResponse.getResult();
      if (result != null) {
        for (V1Event event : result.getItems()) {
          onEvent(event);
        }
      }
      eventWatchers.put(ns, createEventWatcher(ns, getInitialResourceVersion(result)));
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1EventList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final String ns;

    PodListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1PodList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
      V1PodList result = callResponse.getResult();
      if (result != null) {
        for (V1Pod pod : result.getItems()) {
          String domainUID = PodWatcher.getPodDomainUID(pod);
          String serverName = PodWatcher.getPodServerName(pod);
          if (domainUID != null && serverName != null) {
            DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(ns, domainUID);
            ServerKubernetesObjects sko =
                ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
            sko.getPod().set(pod);
          }
        }
      }
      podWatchers.put(ns, createPodWatcher(ns, getInitialResourceVersion(result)));
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1PodList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class NullCompletionCallback implements CompletionCallback {
    private Runnable completionAction;

    NullCompletionCallback(Runnable completionAction) {
      this.completionAction = completionAction;
    }

    @Override
    public void onCompletion(Packet packet) {
      if (completionAction != null) completionAction.run();
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
    }
  }
}
