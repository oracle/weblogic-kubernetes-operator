// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1KeyToPath;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;

import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newContainer;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEmptyDirVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVar;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newLocalObjectReference;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolume;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolumeMount;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when all optional features are enabled: external rest self signed cert remote debug port enabled
 * elk enabled have image pull secret
 */
public abstract class CreateOperatorGeneratedFilesOptionalFeaturesEnabledTestBase
    extends CreateOperatorGeneratedFilesTestBase {

  protected static void defineOperatorYamlFactory(OperatorYamlFactory factory) throws Exception {
    setup(
        factory,
        factory
            .newOperatorValues()
            .setupExternalRestEnabled()
            .enableDebugging()
            .suspendOnDebugStartup("true")
            .elkIntegrationEnabled("true")
            .weblogicOperatorImagePullSecretName("test-operator-image-pull-secret-name"));
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorCert() {
    return getInputs().externalOperatorCustomCertPem();
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorKey() {
    return getInputs().externalOperatorCustomKeyPem();
  }

  @Override
  protected V1Service getExpectedExternalWeblogicOperatorService() {
    return getExpectedExternalWeblogicOperatorService(true, true);
  }

  @Override
  public V1Deployment getExpectedWeblogicOperatorDeployment() {
    V1Deployment expected = super.getExpectedWeblogicOperatorDeployment();

    boolean isElkIntegrationEnabled = Boolean.parseBoolean(getInputs().getElkIntegrationEnabled());

    V1Container operatorContainer =
        expected.getSpec().getTemplate().getSpec().getContainers().get(0);
    operatorContainer.addVolumeMountsItem(
        newVolumeMount().name("log-dir").mountPath("/logs").readOnly(false));
    expectRemoteDebug(operatorContainer, "y");
    expected
        .getSpec()
        .getTemplate()
        .getSpec()
        .addVolumesItem(
            newVolume().name("log-dir").emptyDir(newEmptyDirVolumeSource().medium("Memory")))
        .addImagePullSecretsItem(
            newLocalObjectReference().name(getInputs().getWeblogicOperatorImagePullSecretName()));

    V1Container logstashContainer = newContainer()
        .name("logstash")
        .image(getInputs().getLogStashImage())
        .addEnvItem(
            newEnvVar()
                .name("ELASTICSEARCH_HOST")
                .value(getInputs().getElasticSearchHost()))
        .addEnvItem(
            newEnvVar()
                .name("ELASTICSEARCH_PORT")
                .value(getInputs().getElasticSearchPort()))
        .addEnvItem(
            newEnvVar()
                .name("ELASTICSEARCH_PROTOCOL")
                .value(getInputs().getElasticSearchProtocol()))
        .addVolumeMountsItem(newVolumeMount().name("log-dir").mountPath("/logs"));

    expected.getSpec().getTemplate().getSpec().addContainersItem(logstashContainer);

    if (isElkIntegrationEnabled) {
      expected.getSpec().getTemplate().getSpec()
          .addVolumesItem(newVolume()
              .name("logstash-pipeline-volume")
              .configMap(new V1ConfigMapVolumeSource()
                  .name("weblogic-operator-logstash-cm")
                  .addItemsItem(new V1KeyToPath().key("logstash.conf").path("logstash.conf"))))
          .addVolumesItem(newVolume()
            .name("logstash-config-volume")
            .configMap(new V1ConfigMapVolumeSource()
                .name("weblogic-operator-logstash-cm")
                .addItemsItem(new V1KeyToPath().key("logstash.yml").path("logstash.yml"))))
          .addVolumesItem(newVolume()
              .name("logstash-certs-secret-volume")
              .secret(new V1SecretVolumeSource()
                  .secretName("logstash-certs-secret")
                  .optional(true)));

      logstashContainer.addVolumeMountsItem(
          newVolumeMount().name("logstash-pipeline-volume").mountPath("/usr/share/logstash/pipeline"));
      logstashContainer.addVolumeMountsItem(
          newVolumeMount().name("logstash-config-volume")
              .mountPath("/usr/share/logstash/config/logstash.yml")
              .subPath("logstash.yml"));
      logstashContainer.addVolumeMountsItem(
          newVolumeMount().name("logstash-certs-secret-volume").mountPath("/usr/share/logstash/config/certs"));
    }
    return expected;
  }
}
