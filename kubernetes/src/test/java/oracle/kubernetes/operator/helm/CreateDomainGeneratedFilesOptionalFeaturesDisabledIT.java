// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;

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

  @Test
  public void generatesCorrect_weblogicDomainJobPersistentVolume() {
    assertThat(
        getActualWeblogicDomainJobPersistentVolume(),
        yamlEqualTo(getExpectedWeblogicDomainJobPersistentVolume()));
  }

  private V1PersistentVolume getActualWeblogicDomainJobPersistentVolume() {
    return getWeblogicDomainPersistentVolumeYaml().getWeblogicDomainJobPersistentVolume();
  }

  private V1PersistentVolume getExpectedWeblogicDomainJobPersistentVolume() {
    V1PersistentVolume expected = getExpectedWeblogicDomainPersistentVolume();
    expected.getMetadata().setName(getInputs().getWeblogicDomainJobPersistentVolumeName());
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded,hook-failed")
        .putAnnotationsItem("helm.sh/hook-weight", "-5");
    return expected;
  }

  @Test
  public void generatesCorrect_weblogicDomainJobPersistentVolumeClaim() {
    assertThat(
        getActualWeblogicDomainJobPersistentVolumeClaim(),
        yamlEqualTo(getExpectedWeblogicDomainJobPersistentVolumeClaim()));
  }

  private V1PersistentVolumeClaim getActualWeblogicDomainJobPersistentVolumeClaim() {
    return getWeblogicDomainPersistentVolumeClaimYaml().getWeblogicDomainJobPersistentVolumeClaim();
  }

  private V1PersistentVolumeClaim getExpectedWeblogicDomainJobPersistentVolumeClaim() {
    V1PersistentVolumeClaim expected = getExpectedWeblogicDomainPersistentVolumeClaim();
    expected.getMetadata().setName(getInputs().getWeblogicDomainJobPersistentVolumeClaimName());
    expected
        .getMetadata()
        .putAnnotationsItem("helm.sh/hook", "pre-install")
        .putAnnotationsItem("helm.sh/hook-delete-policy", "hook-succeeded,hook-failed")
        .putAnnotationsItem("helm.sh/hook-weight", "-5");
    return expected;
  }
}
