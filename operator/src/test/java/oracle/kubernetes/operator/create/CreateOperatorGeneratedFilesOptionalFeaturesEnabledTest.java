// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Service;
import org.junit.BeforeClass;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh creates are correct
 * when all optional features are enabled: external rest self signed cert remote debug port enabled
 * elk enabled haveimage pull secret
 */
public class CreateOperatorGeneratedFilesOptionalFeaturesEnabledTest
    extends CreateOperatorGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(
        CreateOperatorInputs.newInputs()
            .setupExternalRestSelfSignedCert()
            .enableDebugging()
            .elkIntegrationEnabled("true")
            .weblogicOperatorImagePullSecretName("test-operator-image-pull-secret-name"));
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorCert() {
    return getInputs().externalOperatorSelfSignedCertPem();
  }

  @Override
  protected String getExpectedExternalWeblogicOperatorKey() {
    return getInputs().externalOperatorSelfSignedKeyPem();
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
                .image("logstash:5")
                .addArgsItem("-f")
                .addArgsItem("/logs/logstash.conf")
                .addEnvItem(
                    newEnvVar()
                        .name("ELASTICSEARCH_HOST")
                        .value("elasticsearch.default.svc.cluster.local"))
                .addEnvItem(newEnvVar().name("ELASTICSEARCH_PORT").value("9200"))
                .addVolumeMountsItem(newVolumeMount().name("log-dir").mountPath("/logs")))
        .addVolumesItem(
            newVolume().name("log-dir").emptyDir(newEmptyDirVolumeSource().medium("Memory")))
        .addImagePullSecretsItem(
            newLocalObjectReference().name(getInputs().getWeblogicOperatorImagePullSecretName()));
    return expected;
  }
}
