// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;

/**
 * Parses a generated create-weblogic-domain-job.yaml file into a set of typed k8s java objects
 */
public class ParsedCreateWeblogicDomainJobYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedCreateWeblogicDomainJobYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1ConfigMap getCreateWeblogicDomainConfigMap() {
    return parsedYaml.getConfigMaps().find("domain-" + inputs.getDomainUid() + "-scripts");
  }

  public V1Job getCreateWeblogicDomainJob() {
    return parsedYaml.getJobs().find("domain-" + inputs.getDomainUid() + "-job");
  }

  public V1Job getExpectedBaseCreateWeblogicDomainJob() {
    return
      newJob()
        .metadata(newObjectMeta()
          .name("domain-" + inputs.getDomainUid() + "-job")
          .namespace(inputs.getNamespace()))
        .spec(newJobSpec()
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem("app", "domain-" + inputs.getDomainUid() + "-job")
              .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
            .spec(newPodSpec()
              .restartPolicy("Never")
              .addContainersItem(newContainer()
                .name("domain-job")
                .image("store/oracle/weblogic:12.2.1.3")
                .imagePullPolicy("IfNotPresent")
                .addCommandItem("/bin/sh")
                .addArgsItem("/u01/weblogic/create-domain-job.sh")
                .addEnvItem(newEnvVar()
                  .name("SHARED_PATH")
                  .value("/shared"))
                .addPortsItem(newContainerPort()
                  .containerPort(7001))
                .addVolumeMountsItem(newVolumeMount()
                  .name("config-map-scripts")
                  .mountPath("/u01/weblogic"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("pv-storage")
                  .mountPath("/shared"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("secrets")
                  .mountPath("/weblogic-operator/secrets")))
              .addVolumesItem(newVolume()
                .name("config-map-scripts")
                  .configMap(newConfigMapVolumeSource()
                    .name("domain-" + inputs.getDomainUid() + "-scripts")))
              .addVolumesItem(newVolume()
                .name("pv-storage")
                .persistentVolumeClaim(newPersistentVolumeClaimVolumeSource()
                  .claimName(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeClaimName())))
              .addVolumesItem(newVolume()
                .name("secrets")
                  .secret(newSecretVolumeSource()
                    .secretName(inputs.getSecretName()))))));
  }
}
