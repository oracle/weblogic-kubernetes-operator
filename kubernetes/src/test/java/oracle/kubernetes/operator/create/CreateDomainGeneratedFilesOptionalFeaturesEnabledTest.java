// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.CreateDomainInputs.*;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import oracle.kubernetes.operator.utils.CreateDomainInputs;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the admin node port is enabled, the t3 channel is enabled, there is a weblogic image pull
 * secret, production mode is enabled, and the weblogic domain storage type is NFS.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesEnabledTest
    extends CreateDomainGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(
        CreateDomainInputs.newInputs()
            .exposeAdminNodePort("true")
            .exposeAdminT3Channel("true")
            .clusterType("CONFIGURED")
            .weblogicImagePullSecretName("test-weblogic-image-pull-secret-name")
            .loadBalancer(LOAD_BALANCER_APACHE)
            .loadBalancerAppPrepath("/loadBalancerAppPrePath")
            .loadBalancerExposeAdminPort("false")
            .weblogicDomainStorageType(STORAGE_TYPE_NFS)
            .productionModeEnabled("true"));
  }

  @Override
  protected V1Job getExpectedCreateWeblogicDomainJob() {
    V1Job expected = super.getExpectedCreateWeblogicDomainJob();
    expected
        .getSpec()
        .getTemplate()
        .getSpec()
        .addImagePullSecretsItem(
            newLocalObjectReference().name(getInputs().getWeblogicImagePullSecretName()));
    return expected;
  }

  @Override
  protected Domain getExpectedDomain() {
    Domain expected = super.getExpectedDomain();
    expected.getSpec().withExportT3Channels(newStringList().addElement("T3Channel"));
    // there is only one server startup item in the base domain config - set its node port:
    expected
        .getSpec()
        .getServerStartup()
        .get(0)
        .withNodePort(Integer.parseInt(getInputs().getAdminNodePort()));
    return expected;
  }

  @Override
  protected V1PersistentVolume getExpectedWeblogicDomainPersistentVolume() {
    V1PersistentVolume expected = super.getExpectedWeblogicDomainPersistentVolume();
    expected
        .getSpec()
        .nfs(
            newNFSVolumeSource()
                .server(getInputs().getWeblogicDomainStorageNFSServer())
                .path(getInputs().getWeblogicDomainStoragePath()));
    return expected;
  }

  @Override
  public void generatesCorrect_loadBalancerDeployment() throws Exception {
    assertThat(getActualApacheDeployment(), yamlEqualTo(getExpectedApacheDeployment()));
  }

  @Override
  public void generatesCorrect_loadBalancerServiceAccount() throws Exception {
    assertThat(getActualApacheServiceAccount(), yamlEqualTo(getExpectedApacheServiceAccount()));
  }

  @Override
  public void generatesCorrect_loadBalancerConfigMap() throws Exception {
    // No config map in generated yaml for LOAD_BALANCER_APACHE
  }

  @Override
  public void generatesCorrect_loadBalancerService() throws Exception {
    assertThat(getActualApacheService(), yamlEqualTo(getExpectedApacheService()));
  }

  @Override
  public void generatesCorrect_loadBalancerDashboardService() throws Exception {
    // No dashboard service in generated yaml for LOAD_BALANCER_APACHE
  }

  @Override
  public void generatesCorrect_loadBalancerClusterRole() throws Exception {
    assertThat(getActualApacheClusterRole(), yamlEqualTo(getExpectedApacheClusterRole()));
  }

  @Override
  public void generatesCorrect_loadBalancerClusterRoleBinding() throws Exception {
    assertThat(
        getActualApacheClusterRoleBinding(), yamlEqualTo(getExpectedApacheClusterRoleBinding()));
  }

  @Override
  public void loadBalancerSecurityYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
        getApacheSecurityYaml().getObjectCount(),
        is(getApacheSecurityYaml().getExpectedObjectCount()));
  }

  @Override
  public void loadBalancerYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(getApacheYaml().getObjectCount(), is(getApacheYaml().getExpectedObjectCount()));
  }
}
