// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainCreationImage;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_IMAGE_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createAuxiliaryImage;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setAuxiliaryImages;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setDomainCreationImages;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.LOG_HOME;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.SERVER_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

abstract class DomainAdmissionCheckerTestBase extends AdmissionCheckerTestBase {
  private static final String DUPLICATE_SERVER_NAME = "duplicate-ms";
  private static final String DUPLICATE_CLUSTER_NAME = "duplicate-cluster";
  private static final String BAD_LOG_HOME = "/loghome";
  private static final String BAD_DOMAIN_UID = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz123456789012345";
  private static final String GOOD_CONTAINER_NAME = "abcdef";
  private static final String BAD_PORT_NAME = "abcdefghijklmnopqrstuvw";
  private static final String MODEL_HOME_1 = "/u01/modelhome";
  private static final String WDT_INSTALL_HOME_1 = "/u01/modelhome/wdtinstall";
  private static final String MODEL_HOME_2 = "/u01/wdtinstall/modelhome";
  private static final String WDT_INSTALL_HOME_2 = "/u01/wdtinstall";
  public static final String MOUNT_PATH = "/" + LOG_HOME + "/valume";

  // DomainResource Fatal validation
  @Test
  void whenDomainSourceTypeNullWithModelAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasDuplicateServerNames_returnFalse() {
    proposedDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName(DUPLICATE_SERVER_NAME));
    proposedDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName(DUPLICATE_SERVER_NAME));
    proposedDomain.getSpec().getManagedServers().add(new ManagedServer().withServerName(DUPLICATE_SERVER_NAME));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainHasDuplicateClusterNames_returnFalse() {
    proposedDomain.getSpec().getClusters().add(new V1LocalObjectReference().name(DUPLICATE_CLUSTER_NAME));
    proposedDomain.getSpec().getClusters().add(new V1LocalObjectReference().name(DUPLICATE_CLUSTER_NAME));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainVolumeMountPathInvalid_returnFalse() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(BAD_MOUNT_PATH));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainVolumeMountPathContainsToken_returnTrue() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(MOUNT_PATH_WITH_TOKEN));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainVolumeMountPathContainsTokenInMiddle_returnTrue() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(MOUNT_PATH_WITH_TOKEN_2));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainLogHomeEnabledHasUnmappedLogHome_returnFalse() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(GOOD_MOUNT_PATH));
    proposedDomain.getSpec().setLogHomeEnabled(true);
    proposedDomain.getSpec().setLogHome(BAD_LOG_HOME);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainLogHomeDisabledHasUnmappedLogHome_returnTrue() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(GOOD_MOUNT_PATH));
    proposedDomain.getSpec().setLogHomeEnabled(false);
    proposedDomain.getSpec().setLogHome(BAD_LOG_HOME);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSpecHasReservedEnvs_returnFalse() {
    List<V1EnvVar> list = createEnvVarListWithReservedName();
    proposedDomain.getSpec().setEnv(list);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Nonnull
  private List<V1EnvVar> createEnvVarListWithReservedName() {
    List<V1EnvVar> list = new ArrayList<>();
    list.add(new V1EnvVar().name(SERVER_NAME).value("haha"));
    return list;
  }

  @Test
  void whenDomainASHasReservedEnvs_returnFalse() {
    List<V1EnvVar> list = createEnvVarListWithReservedName();
    proposedDomain.getSpec().getOrCreateAdminServer().setEnv(list);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainHasIllegalSitConfigForMII_returnFalse() {
    proposedDomain.getSpec().setDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().setConfiguration(new Configuration().withOverridesConfigMap("my-config-map"));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainHasIntrospectorJobName_returnFalse() {
    proposedDomain.getSpec().setDomainUid(BAD_DOMAIN_UID);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainASHasReservedContainerName_returnFalse() {
    proposedDomain.getSpec().getOrCreateAdminServer().getContainers().add(new V1Container().name(WLS_CONTAINER_NAME));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainASHasInvalidContainerPortName_returnFalse() {
    setASInvalidContainerPortName();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setASInvalidContainerPortName() {
    proposedDomain.getSpec().getOrCreateAdminServer().getContainers()
        .add(new V1Container().name(GOOD_CONTAINER_NAME).addPortsItem(new V1ContainerPort().name(BAD_PORT_NAME)));
  }

  @Test
  void whenDomainHasWDTInstallHomeContainsModelHome_returnFalse() {
    setWDTInstallNameContainsModelHome();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setWDTInstallNameContainsModelHome() {
    proposedDomain.getSpec().setConfiguration(
        new Configuration().withModel(new Model().withModelHome(MODEL_HOME_1).withWdtInstallHome(WDT_INSTALL_HOME_1)));
  }

  @Test
  void whenDomainHasModelHomeContainsWDTInstallHome_returnFalse() {
    setModelHomeContainsWDTInstallHome();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setModelHomeContainsWDTInstallHome() {
    proposedDomain.getSpec().setConfiguration(
        new Configuration().withModel(new Model().withModelHome(MODEL_HOME_2).withWdtInstallHome(WDT_INSTALL_HOME_2)));
  }

  @Test
  void whenDomainSpecHasMountPathConflictsWithAuxiliaryImages_returnFalse() {
    setAuxiliaryImages(proposedDomain, List.of(new AuxiliaryImage()));
    setDomainSpecMountPathUsingDefaultAuxImageMountPath();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setDomainSpecMountPathUsingDefaultAuxImageMountPath() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH));
  }

  @Test
  void whenDomainASHasMountPathConflictsWithAuxiliaryImages_returnFalse() {
    setAuxiliaryImages(proposedDomain, List.of(new AuxiliaryImage()));
    setDomainASMountPathUsingDefaultAuxImageMountPath();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setDomainASMountPathUsingDefaultAuxImageMountPath() {
    proposedDomain.getSpec().getOrCreateAdminServer().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH));
  }

  @Test
  void whenDomainHasMountPathConflictsWithoutAuxiliaryImages_returnTrue() {
    setDomainSpecMountPathUsingDefaultAuxImageMountPath();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasOnlyOneAuxImageSetsSourceWDTInstallHome_returnTrue() {
    AuxiliaryImage image1 = new AuxiliaryImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    AuxiliaryImage image2 = new AuxiliaryImage().image(AUX_IMAGE_2);
    List<AuxiliaryImage> images = List.of(image1, image2);
    setAuxiliaryImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasMoreThanOneAuxImageSetsSourceWDTInstallHome_returnFalse() {
    AuxiliaryImage image1 = new AuxiliaryImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    AuxiliaryImage image2 = new AuxiliaryImage().image(AUX_IMAGE_2).sourceWDTInstallHome(MODEL_HOME_2);
    List<AuxiliaryImage> images = List.of(image1, image2);
    setAuxiliaryImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainHasOnlyOneDomainCreationImageSetsSourceWDTInstallHome_returnTrue() {
    DomainCreationImage image1 = new DomainCreationImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    DomainCreationImage image2 = new DomainCreationImage().image(AUX_IMAGE_2);
    List<DomainCreationImage> images = List.of(image1, image2);
    setDomainCreationImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasMoreThanOneDomainCreationImageSetsSourceWDTInstallHome_returnFalse() {
    DomainCreationImage image1 = new DomainCreationImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    DomainCreationImage image2 = new DomainCreationImage().image(AUX_IMAGE_2).sourceWDTInstallHome(MODEL_HOME_2);
    List<DomainCreationImage> images = List.of(image1, image2);
    setDomainCreationImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }
}
