// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import static oracle.kubernetes.operator.create.CreateDomainInputs.*;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.BeforeClass;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh
 * creates are correct when the admin node port is enabled, the t3 channel is enabled,
 * there is a weblogic image pull secret, production mode is enabled, and the 
 * weblogic domain storage type is NFS.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesEnabledTest extends CreateDomainGeneratedFilesBaseTest {

  @BeforeClass
  public static void setup() throws Exception {
    setup(
      CreateDomainInputs.newInputs()
        .exposeAdminNodePort("true")
        .exposeAdminT3Channel("true")
        .weblogicImagePullSecretName("test-weblogic-image-pull-secret-name")
        .loadBalancer(LOAD_BALANCER_TRAEFIK)
        .weblogicDomainStorageType(STORAGE_TYPE_NFS)
        .productionModeEnabled("true")
    );
  }

  @Override
  protected V1Job getExpectedCreateWeblogicDomainJob() {
    V1Job expected = super.getExpectedCreateWeblogicDomainJob();
    expected.getSpec().getTemplate().getSpec().addImagePullSecretsItem(newLocalObjectReference()
      .name(getInputs().getWeblogicImagePullSecretName()));
    return expected;
  }

  @Override
  protected Domain getExpectedDomain() {
    Domain expected = super.getExpectedDomain();
    expected.getSpec().withExportT3Channels(newStringList().addElement("T3Channel"));
    // there is only one server startup item in the base domain config - set its node port:
    expected.getSpec().getServerStartup().get(0).withNodePort(Integer.parseInt(getInputs().getAdminNodePort()));
    return expected;
  }

  @Override
  protected V1PersistentVolume getExpectedWeblogicDomainPersistentVolume() {
    V1PersistentVolume expected = super.getExpectedWeblogicDomainPersistentVolume();
    expected.getSpec()
      .nfs(newNFSVolumeSource()
        .server(getInputs().getWeblogicDomainStorageNFSServer())
        .path(getInputs().getWeblogicDomainStoragePath()));
    return expected;
  }
}
