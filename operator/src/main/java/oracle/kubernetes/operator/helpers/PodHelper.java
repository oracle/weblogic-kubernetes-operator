// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.PodTuning;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

public class PodHelper {
  private static final String INTERNAL_OPERATOR_CERT_FILE = "internalOperatorCert";
  private static final String INTERNAL_OPERATOR_CERT_ENV = "INTERNAL_OPERATOR_CERT";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private PodHelper() {}

  /**
   * Factory for {@link Step} that creates admin server pod
   *
   * @param next Next processing step
   * @return Step for creating admin server pod
   */
  public static Step createAdminPodStep(Step next) {
    return new AdminPodStep(next);
  }

  // Make this public so that it can be unit tested
  public static class AdminPodStep extends Step {
    public AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      Container c = ContainerResolver.getInstance().getContainer();
      CallBuilderFactory factory = c.getSPI(CallBuilderFactory.class);
      TuningParameters configMapHelper = c.getSPI(TuningParameters.class);

      // Compute the desired pod configuration for the admin server
      V1Pod adminPod = computeAdminPodConfig(configMapHelper, packet);

      // Verify if Kubernetes api server has a matching Pod
      // Create or replace, if necessary
      V1ObjectMeta metadata = adminPod.getMetadata();
      String podName = metadata.getName();
      String namespace = metadata.getNamespace();
      String weblogicDomainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String asName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);

      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      boolean isExplicitRestartThisServer =
          info.getExplicitRestartAdmin().get() || info.getExplicitRestartServers().contains(asName);

      ServerKubernetesObjects sko = ServerKubernetesObjectsManager.getOrCreate(info, asName);

      // First, verify existing Pod
      Step read =
          factory
              .create()
              .readPodAsync(
                  podName,
                  namespace,
                  new ResponseStep<V1Pod>(getNext()) {
                    @Override
                    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
                      if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
                        return onSuccess(packet, callResponse);
                      }
                      return super.onFailure(packet, callResponse);
                    }

                    @Override
                    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
                      V1Pod result = callResponse.getResult();
                      if (result == null) {
                        info.getExplicitRestartAdmin().set(false);
                        info.getExplicitRestartServers().remove(asName);
                        Step create =
                            factory
                                .create()
                                .createPodAsync(
                                    namespace,
                                    adminPod,
                                    new ResponseStep<V1Pod>(getNext()) {
                                      @Override
                                      public NextAction onFailure(
                                          Packet packet,
                                          ApiException e,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {
                                        return super.onFailure(
                                            AdminPodStep.this,
                                            packet,
                                            e,
                                            statusCode,
                                            responseHeaders);
                                      }

                                      @Override
                                      public NextAction onSuccess(
                                          Packet packet,
                                          V1Pod result,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {

                                        LOGGER.info(
                                            MessageKeys.ADMIN_POD_CREATED,
                                            weblogicDomainUID,
                                            asName);
                                        if (result != null) {
                                          sko.getPod().set(result);
                                        }
                                        return doNext(packet);
                                      }
                                    });
                        return doNext(create, packet);
                      } else if (!isExplicitRestartThisServer
                          && validateCurrentPod(adminPod, result)) {
                        // existing Pod has correct spec
                        LOGGER.fine(MessageKeys.ADMIN_POD_EXISTS, weblogicDomainUID, asName);
                        sko.getPod().set(result);
                        return doNext(packet);
                      } else {
                        // we need to update the Pod
                        Step replace =
                            new CyclePodStep(
                                AdminPodStep.this,
                                podName,
                                namespace,
                                adminPod,
                                MessageKeys.ADMIN_POD_REPLACED,
                                weblogicDomainUID,
                                asName,
                                info,
                                sko,
                                getNext());
                        return doNext(replace, packet);
                      }
                    }
                  });

      return doNext(read, packet);
    }

    // Make this protected so that it can be unit tested
    protected V1Pod computeAdminPodConfig(TuningParameters configMapHelper, Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String weblogicDomainUID = spec.getDomainUID();
      String weblogicDomainName = spec.getDomainName();

      // Create local admin server Pod object
      String podName = LegalNames.toPodName(weblogicDomainUID, spec.getAsName());

      String imageName = spec.getImage();
      if (imageName == null || imageName.length() == 0) {
        imageName = KubernetesConstants.DEFAULT_IMAGE;
      }
      String imagePullPolicy = spec.getImagePullPolicy();
      if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
        imagePullPolicy =
            (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX))
                ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY
                : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
      }

      V1Pod adminPod = new V1Pod();

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(podName);
      metadata.setNamespace(namespace);
      adminPod.setMetadata(metadata);

      AnnotationHelper.annotateForPrometheus(metadata, spec.getAsPort());

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1);
      labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
      labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
      labels.put(LabelConstants.SERVERNAME_LABEL, spec.getAsName());
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      V1PodSpec podSpec = new V1PodSpec();
      adminPod.setSpec(podSpec);

      podSpec.setHostname(podName);

      List<V1Container> containers = new ArrayList<>();
      V1Container container = new V1Container();
      container.setName(KubernetesConstants.CONTAINER_NAME);
      containers.add(container);
      podSpec.setContainers(containers);

      container.setImage(imageName);
      container.setImagePullPolicy(imagePullPolicy);

      V1ContainerPort containerPort = new V1ContainerPort();
      containerPort.setContainerPort(spec.getAsPort());
      containerPort.setProtocol("TCP");
      container.addPortsItem(containerPort);

      V1Lifecycle lifecycle = new V1Lifecycle();
      V1Handler preStopHandler = new V1Handler();
      V1ExecAction lifecycleExecAction = new V1ExecAction();
      lifecycleExecAction.addCommandItem("/weblogic-operator/scripts/stopServer.sh");
      lifecycleExecAction.addCommandItem(weblogicDomainUID);
      lifecycleExecAction.addCommandItem(spec.getAsName());
      lifecycleExecAction.addCommandItem(weblogicDomainName);
      preStopHandler.setExec(lifecycleExecAction);
      lifecycle.setPreStop(preStopHandler);
      container.setLifecycle(lifecycle);

      V1VolumeMount volumeMount = new V1VolumeMount();
      volumeMount.setName("weblogic-domain-storage-volume");
      volumeMount.setMountPath("/shared");
      container.addVolumeMountsItem(volumeMount);

      V1VolumeMount volumeMountSecret = new V1VolumeMount();
      volumeMountSecret.setName("weblogic-credentials-volume");
      volumeMountSecret.setMountPath("/weblogic-operator/secrets");
      volumeMountSecret.setReadOnly(true);
      container.addVolumeMountsItem(volumeMountSecret);

      V1VolumeMount volumeMountScripts = new V1VolumeMount();
      volumeMountScripts.setName("weblogic-domain-cm-volume");
      volumeMountScripts.setMountPath("/weblogic-operator/scripts");
      volumeMountScripts.setReadOnly(true);
      container.addVolumeMountsItem(volumeMountScripts);

      container.addCommandItem("/weblogic-operator/scripts/startServer.sh");
      container.addCommandItem(weblogicDomainUID);
      container.addCommandItem(spec.getAsName());
      container.addCommandItem(weblogicDomainName);

      PodTuning tuning = configMapHelper.getPodTuning();

      V1Probe readinessProbe = new V1Probe();
      V1ExecAction readinessAction = new V1ExecAction();
      readinessAction.addCommandItem("/weblogic-operator/scripts/readinessProbe.sh");
      readinessAction.addCommandItem(weblogicDomainName);
      readinessAction.addCommandItem(spec.getAsName());
      readinessProbe.exec(readinessAction);
      readinessProbe.setInitialDelaySeconds(tuning.readinessProbeInitialDelaySeconds);
      readinessProbe.setTimeoutSeconds(tuning.readinessProbeTimeoutSeconds);
      readinessProbe.setPeriodSeconds(tuning.readinessProbePeriodSeconds);
      readinessProbe.setFailureThreshold(1); // must be 1
      container.readinessProbe(readinessProbe);

      V1Probe livenessProbe = new V1Probe();
      V1ExecAction livenessAction = new V1ExecAction();
      livenessAction.addCommandItem("/weblogic-operator/scripts/livenessProbe.sh");
      livenessAction.addCommandItem(weblogicDomainName);
      livenessAction.addCommandItem(spec.getAsName());
      livenessProbe.exec(livenessAction);
      livenessProbe.setInitialDelaySeconds(tuning.livenessProbeInitialDelaySeconds);
      livenessProbe.setTimeoutSeconds(tuning.livenessProbeTimeoutSeconds);
      livenessProbe.setPeriodSeconds(tuning.livenessProbePeriodSeconds);
      livenessProbe.setFailureThreshold(1); // must be 1
      container.livenessProbe(livenessProbe);

      if (spec.getServerStartup() != null) {
        for (ServerStartup ss : spec.getServerStartup()) {
          if (ss.getServerName().equals(spec.getAsName())) {
            for (V1EnvVar ev : ss.getEnv()) {
              container.addEnvItem(ev);
            }
          }
        }
      }

      // Add internal-weblogic-operator-service certificate to Admin Server pod
      String internalOperatorCert = getInternalOperatorCertFile(configMapHelper, packet);
      addEnvVar(container, INTERNAL_OPERATOR_CERT_ENV, internalOperatorCert);

      // Override the weblogic domain and admin server related environment variables that
      // come for free with the WLS docker container with the correct values.
      overrideContainerWeblogicEnvVars(spec, spec.getAsName(), container);

      if (!info.getClaims().getItems().isEmpty()) {
        V1Volume volume = new V1Volume();
        volume.setName("weblogic-domain-storage-volume");
        V1PersistentVolumeClaimVolumeSource pvClaimSource =
            new V1PersistentVolumeClaimVolumeSource();
        pvClaimSource.setClaimName(
            info.getClaims().getItems().iterator().next().getMetadata().getName());
        volume.setPersistentVolumeClaim(pvClaimSource);
        podSpec.addVolumesItem(volume);
      }

      V1Volume volumeSecret = new V1Volume();
      volumeSecret.setName("weblogic-credentials-volume");
      V1SecretVolumeSource secret = new V1SecretVolumeSource();
      secret.setSecretName(spec.getAdminSecret().getName());
      volumeSecret.setSecret(secret);
      podSpec.addVolumesItem(volumeSecret);

      V1Volume volumeDomainConfigMap = new V1Volume();
      volumeDomainConfigMap.setName("weblogic-domain-cm-volume");
      V1ConfigMapVolumeSource cm = new V1ConfigMapVolumeSource();
      cm.setName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME);
      cm.setDefaultMode(0555); // read and execute
      volumeDomainConfigMap.setConfigMap(cm);
      podSpec.addVolumesItem(volumeDomainConfigMap);

      return adminPod;
    }

    // Make it protected to so that it can be unit tested:
    protected String getInternalOperatorCertFile(TuningParameters configMapHelper, Packet packet) {
      return configMapHelper.get(INTERNAL_OPERATOR_CERT_FILE);
    }
  }

  private static class CyclePodStep extends Step {
    private final Step conflictStep;
    private final String podName;
    private final String namespace;
    private final V1Pod newPod;
    private final String messageKey;
    private final String weblogicDomainUID;
    private final String serverName;
    private final DomainPresenceInfo info;
    private final ServerKubernetesObjects sko;

    public CyclePodStep(
        Step conflictStep,
        String podName,
        String namespace,
        V1Pod newPod,
        String messageKey,
        String weblogicDomainUID,
        String serverName,
        DomainPresenceInfo info,
        ServerKubernetesObjects sko,
        Step next) {
      super(next);
      this.conflictStep = conflictStep;
      this.podName = podName;
      this.namespace = namespace;
      this.newPod = newPod;
      this.messageKey = messageKey;
      this.weblogicDomainUID = weblogicDomainUID;
      this.serverName = serverName;
      this.info = info;
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      // Set to null so that watcher doesn't recreate pod with old spec
      sko.getPod().set(null);
      CallBuilderFactory factory =
          ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step delete =
          factory
              .create()
              .deletePodAsync(
                  podName,
                  namespace,
                  deleteOptions,
                  new ResponseStep<V1Status>(getNext()) {
                    @Override
                    public NextAction onFailure(
                        Packet packet, CallResponse<V1Status> callResponses) {
                      if (callResponses.getStatusCode() == CallBuilder.NOT_FOUND) {
                        return onSuccess(packet, callResponses);
                      }
                      return super.onFailure(conflictStep, packet, callResponses);
                    }

                    @Override
                    public NextAction onSuccess(
                        Packet packet, CallResponse<V1Status> callResponses) {
                      if (conflictStep instanceof AdminPodStep) {
                        info.getExplicitRestartAdmin().set(false);
                      }
                      info.getExplicitRestartServers().contains(serverName);
                      Step create =
                          factory
                              .create()
                              .createPodAsync(
                                  namespace,
                                  newPod,
                                  new ResponseStep<V1Pod>(getNext()) {
                                    @Override
                                    public NextAction onFailure(
                                        Packet packet,
                                        ApiException e,
                                        int statusCode,
                                        Map<String, List<String>> responseHeaders) {
                                      return super.onFailure(
                                          conflictStep, packet, e, statusCode, responseHeaders);
                                    }

                                    @Override
                                    public NextAction onSuccess(
                                        Packet packet,
                                        V1Pod result,
                                        int statusCode,
                                        Map<String, List<String>> responseHeaders) {

                                      LOGGER.info(messageKey, weblogicDomainUID, serverName);
                                      if (result != null) {
                                        sko.getPod().set(result);
                                      }

                                      PodWatcher pw = packet.getSPI(PodWatcher.class);
                                      return doNext(pw.waitForReady(result, getNext()), packet);
                                    }
                                  });
                      return doNext(create, packet);
                    }
                  });
      return doNext(delete, packet);
    }
  }

  /**
   * Factory for {@link Step} that creates managed server pod
   *
   * @param next Next processing step
   * @return Step for creating managed server pod
   */
  public static Step createManagedPodStep(Step next) {
    return new ManagedPodStep(next);
  }

  private static boolean validateCurrentPod(V1Pod build, V1Pod current) {
    // We want to detect changes that would require replacing an existing Pod
    // however, we've also found that Pod.equals(Pod) isn't right because k8s
    // returns fields, such as nodeName, even when export=true is specified.
    // Therefore, we'll just compare specific fields

    if (!VersionHelper.matchesResourceVersion(current.getMetadata(), VersionConstants.DOMAIN_V1)) {
      return false;
    }

    V1PodSpec buildSpec = build.getSpec();
    V1PodSpec currentSpec = current.getSpec();

    List<V1Container> buildContainers = buildSpec.getContainers();
    List<V1Container> currentContainers = currentSpec.getContainers();

    if (buildContainers != null) {
      if (currentContainers == null) {
        return false;
      }

      for (V1Container bc : buildContainers) {
        V1Container fcc = null;
        for (V1Container cc : currentContainers) {
          if (cc.getName().equals(bc.getName())) {
            fcc = cc;
            break;
          }
        }
        if (fcc == null) {
          return false;
        }
        if (!fcc.getImage().equals(bc.getImage())
            || !fcc.getImagePullPolicy().equals(bc.getImagePullPolicy())) {
          return false;
        }
        if (!compareUnordered(fcc.getPorts(), bc.getPorts())) {
          return false;
        }
        if (!compareUnordered(fcc.getEnv(), bc.getEnv())) {
          return false;
        }
        if (!compareUnordered(fcc.getEnvFrom(), bc.getEnvFrom())) {
          return false;
        }
      }
    }

    return true;
  }

  private static <T> boolean compareUnordered(List<T> a, List<T> b) {
    if (a == b) {
      return true;
    } else if (a == null || b == null) {
      return false;
    }
    if (a.size() != b.size()) {
      return false;
    }

    List<T> bprime = new ArrayList<>(b);
    for (T at : a) {
      if (!bprime.remove(at)) {
        return false;
      }
    }
    return true;
  }

  // Make this public so that it can be unit tested
  public static class ManagedPodStep extends Step {
    public ManagedPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      Container c = ContainerResolver.getInstance().getContainer();
      CallBuilderFactory factory = c.getSPI(CallBuilderFactory.class);
      TuningParameters configMapHelper = c.getSPI(TuningParameters.class);

      // Compute the desired pod configuration for the managed server
      V1Pod pod = computeManagedPodConfig(configMapHelper, packet);

      // Verify if Kubernetes api server has a matching Pod
      // Create or replace, if necessary
      V1ObjectMeta metadata = pod.getMetadata();
      String podName = metadata.getName();
      String namespace = metadata.getNamespace();
      String weblogicDomainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String weblogicServerName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String weblogicClusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);

      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      boolean isExplicitRestartThisServer =
          info.getExplicitRestartServers().contains(weblogicServerName)
              || (weblogicClusterName != null
                  && info.getExplicitRestartClusters().contains(weblogicClusterName));

      ServerKubernetesObjects sko =
          ServerKubernetesObjectsManager.getOrCreate(info, weblogicServerName);

      // First, verify there existing Pod
      Step read =
          factory
              .create()
              .readPodAsync(
                  podName,
                  namespace,
                  new ResponseStep<V1Pod>(getNext()) {
                    @Override
                    public NextAction onFailure(
                        Packet packet,
                        ApiException e,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (statusCode == CallBuilder.NOT_FOUND) {
                        return onSuccess(packet, null, statusCode, responseHeaders);
                      }
                      return super.onFailure(packet, e, statusCode, responseHeaders);
                    }

                    @Override
                    public NextAction onSuccess(
                        Packet packet,
                        V1Pod result,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (result == null) {
                        info.getExplicitRestartServers().remove(weblogicServerName);
                        Step create =
                            factory
                                .create()
                                .createPodAsync(
                                    namespace,
                                    pod,
                                    new ResponseStep<V1Pod>(getNext()) {
                                      @Override
                                      public NextAction onFailure(
                                          Packet packet,
                                          ApiException e,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {
                                        return super.onFailure(
                                            ManagedPodStep.this,
                                            packet,
                                            e,
                                            statusCode,
                                            responseHeaders);
                                      }

                                      @Override
                                      public NextAction onSuccess(
                                          Packet packet,
                                          V1Pod result,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {

                                        LOGGER.info(
                                            MessageKeys.MANAGED_POD_CREATED,
                                            weblogicDomainUID,
                                            weblogicServerName);
                                        if (result != null) {
                                          sko.getPod().set(result);
                                        }
                                        return doNext(packet);
                                      }
                                    });
                        return doNext(
                            DomainStatusUpdater.createProgressingStep(
                                DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON,
                                false,
                                create),
                            packet);
                      } else if (!isExplicitRestartThisServer && validateCurrentPod(pod, result)) {
                        // existing Pod has correct spec
                        LOGGER.fine(
                            MessageKeys.MANAGED_POD_EXISTS, weblogicDomainUID, weblogicServerName);
                        sko.getPod().set(result);
                        return doNext(packet);
                      } else {
                        // we need to update the Pod
                        // defer to Pod rolling step
                        Step replace =
                            new CyclePodStep(
                                ManagedPodStep.this,
                                podName,
                                namespace,
                                pod,
                                MessageKeys.MANAGED_POD_REPLACED,
                                weblogicDomainUID,
                                weblogicServerName,
                                info,
                                sko,
                                getNext());
                        synchronized (packet) {
                          @SuppressWarnings("unchecked")
                          Map<String, StepAndPacket> rolling =
                              (Map<String, StepAndPacket>)
                                  packet.get(ProcessingConstants.SERVERS_TO_ROLL);
                          if (rolling != null) {
                            rolling.put(
                                weblogicServerName,
                                new StepAndPacket(
                                    DomainStatusUpdater.createProgressingStep(
                                        DomainStatusUpdater
                                            .MANAGED_SERVERS_STARTING_PROGRESS_REASON,
                                        false,
                                        replace),
                                    packet.clone()));
                          }
                        }
                        return doEnd(packet);
                      }
                    }
                  });

      return doNext(read, packet);
    }

    // Make this protected so that it can be unit tested
    protected V1Pod computeManagedPodConfig(TuningParameters configMapHelper, Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String weblogicDomainUID = spec.getDomainUID();
      String weblogicDomainName = spec.getDomainName();

      WlsServerConfig scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
      WlsClusterConfig cluster = (WlsClusterConfig) packet.get(ProcessingConstants.CLUSTER_SCAN);
      @SuppressWarnings("unchecked")
      List<V1EnvVar> envVars = (List<V1EnvVar>) packet.get(ProcessingConstants.ENVVARS);

      String weblogicServerName = scan.getName();

      // Create local managed server Pod object
      String podName = LegalNames.toPodName(weblogicDomainUID, weblogicServerName);

      String weblogicClusterName = null;
      if (cluster != null) weblogicClusterName = cluster.getClusterName();

      String imageName = spec.getImage();
      if (imageName == null || imageName.length() == 0) {
        imageName = KubernetesConstants.DEFAULT_IMAGE;
      }
      String imagePullPolicy = spec.getImagePullPolicy();
      if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
        imagePullPolicy =
            (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX))
                ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY
                : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
      }

      V1Pod pod = new V1Pod();

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(podName);
      metadata.setNamespace(namespace);
      pod.setMetadata(metadata);

      AnnotationHelper.annotateForPrometheus(metadata, scan.getListenPort());

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1);
      labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
      labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
      labels.put(LabelConstants.SERVERNAME_LABEL, weblogicServerName);
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      if (weblogicClusterName != null) {
        labels.put(LabelConstants.CLUSTERNAME_LABEL, weblogicClusterName);
      }
      metadata.setLabels(labels);

      V1PodSpec podSpec = new V1PodSpec();
      pod.setSpec(podSpec);

      List<V1Container> containers = new ArrayList<>();
      V1Container container = new V1Container();
      container.setName(KubernetesConstants.CONTAINER_NAME);
      containers.add(container);
      podSpec.setContainers(containers);

      container.setImage(imageName);
      container.setImagePullPolicy(imagePullPolicy);

      V1ContainerPort containerPort = new V1ContainerPort();
      containerPort.setContainerPort(scan.getListenPort());
      containerPort.setProtocol("TCP");
      container.addPortsItem(containerPort);

      V1Lifecycle lifecycle = new V1Lifecycle();
      V1Handler preStop = new V1Handler();
      V1ExecAction exec = new V1ExecAction();
      exec.addCommandItem("/weblogic-operator/scripts/stopServer.sh");
      exec.addCommandItem(weblogicDomainUID);
      exec.addCommandItem(weblogicServerName);
      exec.addCommandItem(weblogicDomainName);
      preStop.setExec(exec);
      lifecycle.setPreStop(preStop);
      container.setLifecycle(lifecycle);

      V1VolumeMount volumeMount = new V1VolumeMount();
      volumeMount.setName("weblogic-domain-storage-volume");
      volumeMount.setMountPath("/shared");
      container.addVolumeMountsItem(volumeMount);

      V1VolumeMount volumeMountSecret = new V1VolumeMount();
      volumeMountSecret.setName("weblogic-credentials-volume");
      volumeMountSecret.setMountPath("/weblogic-operator/secrets");
      volumeMountSecret.setReadOnly(true);
      container.addVolumeMountsItem(volumeMountSecret);

      V1VolumeMount volumeMountScripts = new V1VolumeMount();
      volumeMountScripts.setName("weblogic-domain-cm-volume");
      volumeMountScripts.setMountPath("/weblogic-operator/scripts");
      volumeMountScripts.setReadOnly(true);
      container.addVolumeMountsItem(volumeMountScripts);

      container.addCommandItem("/weblogic-operator/scripts/startServer.sh");
      container.addCommandItem(weblogicDomainUID);
      container.addCommandItem(weblogicServerName);
      container.addCommandItem(weblogicDomainName);
      container.addCommandItem(spec.getAsName());
      container.addCommandItem(String.valueOf(spec.getAsPort()));

      PodTuning tuning = configMapHelper.getPodTuning();

      V1Probe readinessProbe = new V1Probe();
      V1ExecAction readinessAction = new V1ExecAction();
      readinessAction.addCommandItem("/weblogic-operator/scripts/readinessProbe.sh");
      readinessAction.addCommandItem(weblogicDomainName);
      readinessAction.addCommandItem(weblogicServerName);
      readinessProbe.exec(readinessAction);
      readinessProbe.setInitialDelaySeconds(tuning.readinessProbeInitialDelaySeconds);
      readinessProbe.setTimeoutSeconds(tuning.readinessProbeTimeoutSeconds);
      readinessProbe.setPeriodSeconds(tuning.readinessProbePeriodSeconds);
      readinessProbe.setFailureThreshold(1); // must be 1
      container.readinessProbe(readinessProbe);

      V1Probe livenessProbe = new V1Probe();
      V1ExecAction livenessAction = new V1ExecAction();
      livenessAction.addCommandItem("/weblogic-operator/scripts/livenessProbe.sh");
      livenessAction.addCommandItem(weblogicDomainName);
      livenessAction.addCommandItem(weblogicServerName);
      livenessProbe.exec(livenessAction);
      livenessProbe.setInitialDelaySeconds(tuning.livenessProbeInitialDelaySeconds);
      livenessProbe.setTimeoutSeconds(tuning.livenessProbeTimeoutSeconds);
      livenessProbe.setPeriodSeconds(tuning.livenessProbePeriodSeconds);
      livenessProbe.setFailureThreshold(1); // must be 1
      container.livenessProbe(livenessProbe);

      if (!info.getClaims().getItems().isEmpty()) {
        V1Volume volume = new V1Volume();
        volume.setName("weblogic-domain-storage-volume");
        V1PersistentVolumeClaimVolumeSource pvClaimSource =
            new V1PersistentVolumeClaimVolumeSource();
        pvClaimSource.setClaimName(
            info.getClaims().getItems().iterator().next().getMetadata().getName());
        volume.setPersistentVolumeClaim(pvClaimSource);
        podSpec.addVolumesItem(volume);
      }

      V1Volume volumeSecret = new V1Volume();
      volumeSecret.setName("weblogic-credentials-volume");
      V1SecretVolumeSource secret = new V1SecretVolumeSource();
      secret.setSecretName(spec.getAdminSecret().getName());
      volumeSecret.setSecret(secret);
      podSpec.addVolumesItem(volumeSecret);

      V1Volume volumeDomainConfigMap = new V1Volume();
      volumeDomainConfigMap.setName("weblogic-domain-cm-volume");
      V1ConfigMapVolumeSource cm = new V1ConfigMapVolumeSource();
      cm.setName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME);
      cm.setDefaultMode(0555); // read and execute
      volumeDomainConfigMap.setConfigMap(cm);
      podSpec.addVolumesItem(volumeDomainConfigMap);

      if (envVars != null) {
        for (V1EnvVar ev : envVars) {
          container.addEnvItem(ev);
        }
      }

      // Override the weblogic domain and admin server related environment variables that
      // come for free with the WLS docker container with the correct values.
      overrideContainerWeblogicEnvVars(spec, weblogicServerName, container);

      return pod;
    }
  }

  // Override the weblogic domain and admin server related environment variables that
  // come for free with the WLS docker container with the correct values.
  private static void overrideContainerWeblogicEnvVars(
      DomainSpec spec, String serverName, V1Container container) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(container, "DOMAIN_NAME", spec.getDomainName());
    addEnvVar(container, "DOMAIN_HOME", "/shared/domain/" + spec.getDomainName());
    addEnvVar(container, "ADMIN_NAME", spec.getAsName());
    addEnvVar(container, "ADMIN_PORT", spec.getAsPort().toString());
    addEnvVar(container, "SERVER_NAME", serverName);
    // Hide the admin account's user name and password.
    // Note: need to use null v.s. "" since if you upload a "" to kubectl then download it,
    // it comes back as a null and V1EnvVar.equals returns false even though it's supposed to
    // be the same value.
    // Regardless, the pod ends up with an empty string as the value (v.s. thinking that
    // the environment variable hasn't been set), so it honors the value (instead of using
    // the default, e.g. 'weblogic' for the user name).
    addEnvVar(container, "ADMIN_USERNAME", null);
    addEnvVar(container, "ADMIN_PASSWORD", null);
  }

  // Add an environment variable to a container
  private static void addEnvVar(V1Container container, String name, String value) {
    V1EnvVar envVar = new V1EnvVar();
    envVar.setName(name);
    envVar.setValue(value);
    container.addEnvItem(envVar);
  }

  /**
   * Factory for {@link Step} that deletes server pod
   *
   * @param sko Server Kubernetes Objects
   * @param next Next processing step
   * @return Step for deleting server pod
   */
  public static Step deletePodStep(ServerKubernetesObjects sko, Step next) {
    return new DeletePodStep(sko, next);
  }

  private static class DeletePodStep extends Step {
    private final ServerKubernetesObjects sko;

    public DeletePodStep(ServerKubernetesObjects sko, Step next) {
      super(next);
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      String namespace = meta.getNamespace();

      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      // Set pod to null so that watcher doesn't try to recreate pod
      V1Pod oldPod = sko.getPod().getAndSet(null);
      if (oldPod != null) {
        CallBuilderFactory factory =
            ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
        return doNext(
            factory
                .create()
                .deletePodAsync(
                    oldPod.getMetadata().getName(),
                    namespace,
                    deleteOptions,
                    new ResponseStep<V1Status>(getNext()) {
                      @Override
                      public NextAction onFailure(
                          Packet packet,
                          ApiException e,
                          int statusCode,
                          Map<String, List<String>> responseHeaders) {
                        if (statusCode == CallBuilder.NOT_FOUND) {
                          return onSuccess(packet, null, statusCode, responseHeaders);
                        }
                        return super.onFailure(packet, e, statusCode, responseHeaders);
                      }

                      @Override
                      public NextAction onSuccess(
                          Packet packet,
                          V1Status result,
                          int statusCode,
                          Map<String, List<String>> responseHeaders) {
                        return doNext(getNext(), packet);
                      }
                    }),
            packet);
      }
      return doNext(packet);
    }
  }
}
