// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import oracle.kubernetes.operator.create.CreateDomainGeneratedFilesOptionalFeaturesDisabledTestBase;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateDomainGeneratedFilesOptionalFeaturesDisabledIT
    extends CreateDomainGeneratedFilesOptionalFeaturesDisabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineDomainYamlFactory(new HelmDomainYamlFactory());
  }

  @Override
  protected V1Job getExpectedCreateWeblogicDomainJob() {
    V1Job expected = super.getExpectedCreateWeblogicDomainJob();
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded")
        .putAnnotationsItem("helm.sh/hook-weight", "0");
    expected
        .getSpec()
        .getTemplate()
        .getSpec()
        .getVolumes()
        .get(1)
        .getPersistentVolumeClaim()
        .claimName(getInputs().getWeblogicDomainJobPersistentVolumeClaimName());
    return expected;
  }

  @Override
  protected V1ConfigMap getExpectedCreateWeblogicDomainJobConfigMap() {
    V1ConfigMap expected = super.getExpectedCreateWeblogicDomainJobConfigMap();
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded")
        .putAnnotationsItem("helm.sh/hook-weight", "-5");
    return expected;
  }

  @Test
  public void generatesCorrect_weblogicDomainJobPersistentVolume() throws Exception {
    assertThat(
        getActualWeblogicDomainJobPersistentVolume(),
        yamlEqualTo(getExpectedWeblogicDomainJobPersistentVolume()));
  }

  protected V1PersistentVolume getActualWeblogicDomainJobPersistentVolume() {
    return getWeblogicDomainPersistentVolumeYaml().getWeblogicDomainJobPersistentVolume();
  }

  protected V1PersistentVolume getExpectedWeblogicDomainJobPersistentVolume() {
    V1PersistentVolume expected = getExpectedWeblogicDomainPersistentVolume();
    expected.getMetadata().setName(getInputs().getWeblogicDomainJobPersistentVolumeName());
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded,hook-failed")
        .putAnnotationsItem("helm.sh/hook-weight", "-5");
    ;
    return expected;
  }

  @Test
  public void generatesCorrect_weblogicDomainJobPersistentVolumeClaim() throws Exception {
    assertThat(
        getActualWeblogicDomainJobPersistentVolumeClaim(),
        yamlEqualTo(getExpectedWeblogicDomainJobPersistentVolumeClaim()));
  }

  protected V1PersistentVolumeClaim getActualWeblogicDomainJobPersistentVolumeClaim() {
    return getWeblogicDomainPersistentVolumeClaimYaml().getWeblogicDomainJobPersistentVolumeClaim();
  }

  protected V1PersistentVolumeClaim getExpectedWeblogicDomainJobPersistentVolumeClaim() {
    V1PersistentVolumeClaim expected = getExpectedWeblogicDomainPersistentVolumeClaim();
    expected.getMetadata().setName(getInputs().getWeblogicDomainJobPersistentVolumeClaimName());
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded,hook-failed")
        .putAnnotationsItem("helm.sh/hook-weight", "-5");
    ;
    return expected;
  }
}
