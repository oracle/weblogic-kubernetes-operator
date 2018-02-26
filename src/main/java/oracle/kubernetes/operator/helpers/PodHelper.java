// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1HTTPGetAction;
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
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PodHelper {
  private static final String INTERNAL_OPERATOR_CERT_FILE = "internalOperatorCert";
  private static final String INTERNAL_OPERATOR_CERT_ENV = "INTERNAL_OPERATOR_CERT";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private PodHelper() {}
  
  /**
   * Factory for {@link Step} that creates admin server pod
   * @param next Next processing step
   * @return Step for creating admin server pod
   */
  public static Step createAdminPodStep(Step next) {
    return new AdminPodStep(next);
  }

  private static class AdminPodStep extends Step {
    public AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String weblogicDomainUID = spec.getDomainUID();
      String weblogicDomainName = spec.getDomainName();
      
      // Create local admin server Pod object
      String podName = CallBuilder.toDNS1123LegalName(weblogicDomainUID + "-" + spec.getAsName());

      Boolean explicitRestartAdmin = (Boolean) packet.get(ProcessingConstants.EXPLICIT_RESTART_ADMIN);
      @SuppressWarnings("unchecked")
      List<String> explicitRestartServers = (List<String>) packet.get(ProcessingConstants.EXPLICIT_RESTART_SERVERS);
      
      boolean isExplicitRestartThisServer = 
          (Boolean.TRUE.equals(explicitRestartAdmin)) ||
          (explicitRestartServers != null && explicitRestartServers.contains(spec.getAsName()));
      
      String imageName = spec.getImage();
      if (imageName == null || imageName.length() == 0) {
        imageName = KubernetesConstants.DEFAULT_IMAGE;
      }
      String imagePullPolicy = spec.getImagePullPolicy();
      if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
        imagePullPolicy = (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX)) ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
      }

      V1Pod adminPod = new V1Pod();

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(podName);
      metadata.setNamespace(namespace);
      adminPod.setMetadata(metadata);
      
      AnnotationHelper.annotateWithDomain(metadata, dom);
      AnnotationHelper.annotateForPrometheus(metadata, spec.getAsPort());

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
      labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
      labels.put(LabelConstants.SERVERNAME_LABEL, spec.getAsName());
      metadata.setLabels(labels);

      V1PodSpec podSpec = new V1PodSpec();
      adminPod.setSpec(podSpec);

      podSpec.setHostname(podName);

      List<V1Container> containers = new ArrayList<>();
      V1Container container = new V1Container();
      container.setName("weblogic-server");
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
      lifecycleExecAction.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + spec.getAsName() + "/nodemgr_home/stopServer.sh");
      preStopHandler.setExec(lifecycleExecAction);
      lifecycle.setPreStop(preStopHandler);
      container.setLifecycle(lifecycle);

      V1VolumeMount volumeMount = new V1VolumeMount();
      volumeMount.setName("pv-storage");
      volumeMount.setMountPath("/shared");
      container.addVolumeMountsItem(volumeMount);

      V1VolumeMount volumeMountSecret = new V1VolumeMount();
      volumeMountSecret.setName("secrets");
      volumeMountSecret.setMountPath("/weblogic-operator/secrets");
      container.addVolumeMountsItem(volumeMountSecret);

      container.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + spec.getAsName() + "/nodemgr_home/startServer.sh");

      V1Probe readinessProbe = new V1Probe();
      V1HTTPGetAction httpGet = new V1HTTPGetAction();
      httpGet.setPath("/weblogic/ready");
      httpGet.setPort(new IntOrString(spec.getAsPort()));
      readinessProbe.setHttpGet(httpGet);
      readinessProbe.setInitialDelaySeconds(15);
      readinessProbe.setTimeoutSeconds(5);
      readinessProbe.setPeriodSeconds(15);
      container.readinessProbe(readinessProbe);

      V1Probe livenessProbe = new V1Probe();
      V1ExecAction livenessExecAction = new V1ExecAction();
      livenessExecAction.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + spec.getAsName() + "/nodemgr_home/livenessProbe.sh");
      livenessProbe.exec(livenessExecAction);
      livenessProbe.setInitialDelaySeconds(10);
      livenessProbe.setPeriodSeconds(1);
      livenessProbe.setFailureThreshold(1);
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
      ConfigMapHelper configMapHelper = new ConfigMapHelper("/operator/config");
      String internalOperatorCert = configMapHelper.get(INTERNAL_OPERATOR_CERT_FILE);
      addEnvVar(container, INTERNAL_OPERATOR_CERT_ENV, internalOperatorCert);

      // Override the weblogic domain and admin server related environment variables that
      // come for free with the WLS docker container with the correct values.
      overrideContainerWeblogicEnvVars(spec, container);

      if (!info.getClaims().getItems().isEmpty()) {
        V1Volume volume = new V1Volume();
        volume.setName("pv-storage");
        V1PersistentVolumeClaimVolumeSource pvClaimSource = new V1PersistentVolumeClaimVolumeSource();
        pvClaimSource.setClaimName(info.getClaims().getItems().iterator().next().getMetadata().getName());
        volume.setPersistentVolumeClaim(pvClaimSource);
        podSpec.addVolumesItem(volume);
      }
      
      V1Volume volumeSecret = new V1Volume();
      volumeSecret.setName("secrets");
      V1SecretVolumeSource secret = new V1SecretVolumeSource();
      secret.setSecretName(spec.getAdminSecret().getName());
      volumeSecret.setSecret(secret);
      podSpec.addVolumesItem(volumeSecret);
      
      // Verify if Kubernetes api server has a matching Pod
      // Create or replace, if necessary
      ServerKubernetesObjects created = new ServerKubernetesObjects();
      ServerKubernetesObjects current = info.getServers().putIfAbsent(spec.getAsName(), created);
      ServerKubernetesObjects sko = current != null ? current : created;

      // First, verify existing Pod
      Step read = CallBuilder.create().readPodAsync(podName, namespace, new ResponseStep<V1Pod>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1Pod result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (result == null) {
            Step create = CallBuilder.create().createPodAsync(namespace, adminPod, new ResponseStep<V1Pod>(next) {
              @Override
              public NextAction onSuccess(Packet packet, V1Pod result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.ADMIN_POD_CREATED, weblogicDomainUID, spec.getAsName());
                if (result != null) {
                  sko.getPod().set(result);
                }
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (!isExplicitRestartThisServer && (AnnotationHelper.checkDomainAnnotation(result.getMetadata(), dom) || validateCurrentPod(adminPod, result))) {
            // existing Pod has correct spec
            LOGGER.info(MessageKeys.ADMIN_POD_EXISTS, weblogicDomainUID, spec.getAsName());
            sko.getPod().set(result);
            return doNext(packet);
          } else {
            // we need to update the Pod
            Step replace = new CyclePodStep(
                podName, namespace, adminPod, MessageKeys.ADMIN_POD_REPLACED, 
                weblogicDomainUID, spec.getAsName(), sko, next);
            return doNext(replace, packet);
          }
        }
      });
      
      return doNext(read, packet);
    }
  }

  private static class CyclePodStep extends Step  {
    private final String podName;
    private final String namespace;
    private final V1Pod newPod;
    private final String messageKey;
    private final String weblogicDomainUID;
    private final String serverName;
    private final ServerKubernetesObjects sko;
    
    public CyclePodStep(String podName, String namespace, V1Pod newPod, String messageKey, String weblogicDomainUID, String serverName, ServerKubernetesObjects sko, Step next) {
      super(next);
      this.podName = podName;
      this.namespace = namespace;
      this.newPod = newPod;
      this.messageKey = messageKey;
      this.weblogicDomainUID = weblogicDomainUID;
      this.serverName = serverName;
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      // Set to null so that watcher doesn't recreate pod with old spec
      sko.getPod().set(null);
      Step delete = CallBuilder.create().deletePodAsync(podName, namespace, deleteOptions, new ResponseStep<V1Status>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1Status result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          Step create = CallBuilder.create().createPodAsync(namespace, newPod, new ResponseStep<V1Pod>(next) {
            @Override
            public NextAction onSuccess(Packet packet, V1Pod result, int statusCode,
                Map<String, List<String>> responseHeaders) {
              
              LOGGER.info(messageKey, weblogicDomainUID, serverName);
              if (result != null) {
                sko.getPod().set(result);
              }
              
              PodWatcher pw = packet.getSPI(PodWatcher.class);
              return doNext(pw.waitForReady(result, next), packet);
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
        if (!fcc.getImage().equals(bc.getImage()) || !fcc.getImagePullPolicy().equals(bc.getImagePullPolicy())) {
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

  private static class ManagedPodStep extends Step {
    public ManagedPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
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
      String podName = CallBuilder.toDNS1123LegalName(weblogicDomainUID + "-" + weblogicServerName);

      String weblogicClusterName = null;
      if (cluster != null)
        weblogicClusterName = cluster.getClusterName();
      
      @SuppressWarnings("unchecked")
      List<String> explicitRestartServers = (List<String>) packet.get(ProcessingConstants.EXPLICIT_RESTART_SERVERS);
      @SuppressWarnings("unchecked")
      List<String> explicitRestartClusters = (List<String>) packet.get(ProcessingConstants.EXPLICIT_RESTART_CLUSTERS);
      
      boolean isExplicitRestartThisServer = 
          (explicitRestartServers != null && explicitRestartServers.contains(weblogicServerName)) ||
          (explicitRestartClusters != null && weblogicClusterName != null && explicitRestartClusters.contains(weblogicClusterName));
      
      String imageName = spec.getImage();
      if (imageName == null || imageName.length() == 0) {
        imageName = KubernetesConstants.DEFAULT_IMAGE;
      }
      String imagePullPolicy = spec.getImagePullPolicy();
      if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
        imagePullPolicy = (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX)) ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
      }

      V1Pod pod = new V1Pod();

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(podName);
      metadata.setNamespace(namespace);
      pod.setMetadata(metadata);

      AnnotationHelper.annotateWithDomain(metadata, dom);
      AnnotationHelper.annotateForPrometheus(metadata, scan.getListenPort());

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
      labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
      labels.put(LabelConstants.SERVERNAME_LABEL, weblogicServerName);
      if (weblogicClusterName != null)
        labels.put(LabelConstants.CLUSTERNAME_LABEL, weblogicClusterName);
      metadata.setLabels(labels);

      V1PodSpec podSpec = new V1PodSpec();
      pod.setSpec(podSpec);

      List<V1Container> containers = new ArrayList<>();
      V1Container container = new V1Container();
      container.setName("weblogic-server");
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
      exec.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + weblogicServerName + "/nodemgr_home/stopServer.sh");
      preStop.setExec(exec);
      lifecycle.setPreStop(preStop);
      container.setLifecycle(lifecycle);

      V1VolumeMount volumeMount = new V1VolumeMount();
      volumeMount.setName("pv-storage");
      volumeMount.setMountPath("/shared");
      container.addVolumeMountsItem(volumeMount);

      V1VolumeMount volumeMountSecret = new V1VolumeMount();
      volumeMountSecret.setName("secrets");
      volumeMountSecret.setMountPath("/weblogic-operator/secrets");
      container.addVolumeMountsItem(volumeMountSecret);

      container.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + weblogicServerName + "/nodemgr_home/startServer.sh");

      V1Probe readinessProbe = new V1Probe();
      V1HTTPGetAction httpGet = new V1HTTPGetAction();
      httpGet.setPath("/weblogic/ready");
      httpGet.setPort(new IntOrString(scan.getListenPort()));
      readinessProbe.setHttpGet(httpGet);
      readinessProbe.setInitialDelaySeconds(15);
      readinessProbe.setTimeoutSeconds(5);
      readinessProbe.setPeriodSeconds(15);
      container.readinessProbe(readinessProbe);

      V1Probe livenessProbe = new V1Probe();
      V1ExecAction execAction = new V1ExecAction();
      execAction.addCommandItem("/shared/domain/" + weblogicDomainName + "/servers/" + weblogicServerName + "/nodemgr_home/livenessProbe.sh");
      livenessProbe.exec(execAction);
      livenessProbe.setInitialDelaySeconds(10);
      livenessProbe.setPeriodSeconds(1);
      livenessProbe.setFailureThreshold(1);
      container.livenessProbe(livenessProbe);

      if (!info.getClaims().getItems().isEmpty()) {
        V1Volume volume = new V1Volume();
        volume.setName("pv-storage");
        V1PersistentVolumeClaimVolumeSource pvClaimSource = new V1PersistentVolumeClaimVolumeSource();
        pvClaimSource.setClaimName(info.getClaims().getItems().iterator().next().getMetadata().getName());
        volume.setPersistentVolumeClaim(pvClaimSource);
        podSpec.addVolumesItem(volume);
      }

      V1Volume volumeSecret = new V1Volume();
      volumeSecret.setName("secrets");
      V1SecretVolumeSource secret = new V1SecretVolumeSource();
      secret.setSecretName(spec.getAdminSecret().getName());
      volumeSecret.setSecret(secret);
      podSpec.addVolumesItem(volumeSecret);

      if (envVars != null) {
        for (V1EnvVar ev : envVars) {
          container.addEnvItem(ev);
        }
      }

      // Override the weblogic domain and admin server related environment variables that
      // come for free with the WLS docker container with the correct values.
      overrideContainerWeblogicEnvVars(spec, container);

      // Verify if Kubernetes api server has a matching Pod
      // Create or replace, if necessary
      ServerKubernetesObjects created = new ServerKubernetesObjects();
      ServerKubernetesObjects current = info.getServers().putIfAbsent(weblogicServerName, created);
      ServerKubernetesObjects sko = current != null ? current : created;

      // First, verify there existing Pod
      Step read = CallBuilder.create().readPodAsync(podName, namespace, new ResponseStep<V1Pod>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1Pod result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (result == null) {
            Step create = CallBuilder.create().createPodAsync(namespace, pod, new ResponseStep<V1Pod>(next) {
              @Override
              public NextAction onSuccess(Packet packet, V1Pod result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.MANAGED_POD_CREATED, weblogicDomainUID, weblogicServerName);
                if (result != null) {
                  sko.getPod().set(result);
                }
                return doNext(packet);
              }
            });
            return doNext(DomainStatusUpdater.createProgressingStep(DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON, false, create), packet);
          } else if (!isExplicitRestartThisServer && (AnnotationHelper.checkDomainAnnotation(result.getMetadata(), dom) || validateCurrentPod(pod, result))) {
            // existing Pod has correct spec
            LOGGER.info(MessageKeys.MANAGED_POD_EXISTS, weblogicDomainUID, weblogicServerName);
            sko.getPod().set(result);
            return doNext(packet);
          } else {
            // we need to update the Pod
            // defer to Pod rolling step
            Step replace = new CyclePodStep(
                podName, namespace, pod, MessageKeys.MANAGED_POD_REPLACED, 
                weblogicDomainUID, weblogicServerName, sko, next);
            synchronized (packet) {
              @SuppressWarnings("unchecked")
              Map<String, StepAndPacket> rolling = (Map<String, StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL);
              if (rolling != null) {
                rolling.put(weblogicServerName, new StepAndPacket(
                    DomainStatusUpdater.createProgressingStep(DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON, false, replace), packet.clone()));
              }
            }
            return doEnd(packet);
          }
        }
      });
      
      return doNext(read, packet);
    }
  }

  // Override the weblogic domain and admin server related environment variables that
  // come for free with the WLS docker container with the correct values.
  private static void overrideContainerWeblogicEnvVars(DomainSpec spec, V1Container container) {
    // Override the domain name, domain directory, admin server name and admin server port.
    addEnvVar(container, "DOMAIN_NAME", spec.getDomainName());
    addEnvVar(container, "DOMAIN_HOME", "/shared/domain/" + spec.getDomainName());
    addEnvVar(container, "ADMIN_NAME", spec.getAsName());
    addEnvVar(container, "ADMIN_PORT", spec.getAsPort().toString());
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
        return doNext(CallBuilder.create().deletePodAsync(oldPod.getMetadata().getName(), namespace, deleteOptions, new ResponseStep<V1Status>(next) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }
  
          @Override
          public NextAction onSuccess(Packet packet, V1Status result, int statusCode,
              Map<String, List<String>> responseHeaders) {
            return doNext(next, packet);
          }
        }), packet);
      }
      return doNext(packet);
    }
  }
}
