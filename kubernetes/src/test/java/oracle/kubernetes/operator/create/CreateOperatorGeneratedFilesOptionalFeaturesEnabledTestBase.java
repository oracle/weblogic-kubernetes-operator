// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newContainer;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEmptyDirVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVar;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newLocalObjectReference;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolume;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolumeMount;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Service;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when all optional features are enabled: external rest self signed cert remote debug port enabled
 * elk enabled haveimage pull secret
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
  public ExtensionsV1beta1Deployment getExpectedWeblogicOperatorDeployment() {
    ExtensionsV1beta1Deployment expected = super.getExpectedWeblogicOperatorDeployment();
    V1Container operatorContainer =
        expected.getSpec().getTemplate().getSpec().getContainers().get(0);
    operatorContainer
        .addVolumeMountsItem(newVolumeMount().name("log-dir").mountPath("/logs").readOnly(false))
        .addEnvItem(
            newEnvVar().name("REMOTE_DEBUG_PORT").value(getInputs().getInternalDebugHttpPort()));
    expected
        .getSpec()
        .getTemplate()
        .getSpec()
        .addContainersItem(
            newContainer()
                .name("logstash")
                .image(getInputs().getLogStashImage())
                .addArgsItem("-f")
                .addArgsItem("/logs/logstash.conf")
                .addEnvItem(
                    newEnvVar()
                        .name("ELASTICSEARCH_HOST")
                        .value(getInputs().getElasticSearchHost()))
                .addEnvItem(
                    newEnvVar()
                        .name("ELASTICSEARCH_PORT")
                        .value(getInputs().getElasticSearchPort()))
                .addVolumeMountsItem(newVolumeMount().name("log-dir").mountPath("/logs")))
        .addVolumesItem(
            newVolume().name("log-dir").emptyDir(newEmptyDirVolumeSource().medium("Memory")))
        .addImagePullSecretsItem(
            newLocalObjectReference().name(getInputs().getWeblogicOperatorImagePullSecretName()));
    return expected;
  }
}
