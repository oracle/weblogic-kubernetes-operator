// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.JSON;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CRDHelper;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfoManager;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper.KubernetesVersion;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjectsManager;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.steps.ConfigMapAfterStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainList;

/** A Kubernetes Operator for WebLogic. */
public class Main {

  private static ThreadFactory getThreadFactory() {
    return ThreadFactorySingleton.getInstance();
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final TuningParameters tuningAndConfig;

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

  static final Engine engine = new Engine(wrappedExecutorService);

  static final Map<String, AtomicBoolean> isNamespaceStopping = new ConcurrentHashMap<>();

  private static final Map<String, ConfigMapWatcher> configMapWatchers = new ConcurrentHashMap<>();
  private static final Map<String, DomainWatcher> domainWatchers = new ConcurrentHashMap<>();
  private static final Map<String, EventWatcher> eventWatchers = new ConcurrentHashMap<>();
  private static final Map<String, ServiceWatcher> serviceWatchers = new ConcurrentHashMap<>();
  private static final Map<String, IngressWatcher> ingressWatchers = new ConcurrentHashMap<>();

  static final Map<String, PodWatcher> podWatchers = new ConcurrentHashMap<>();

  private static final String operatorNamespace = getOperatorNamespace();

  private static String principal;
  private static RestServer restServer = null;
  private static Thread livenessThread = null;
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

    try {
      engine.getExecutor().execute(Main::begin);

      // now we just wait until the pod is terminated
      waitForDeath();

      // stop the REST server
      stopRestServer();
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  private static void begin() {
    String serviceAccountName = tuningAndConfig.get("serviceaccount");
    if (serviceAccountName == null) {
      serviceAccountName = "default";
    }
    principal = "system:serviceaccount:" + operatorNamespace + ":" + serviceAccountName;

    LOGGER.info(MessageKeys.OP_CONFIG_NAMESPACE, operatorNamespace);

    Collection<String> targetNamespaces = getTargetNamespaces();
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

      version = HealthCheckHelper.performK8sVersionCheck();

      runSteps(
          CRDHelper.createDomainCRDStep(new StartNamespacesStep(targetNamespaces)),
          Main::completeBegin);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private static void completeBegin() {
    DomainProcessor.deleteStrandedResources();

    try {
      // start the REST server
      startRestServer(principal, isNamespaceStopping.keySet());

      // start periodic retry and recheck
      int recheckInterval = tuningAndConfig.getMainTuning().domainPresenceRecheckIntervalSeconds;
      engine
          .getExecutor()
          .scheduleWithFixedDelay(
              recheckDomains(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  static KubernetesVersion getVersion() {
    return version;
  }

  static String getPrincipal() {
    return principal;
  }

  private static class StartNamespacesStep extends Step {
    private final Collection<String> targetNamespaces;

    public StartNamespacesStep(Collection<String> targetNamespaces) {
      this.targetNamespaces = targetNamespaces;
    }

    @Override
    public NextAction apply(Packet packet) {
      // check for any existing resources and add the watches on them
      // this would happen when the Domain was running BEFORE the Operator starts up
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      for (String ns : targetNamespaces) {
        startDetails.add(
            new StepAndPacket(
                Step.chain(
                    new StartNamespaceBeforeStep(ns), readExistingResources(operatorNamespace, ns)),
                packet.clone()));
      }
      return doForkJoin(getNext(), packet, startDetails);
    }
  }

  private static class StartNamespaceBeforeStep extends Step {
    private final String ns;

    StartNamespaceBeforeStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (isNamespaceStopping.putIfAbsent(ns, new AtomicBoolean(false)) == null) {
        try {
          HealthCheckHelper.performSecurityChecks(version, operatorNamespace, ns);
        } catch (Throwable e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }

        return doNext(packet);
      }
      return doEnd(packet);
    }
  }

  private static void stopNamespaces(Collection<String> namespacesToStop) {
    for (String ns : namespacesToStop) {
      AtomicBoolean stopping = isNamespaceStopping.remove(ns);
      if (stopping != null) {
        stopping.set(true);
      }
    }
  }

  private static final AtomicBoolean UNINITIALIZED_NS_STOPPING = new AtomicBoolean(true);

  static AtomicBoolean isNamespaceStopping(String ns) {
    return isNamespaceStopping.getOrDefault(ns, UNINITIALIZED_NS_STOPPING);
  }

  static void runSteps(Step firstStep) {
    runSteps(firstStep, null);
  }

  static void runSteps(Step firstStep, Runnable completionAction) {
    engine.createFiber().start(firstStep, new Packet(), andThenDo(completionAction));
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  private static Runnable recheckDomains() {
    return () -> {
      Collection<String> targetNamespaces = getTargetNamespaces();

      // Check for removed namespaces
      Set<String> namespacesToStop = new TreeSet<>(isNamespaceStopping.keySet());
      namespacesToStop.removeAll(targetNamespaces);
      stopNamespaces(namespacesToStop);

      runSteps(new StartNamespacesStep(targetNamespaces), DomainProcessor::deleteStrandedResources);
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
    LOGGER.info(MessageKeys.LISTING_DOMAINS);
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
    return new ConfigMapAfterStep(
        ns, configMapWatchers, isNamespaceStopping(ns), DomainProcessor::dispatchConfigMapWatch);
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

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

    isNamespaceStopping.forEach(
        (key, value) -> {
          value.set(true);
        });
  }

  private static EventWatcher createEventWatcher(String ns, String initialResourceVersion) {
    return EventWatcher.create(
        getThreadFactory(),
        ns,
        READINESS_PROBE_FAILURE_EVENT_FILTER,
        initialResourceVersion,
        DomainProcessor::dispatchEventWatch,
        isNamespaceStopping(ns));
  }

  private static PodWatcher createPodWatcher(String ns, String initialResourceVersion) {
    return PodWatcher.create(
        getThreadFactory(),
        ns,
        initialResourceVersion,
        DomainProcessor::dispatchPodWatch,
        isNamespaceStopping(ns));
  }

  private static ServiceWatcher createServiceWatcher(String ns, String initialResourceVersion) {
    return ServiceWatcher.create(
        getThreadFactory(),
        ns,
        initialResourceVersion,
        DomainProcessor::dispatchServiceWatch,
        isNamespaceStopping(ns));
  }

  private static IngressWatcher createIngressWatcher(String ns, String initialResourceVersion) {
    return IngressWatcher.create(
        getThreadFactory(),
        ns,
        initialResourceVersion,
        DomainProcessor::dispatchIngressWatch,
        isNamespaceStopping(ns));
  }

  static String getOperatorNamespace() {
    String namespace = System.getenv("OPERATOR_NAMESPACE");
    if (namespace == null) {
      namespace = "default";
    }
    return namespace;
  }

  private static Collection<String> getTargetNamespaces() {
    String namespace = getOperatorNamespace();

    return getTargetNamespaces(tuningAndConfig.get("targetNamespaces"), namespace);
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
      if (!ingressWatchers.containsKey(ns)) {
        ingressWatchers.put(ns, createIngressWatcher(ns, getInitialResourceVersion(result)));
      }
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
      Set<String> domainUIDs = new HashSet<>();
      if (callResponse.getResult() != null) {
        for (Domain dom : callResponse.getResult().getItems()) {
          String domainUID = dom.getSpec().getDomainUID();
          domainUIDs.add(domainUID);
          DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(dom);
          if (isNamespaceStopping(dom.getMetadata().getNamespace()).get()) {
            // Update domain here if namespace is not yet running
            info.setDomain(dom);
          }
          DomainProcessor.makeRightDomainPresence(info, dom, true, false, false);
        }
      }

      getDomainPresenceInfos()
          .forEach(
              (key, value) -> {
                Domain d = value.getDomain();
                if (d != null && ns.equals(d.getMetadata().getNamespace())) {
                  if (!domainUIDs.contains(d.getSpec().getDomainUID())) {
                    // This is a stranded DomainPresenceInfo. Clear the Domain reference
                    // so that stranded resources are marked for clean-up.
                    value.setDomain(null);
                  }
                }
              });

      if (!domainWatchers.containsKey(ns)) {
        domainWatchers.put(
            ns, createDomainWatcher(ns, getResourceVersion(callResponse.getResult())));
      }
      return doNext(packet);
    }

    String getResourceVersion(DomainList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }

    private static DomainWatcher createDomainWatcher(String ns, String initialResourceVersion) {
      return DomainWatcher.create(
          getThreadFactory(),
          ns,
          initialResourceVersion,
          DomainProcessor::dispatchDomainWatch,
          isNamespaceStopping(ns));
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
      if (!serviceWatchers.containsKey(ns)) {
        serviceWatchers.put(ns, createServiceWatcher(ns, getInitialResourceVersion(result)));
      }
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
      // don't bother processing pre-existing events

      if (!eventWatchers.containsKey(ns)) {
        eventWatchers.put(ns, createEventWatcher(ns, getInitialResourceVersion(result)));
      }
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
      if (!podWatchers.containsKey(ns)) {
        podWatchers.put(ns, createPodWatcher(ns, getInitialResourceVersion(result)));
      }
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
