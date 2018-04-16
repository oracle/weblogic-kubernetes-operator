// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

public class DomainHomeHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private DomainHomeHelper() {}
  
  public static Step createDomainHomeStep(Step next) {
    return new DomainHomeStep(next);
  }
  
  public static class DomainHomeStep extends Step {
    public DomainHomeStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      Container c = ContainerResolver.getInstance().getContainer();
      CallBuilderFactory factory = c.getSPI(CallBuilderFactory.class);

      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      String namespace = meta.getNamespace();

      V1Job job = computeDomainHomeJob(info);
      Step conflictStep = factory.create().deleteJobAsync(job.getMetadata().getName(), namespace, new ResponseStep<V1Status>(next) {
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
          return doNext(DomainHomeStep.this, packet);
        }
      });
      
      Step create = factory.create().createJobAsync(namespace, computeDomainHomeJob(info), new ResponseStep<V1Job>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          return super.onFailure(conflictStep, packet, e, statusCode, responseHeaders);
        }
        
        @Override
        public NextAction onSuccess(Packet packet, V1Job result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          // TODO: wait for the job to finish or fail
          //       update domain status condition
          return null;
        }
      });
      
      return doNext(create, packet);
    }
    
    protected V1Job computeDomainHomeJob(DomainPresenceInfo info) {
      Domain dom = info.getDomain();
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String weblogicDomainUID = spec.getDomainUID();
      String weblogicDomainName = spec.getDomainName();

      String jobName = weblogicDomainUID + "-create-weblogic-domain-job";
      
      String imageName = spec.getImage();
      if (imageName == null || imageName.length() == 0) {
        imageName = KubernetesConstants.DEFAULT_IMAGE;
      }
      String imagePullPolicy = spec.getImagePullPolicy();
      if (imagePullPolicy == null || imagePullPolicy.length() == 0) {
        imagePullPolicy = (imageName.endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX)) ? KubernetesConstants.ALWAYS_IMAGEPULLPOLICY : KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
      }

      V1Job job = new V1Job();
      
      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(jobName);
      metadata.setNamespace(namespace);
      job.setMetadata(metadata);
      
      AnnotationHelper.annotateWithFormat(metadata);

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.DOMAINUID_LABEL, weblogicDomainUID);
      labels.put(LabelConstants.DOMAINNAME_LABEL, weblogicDomainName);
      labels.put(LabelConstants.SERVERNAME_LABEL, spec.getAsName());
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);
      
      V1JobSpec jobSpec = new V1JobSpec();
      V1PodTemplateSpec templateSpec = new V1PodTemplateSpec();
      V1PodSpec podSpec = new V1PodSpec();
      podSpec.setRestartPolicy("Never");
      
      V1Container container = new V1Container();
      container.setName(weblogicDomainUID + "-create-weblogic-domain-job");
      container.setImage(imageName);
      container.setImagePullPolicy(imagePullPolicy);
      
      V1VolumeMount volumeMount = new V1VolumeMount();
      volumeMount.setName("create-weblogic-domain-job-cm-volume");
      volumeMount.setMountPath("/u01/weblogic");
      container.addVolumeMountsItem(volumeMount);

      V1VolumeMount volumeMountShared = new V1VolumeMount();
      volumeMountShared.setName("weblogic-domain-storage-volume");
      volumeMountShared.setMountPath("/shared");
      container.addVolumeMountsItem(volumeMountShared);
      
      V1VolumeMount volumeMountSecrets = new V1VolumeMount();
      volumeMountSecrets.setName("weblogic-credentials-volume");
      volumeMountSecrets.setMountPath("/weblogic-operator/secrets");
      container.addVolumeMountsItem(volumeMountSecrets);
      
      container.setCommand(Arrays.asList("/bin/sh"));
      container.addArgsItem("/u01/weblogic/create-domain-job.sh");
      
      V1EnvVar envItem = new V1EnvVar();
      envItem.setName("SHARED_PATH");
      envItem.setValue("/shared");
      container.addEnvItem(envItem);
      
      podSpec.addContainersItem(container);
      
      V1Volume volumeDomainConfigMap = new V1Volume();
      volumeDomainConfigMap.setName("create-weblogic-domain-job-cm-volume");
      V1ConfigMapVolumeSource cm = new V1ConfigMapVolumeSource();
      cm.setName(KubernetesConstants.DOMAIN_HOME_CONFIG_MAP_NAME);
      cm.setDefaultMode(0555); // read and execute
      volumeDomainConfigMap.setConfigMap(cm);
      podSpec.addVolumesItem(volumeDomainConfigMap);

      if (!info.getClaims().getItems().isEmpty()) {
        V1Volume volume = new V1Volume();
        volume.setName("weblogic-domain-storage-volume");
        V1PersistentVolumeClaimVolumeSource pvClaimSource = new V1PersistentVolumeClaimVolumeSource();
        pvClaimSource.setClaimName(info.getClaims().getItems().iterator().next().getMetadata().getName());
        volume.setPersistentVolumeClaim(pvClaimSource);
        podSpec.addVolumesItem(volume);
      }

      V1Volume volumeSecret = new V1Volume();
      volumeSecret.setName("weblogic-credentials-volume");
      V1SecretVolumeSource secret = new V1SecretVolumeSource();
      secret.setSecretName(spec.getAdminSecret().getName());
      volumeSecret.setSecret(secret);
      podSpec.addVolumesItem(volumeSecret);

      templateSpec.setSpec(podSpec);
      
      jobSpec.setTemplate(templateSpec);
      job.setSpec(jobSpec);
      return job;
    }
  }
}
