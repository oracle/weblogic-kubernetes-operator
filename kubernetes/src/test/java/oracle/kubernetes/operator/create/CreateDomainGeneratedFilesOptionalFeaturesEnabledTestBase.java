// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.DomainValues.LOAD_BALANCER_APACHE;
import static oracle.kubernetes.operator.utils.DomainValues.STORAGE_TYPE_NFS;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newLocalObjectReference;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newNFSVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newStringList;
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory.forDomain;
import static org.hamcrest.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import oracle.kubernetes.operator.utils.DomainValues;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the admin node port is enabled, the t3 channel is enabled, there is a weblogic image pull
 * secret, production mode is enabled, and the weblogic domain storage type is NFS.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesEnabledTestBase
    extends CreateDomainGeneratedFilesBaseTest {

  protected static void defineDomainYamlFactory(DomainYamlFactory factory) throws Exception {
    setup(
        factory, withFeaturesEnabled(factory.newDomainValues()).loadBalancer(LOAD_BALANCER_APACHE));
  }

  protected static DomainValues withFeaturesEnabled(DomainValues values) {
    return values
        .exposeAdminNodePort("true")
        .exposeAdminT3Channel("true")
        .clusterType("CONFIGURED")
        .weblogicImagePullSecretName("test-weblogic-image-pull-secret-name")
        .weblogicDomainStorageType(STORAGE_TYPE_NFS)
        .productionModeEnabled("true")
        .loadBalancerAppPrepath("/loadBalancerAppPrePath")
        .loadBalancerExposeAdminPort("false");
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
    // set the node port for the admin server:
    forDomain(expected)
        .configureAdminServer()
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
  public void generatesCorrect_loadBalancerDeployment() {
    assertThat(getActualApacheDeployment(), yamlEqualTo(getExpectedApacheDeployment()));
  }

  @Override
  public void generatesCorrect_loadBalancerServiceAccount() {
    assertThat(getActualApacheServiceAccount(), yamlEqualTo(getExpectedApacheServiceAccount()));
  }

  @Override
  public void generatesCorrect_loadBalancerConfigMap() {
    // No config map in generated yaml for LOAD_BALANCER_APACHE
  }

  @Override
  public void generatesCorrect_loadBalancerService() {
    assertThat(getActualApacheService(), yamlEqualTo(getExpectedApacheService()));
  }

  @Override
  public void generatesCorrect_loadBalancerDashboardService() {
    // No dashboard service in generated yaml for LOAD_BALANCER_APACHE
  }

  @Override
  public void generatesCorrect_loadBalancerClusterRole() {
    assertThat(getActualApacheClusterRole(), yamlEqualTo(getExpectedApacheClusterRole()));
  }

  @Override
  public void generatesCorrect_loadBalancerClusterRoleBinding() {
    assertThat(
        getActualApacheClusterRoleBinding(), yamlEqualTo(getExpectedApacheClusterRoleBinding()));
  }
}
