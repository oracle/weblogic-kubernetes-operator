package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHostPathVolumeSource;

import io.kubernetes.client.models.V1PersistentVolume;
import oracle.kubernetes.operator.utils.DomainYamlFactory;

public class CreateDomainGeneratedFilesOptionalFeaturesDisabledTestBase
    extends CreateDomainGeneratedFilesBaseTest {
  protected static void defineDomainYamlFactory(DomainYamlFactory factory) throws Exception {
    setup(factory, factory.newDomainValues()); // defaults to admin node port off, t3 channel off,
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
