// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;

import io.kubernetes.client.models.V1PersistentVolume;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the admin node port is disabled, the t3 channel is disabled, there is no weblogic domain
 * image pull secret, production mode is disabled and the weblogic domain storage type is HOST_PATH.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesDisabledTest
    extends CreateDomainGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(CreateDomainInputs.newInputs()); // defaults to admin node port off, t3 channel off,
  }

  @Override
  protected V1PersistentVolume getExpectedWeblogicDomainPersistentVolume() {
    V1PersistentVolume expected = super.getExpectedWeblogicDomainPersistentVolume();
    expected
        .getSpec()
        .hostPath(newHostPathVolumeSource().path(getInputs().getWeblogicDomainStoragePath()));
    return expected;
  }
}
