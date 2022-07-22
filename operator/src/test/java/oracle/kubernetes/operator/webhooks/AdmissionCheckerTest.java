// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.ClusterAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.DomainAdmissionChecker;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.CLUSTER_NAME_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_IMAGE_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_INTROSPECT_VERSION;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_LOG_HOME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createAuxiliaryImage;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createCluster;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createDomain;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setAuxiliaryImages;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.LOG_HOME;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.SERVER_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class AdmissionCheckerTest {
  private static final String DUPLICATE_SERVER_NAME = "duplicate-ms";
  private static final String DUPLICATE_CLUSTER_NAME = "duplicate-cluster";
  private static final String MOUNT_NAME = "bad-mount";
  private static final String BAD_MOUNT_PATH = "mydir/mount";
  private static final String GOOD_MOUNT_PATH = "/mydir/mount";
  private static final String BAD_LOG_HOME = "/loghome";
  private static final String BAD_DOMAIN_UID = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz123456789012345";
  private static final String GOOD_CONTAINER_NAME = "abcdef";
  private static final String BAD_PORT_NAME = "abcdefghijklmnopqrstuvw";
  private static final String MODEL_HOME_1 = "/u01/modelhome";
  private static final String WDT_INSTALL_HOME_1 = "/u01/modelhome/wdtinstall";
  private static final String MODEL_HOME_2 = "/u01/wdtinstall/modelhome";
  private static final String WDT_INSTALL_HOME_2 = "/u01/wdtinstall";
  public static final String MOUNT_PATH = "/" + LOG_HOME + "/valume";
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  private final DomainResource existingDomain = createDomain();
  private final DomainResource proposedDomain = createDomain();
  private final ClusterResource existingCluster = createCluster();
  private final ClusterResource proposedCluster = createCluster();
  private final DomainAdmissionChecker domainChecker = new DomainAdmissionChecker(existingDomain, proposedDomain);
  private final ClusterAdmissionChecker clusterChecker = new ClusterAdmissionChecker(existingCluster, proposedCluster);

  @BeforeEach
  public void setUp() throws NoSuchFieldException, IOException {
    mementos.add(testSupport.install());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenSameObject_returnTrue() {
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNothingChanged_returnTrue() {
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenSpecAdded_returnTrue() {
    existingDomain.withSpec(null);
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  // DomainResource replica count validation
  @Test
  void whenDomainReplicasChangedAloneValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndDomainReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainImageChangedAndReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome("/home/dir");

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndValid_returnTrue() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenImageChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndOneClusterReplicasChangedValid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(1);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndBothClusterReplicasChangedValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(GOOD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedValidAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeChanged_returnTrue() {
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchanged_returnTrue() {
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypePVAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS).withImage(NEW_IMAGE_NAME).setLogHome(MOUNT_PATH);
    setPVDomainSourceTypeWithAdditionalVolume(existingDomain);
    setPVDomainSourceTypeWithAdditionalVolume(proposedDomain);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setPVDomainSourceTypeWithAdditionalVolume(DomainResource domain) {
    domain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME)
        .getAdditionalVolumeMounts().add(new V1VolumeMount().mountPath(MOUNT_PATH));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgImageChangedAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAddAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIRemoveAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameInDifferentOrderAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_2), createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgDifferentAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, Collections.singletonList(createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  // DomainResource pre-introspection validation

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
    proposedDomain.getSpec().getClusters().add(new ClusterSpec().withClusterName(DUPLICATE_CLUSTER_NAME));
    proposedDomain.getSpec().getClusters().add(new ClusterSpec().withClusterName(DUPLICATE_CLUSTER_NAME));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainVolumeMountPathInvalid_returnFalse() {
    proposedDomain.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(BAD_MOUNT_PATH));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
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
    List<V1EnvVar> list = createEnvVarListWithRerservedName();
    proposedDomain.getSpec().setEnv(list);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @NotNull
  private List<V1EnvVar> createEnvVarListWithRerservedName() {
    List<V1EnvVar> list = new ArrayList<>();
    list.add(new V1EnvVar().name(SERVER_NAME).value("haha"));
    return list;
  }

  @Test
  void whenDomainASHasReservedEnvs_returnFalse() {
    List<V1EnvVar> list = createEnvVarListWithRerservedName();
    proposedDomain.getSpec().getOrCreateAdminServer().setEnv(list);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainClusterHasReservedEnvs_returnFalse() {
    List<V1EnvVar> list = createEnvVarListWithRerservedName();
    proposedDomain.getSpec().getCluster(CLUSTER_NAME_1).setEnv(list);

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
  void whenDomainASHasReservedContainerPortName_returnFalse() {
    proposedDomain.getSpec().getOrCreateAdminServer().getContainers().add(new V1Container().name(WLS_CONTAINER_NAME));

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainClusterHasReservedContainerPortName_returnFalse() {
    proposedDomain.getSpec().getCluster(CLUSTER_NAME_1).getContainers().add(new V1Container().name(WLS_CONTAINER_NAME));

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
  void whenDomainClusterHasInvalidContainerPortName_returnFalse() {
    setClusterInvalidContainerPortName();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setClusterInvalidContainerPortName() {
    proposedDomain.getSpec().getCluster(CLUSTER_NAME_1).getContainers()
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
  void whenDomainClusterHasMountPathConflictsWithAuxiliaryImages_returnFalse() {
    setAuxiliaryImages(proposedDomain, List.of(new AuxiliaryImage()));
    setDomainClusterMountPathUsingDefaultAuxImageMountPath();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setDomainClusterMountPathUsingDefaultAuxImageMountPath() {
    proposedDomain.getSpec().getCluster(CLUSTER_NAME_1).getAdditionalVolumeMounts()
        .add(new V1VolumeMount().mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH));
  }


  @Test
  void whenDomainHasMountPathConflictsWithoutAuxiliaryImages_returnTrue() {
    setDomainSpecMountPathUsingDefaultAuxImageMountPath();

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasOnlyOneImageSetsSourceWDTInstallHome_returnTrue() {
    AuxiliaryImage image1 = new AuxiliaryImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    AuxiliaryImage image2 = new AuxiliaryImage().image(AUX_IMAGE_2);
    List<AuxiliaryImage> images = List.of(image1, image2);
    setAuxiliaryImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainHasMoreThanOneImageSetsSourceWDTInstallHome_returnFalse() {
    AuxiliaryImage image1 = new AuxiliaryImage().image(AUX_IMAGE_1).sourceWDTInstallHome(MODEL_HOME_1);
    AuxiliaryImage image2 = new AuxiliaryImage().image(AUX_IMAGE_2).sourceWDTInstallHome(MODEL_HOME_2);
    List<AuxiliaryImage> images = List.of(image1, image2);
    setAuxiliaryImages(proposedDomain, images);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }


  // ClusterResource validation
  @Test
  void whenClusterReplicasChangedAndValid_returnTrue() {
    testSupport.defineResources(existingDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAndInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(clusterChecker.hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasValid_returnTrue() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(clusterChecker.hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(clusterChecker.hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndReadDomainFailed404_returnFalseWithException() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(clusterChecker.hasException(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedValidAndReadDomainFailed404_returnTrueNoException() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(clusterChecker.hasException(), equalTo(false));
  }
}
