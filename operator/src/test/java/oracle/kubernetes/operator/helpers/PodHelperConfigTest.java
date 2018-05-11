// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.utils.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1Pod;
import java.util.HashMap;
import java.util.List;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.PodTuning;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import org.junit.Test;

/** Test that PodHelper computes the correct admin and managed server pod configurations */
public class PodHelperConfigTest {

  private static final String NAMESPACE = "test-namespace";
  private static final String DOMAIN_UID = "test-domain-uid";
  private static final String DOMAIN_NAME = "TestDomain";
  private static final String ADMIN_SERVER_NAME = "TestAdminServer";
  private static final String CLUSTER_NAME = "TestCluster";
  private static final String MANAGED_SERVER_NAME = "TestManagedServer";
  private static final String WEBLOGIC_CREDENTIALS_SECRET_NAME =
      "test-weblogic-credentials-secret-name";
  private static final int ADMIN_SERVER_PORT = 7654;
  private static final int MANAGED_SERVER_PORT = 4567;
  private static final String WEBLOGIC_DOMAIN_PERSISTENT_VOLUME_CLAIM_NAME =
      "test-weblogic-domain-pvc-name";
  private static final String INTERNAL_OPERATOR_CERT_FILE = "test-internal-operator-cert-file";
  private static final String CUSTOM_LATEST_IMAGE = "custom-image:latest";
  private static final String ADMIN_OPTION1_NAME = "AdminOption1Name";
  private static final String ADMIN_OPTION1_VALUE = "AdminOption1Value";
  private static final String ADMIN_OPTION2_NAME = "AdminOption2Name";
  private static final String ADMIN_OPTION2_VALUE = "AdminOption2Value";
  private static final String MANAGED_OPTION1_NAME = "ManagedOption1Name";
  private static final String MANAGED_OPTION1_VALUE = "ManagedOption1Value";
  private static final String MANAGED_OPTION2_NAME = "ManagedOption2Name";
  private static final String MANAGED_OPTION2_VALUE = "ManagedOption2Value";
  private static final String MANAGED_OPTION3_NAME = "ManagedOption3Name";
  private static final String MANAGED_OPTION3_VALUE = "ManagedOption3Value";
  private static final String MANAGED_OPTION4_NAME = "ManagedOption4Name";
  private static final String MANAGED_OPTION4_VALUE = "ManagedOption4Value";

  @Test
  public void computedAdminServerPodConfigForDefaults_isCorrect() throws Exception {
    assertThat(
        getActualAdminServerPodConfigForDefaults(),
        yamlEqualTo(getDesiredAdminServerPodConfigForDefaults()));
  }

  private V1Pod getDesiredAdminServerPodConfigForDefaults() {
    return getDesiredAdminServerPodConfigForDefaults(DEFAULT_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
  }

  private V1Pod getActualAdminServerPodConfigForDefaults() throws Exception {
    return getActualAdminServerPodConfig(
        getDomainCustomResourceForDefaults(null, null), // default image & image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void computedManagedServerPodConfigForDefaults_isCorrect() throws Exception {
    assertThat(
        getActualManagedServerPodConfigForDefaults(),
        yamlEqualTo(getDesiredManagedServerPodConfigForDefaults()));
  }

  private V1Pod getDesiredManagedServerPodConfigForDefaults() {
    return getDesiredManagedServerPodConfigForDefaults(DEFAULT_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
  }

  private V1Pod getActualManagedServerPodConfigForDefaults() throws Exception {
    return getActualManagedServerPodConfig(
        getDomainCustomResourceForDefaults(null, null), // default image & image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void computedAdminServerPodConfigForCustomLatestImageAndDefaults_isCorrect()
      throws Exception {
    assertThat(
        getActualAdminServerPodConfigForCustomLatestImageAndDefaults(),
        yamlEqualTo(getDesiredAdminServerPodConfigForCustomLatestImageAndDefaults()));
  }

  private V1Pod getDesiredAdminServerPodConfigForCustomLatestImageAndDefaults() {
    return getDesiredAdminServerPodConfigForDefaults(CUSTOM_LATEST_IMAGE, ALWAYS_IMAGEPULLPOLICY);
  }

  private V1Pod getActualAdminServerPodConfigForCustomLatestImageAndDefaults() throws Exception {
    return getActualAdminServerPodConfig(
        getDomainCustomResourceForDefaults(
            CUSTOM_LATEST_IMAGE, null), // custom latest image & default image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void computedManagedServerPodConfigForCustomLatestImageAndDefaults_isCorrect()
      throws Exception {
    assertThat(
        getActualManagedServerPodConfigForCustomLatestImageAndDefaults(),
        yamlEqualTo(getDesiredManagedServerPodConfigForCustomLatestImageAndDefaults()));
  }

  private V1Pod getDesiredManagedServerPodConfigForCustomLatestImageAndDefaults() {
    return getDesiredManagedServerPodConfigForDefaults(CUSTOM_LATEST_IMAGE, ALWAYS_IMAGEPULLPOLICY);
  }

  private V1Pod getActualManagedServerPodConfigForCustomLatestImageAndDefaults() throws Exception {
    return getActualManagedServerPodConfig(
        getDomainCustomResourceForDefaults(
            CUSTOM_LATEST_IMAGE, null), // custom latest image & default image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void
      computedAdminServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults_isCorrect()
          throws Exception {
    assertThat(
        getActualAdminServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults(),
        yamlEqualTo(
            getDesiredAdminServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults()));
  }

  private V1Pod
      getDesiredAdminServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults() {
    return getDesiredAdminServerPodConfigForDefaults(
        CUSTOM_LATEST_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
  }

  private V1Pod
      getActualAdminServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults()
          throws Exception {
    return getActualAdminServerPodConfig(
        getDomainCustomResourceForDefaults(
            CUSTOM_LATEST_IMAGE,
            IFNOTPRESENT_IMAGEPULLPOLICY), // custom latest image & custom image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void
      computedManagedServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults_isCorrect()
          throws Exception {
    assertThat(
        getActualManagedServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults(),
        yamlEqualTo(
            getDesiredManagedServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults()));
  }

  private V1Pod
      getDesiredManagedServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults() {
    return getDesiredManagedServerPodConfigForDefaults(
        CUSTOM_LATEST_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
  }

  private V1Pod
      getActualManagedServerPodConfigForCustomLatestImageAndCustomImagePullPolicyAndDefaults()
          throws Exception {
    return getActualManagedServerPodConfig(
        getDomainCustomResourceForDefaults(
            CUSTOM_LATEST_IMAGE,
            IFNOTPRESENT_IMAGEPULLPOLICY), // custom latest image & custom image pull policy
        newEmptyPersistentVolumeClaimList());
  }

  @Test
  public void computedAdminServerPodConfigForPersistentVolumeClaimAndDefaults_isCorrect()
      throws Exception {
    assertThat(
        getActualAdminServerPodConfigForPersistentVolumeClaimAndDefaults(),
        yamlEqualTo(getDesiredAdminServerPodConfigForPersistentVolumeClaimAndDefaults()));
  }

  private V1Pod getDesiredAdminServerPodConfigForPersistentVolumeClaimAndDefaults() {
    V1Pod pod =
        getDesiredAdminServerPodConfigForDefaults(DEFAULT_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
    setDesiredPersistentVolumeClaim(pod);
    return pod;
  }

  private V1Pod getActualAdminServerPodConfigForPersistentVolumeClaimAndDefaults()
      throws Exception {
    return getActualAdminServerPodConfig(
        getDomainCustomResourceForDefaults(null, null), // default image & default image pull policy
        newPersistentVolumeClaimListForPersistentVolumeClaim());
  }

  @Test
  public void computedManagedServerPodConfigForPersistentVolumeClaimAndDefaults_isCorrect()
      throws Exception {
    assertThat(
        getActualManagedServerPodConfigForPersistentVolumeClaimAndDefaults(),
        yamlEqualTo(getDesiredManagedServerPodConfigForPersistentVolumeClaimAndDefaults()));
  }

  private V1Pod getDesiredManagedServerPodConfigForPersistentVolumeClaimAndDefaults() {
    V1Pod pod =
        getDesiredManagedServerPodConfigForDefaults(DEFAULT_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
    setDesiredPersistentVolumeClaim(pod);
    return pod;
  }

  private V1Pod getActualManagedServerPodConfigForPersistentVolumeClaimAndDefaults()
      throws Exception {
    return getActualManagedServerPodConfig(
        getDomainCustomResourceForDefaults(null, null), // default image & default image pull policy
        newPersistentVolumeClaimListForPersistentVolumeClaim());
  }

  @Test
  public void computedAdminServerPodConfigForServerStartupAndDefaults_isCorrect() throws Exception {
    assertThat(
        getActualAdminServerPodConfigForServerStartupAndDefaults(),
        yamlEqualTo(getDesiredAdminServerPodConfigForServerStartupAndDefaults()));
  }

  private V1Pod getDesiredAdminServerPodConfigForServerStartupAndDefaults() {
    V1Pod pod =
        getDesiredAdminServerPodConfigForDefaults(DEFAULT_IMAGE, IFNOTPRESENT_IMAGEPULLPOLICY);
    // the custom env vars need to be added to the beginning of the list:
    pod.getSpec()
        .getContainers()
        .get(0)
        .getEnv()
        .add(0, newEnvVar().name(ADMIN_OPTION2_NAME).value(ADMIN_OPTION2_VALUE));
    pod.getSpec()
        .getContainers()
        .get(0)
        .getEnv()
        .add(0, newEnvVar().name(ADMIN_OPTION1_NAME).value(ADMIN_OPTION1_VALUE));
    return pod;
  }

  private V1Pod getActualAdminServerPodConfigForServerStartupAndDefaults() throws Exception {
    Domain domain =
        getDomainCustomResourceForDefaults(null, null); // default image & default image pull policy
    domain.getSpec().withServerStartup(newTestServersStartupList());
    return getActualAdminServerPodConfig(domain, newPersistentVolumeClaimList()); // no pvc
  }

  // don't test sending in a server startup list when creating a managed pod config since
  // PodHelper doesn't pay attention to the server startup list - intead, it uses the
  // packet's env vars (which we've already tested)

  private V1Pod getActualAdminServerPodConfig(Domain domain, V1PersistentVolumeClaimList claims)
      throws Exception {
    return (new TestAdminPodStep()).computeAdminPodConfig(domain, claims);
  }

  private V1Pod getActualManagedServerPodConfig(Domain domain, V1PersistentVolumeClaimList claims)
      throws Exception {
    return (new TestManagedPodStep()).computeManagedPodConfig(domain, claims);
  }

  @SuppressWarnings("serial")
  private static class TestTuningParameters extends HashMap<String, String>
      implements TuningParameters {

    @Override
    public MainTuning getMainTuning() {
      return null;
    }

    @Override
    public CallBuilderTuning getCallBuilderTuning() {
      return null;
    }

    @Override
    public WatchTuning getWatchTuning() {
      return null;
    }

    @Override
    public PodTuning getPodTuning() {
      PodTuning pod =
          new PodTuning(
              /* "readinessProbeInitialDelaySeconds" */ 2,
              /* "readinessProbeTimeoutSeconds" */ 5,
              /* "readinessProbePeriodSeconds" */ 10,
              /* "livenessProbeInitialDelaySeconds" */ 10,
              /* "livenessProbeTimeoutSeconds" */ 5,
              /* "livenessProbePeriodSeconds" */ 10);
      return pod;
    }
  }

  private static class TestAdminPodStep extends PodHelper.AdminPodStep {
    public TestAdminPodStep() {
      super(null);
    }

    public V1Pod computeAdminPodConfig(Domain domain, V1PersistentVolumeClaimList claims)
        throws Exception {
      Packet packet = newPacket(domain, claims);
      packet.put(ProcessingConstants.SERVER_NAME, domain.getSpec().getAsName());
      packet.put(ProcessingConstants.PORT, domain.getSpec().getAsPort());
      return super.computeAdminPodConfig(new TestTuningParameters(), packet);
    }

    @Override
    protected String getInternalOperatorCertFile(TuningParameters configMapHelper, Packet packet) {
      // Normally, this would open the files for the config map in the operator.
      return INTERNAL_OPERATOR_CERT_FILE;
    }
  }

  private static class TestManagedPodStep extends PodHelper.ManagedPodStep {
    public TestManagedPodStep() {
      super(null);
    }

    public V1Pod computeManagedPodConfig(Domain domain, V1PersistentVolumeClaimList claims)
        throws Exception {
      Packet packet = newPacket(domain, claims);
      packet.put(
          ProcessingConstants.SERVER_SCAN,
          // no listen address, no network access points since PodHelper doesn't use them:
          new WlsServerConfig(
              MANAGED_SERVER_NAME, MANAGED_SERVER_PORT, null, null, false, null, null));
      packet.put(
          ProcessingConstants.CLUSTER_SCAN,
          // don't attach WlsServerConfigs for the managed server to the WlsClusterConfig
          // since PodHelper doesn't use them:
          new WlsClusterConfig(CLUSTER_NAME));
      packet.put(
          ProcessingConstants.ENVVARS,
          newEnvVarList()
              .addElement(newEnvVar().name(MANAGED_OPTION1_NAME).value(MANAGED_OPTION1_VALUE))
              .addElement(newEnvVar().name(MANAGED_OPTION2_NAME).value(MANAGED_OPTION2_VALUE)));
      return super.computeManagedPodConfig(new TestTuningParameters(), packet);
    }
  }

  private static Packet newPacket(Domain domain, V1PersistentVolumeClaimList claims) {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    info.setClaims(claims);
    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info));
    return packet;
  }

  private V1PersistentVolumeClaimList newEmptyPersistentVolumeClaimList() {
    return newPersistentVolumeClaimList();
  }

  private V1PersistentVolumeClaimList newPersistentVolumeClaimListForPersistentVolumeClaim() {
    return newPersistentVolumeClaimList()
        .addItemsItem(
            newPersistentVolumeClaim()
                .metadata(newObjectMeta().name(WEBLOGIC_DOMAIN_PERSISTENT_VOLUME_CLAIM_NAME)));
  }

  private List<ServerStartup> newTestServersStartupList() {
    return newServerStartupList()
        .addElement(
            newServerStartup()
                .withDesiredState("RUNNING")
                .withServerName(ADMIN_SERVER_NAME)
                .withEnv(
                    newEnvVarList()
                        .addElement(newEnvVar().name(ADMIN_OPTION1_NAME).value(ADMIN_OPTION1_VALUE))
                        .addElement(
                            newEnvVar().name(ADMIN_OPTION2_NAME).value(ADMIN_OPTION2_VALUE))))
        .addElement(
            newServerStartup()
                .withDesiredState("RUNNING")
                .withServerName(MANAGED_SERVER_NAME)
                .withEnv(
                    newEnvVarList()
                        .addElement(
                            newEnvVar().name(MANAGED_OPTION3_NAME).value(MANAGED_OPTION3_VALUE))
                        .addElement(
                            newEnvVar().name(MANAGED_OPTION4_NAME).value(MANAGED_OPTION4_VALUE))));
  }

  private Domain getDomainCustomResourceForDefaults(String image, String imagePullPolicy) {
    DomainSpec spec = newDomainSpec();
    spec.setDomainUID(DOMAIN_UID);
    spec.setDomainName(DOMAIN_NAME);
    spec.setAsName(ADMIN_SERVER_NAME);
    spec.setAdminSecret(newSecretReference().name(WEBLOGIC_CREDENTIALS_SECRET_NAME));
    spec.setAsPort(ADMIN_SERVER_PORT);
    if (image != null) {
      spec.setImage(image);
    }
    if (imagePullPolicy != null) {
      spec.setImagePullPolicy(imagePullPolicy);
    }
    Domain domain = new Domain();
    domain.setMetadata(newObjectMeta().namespace(NAMESPACE));
    domain.setSpec(spec);
    return domain;
  }

  private V1Pod getDesiredAdminServerPodConfigForDefaults(String image, String imagePullPolicy) {
    V1Pod pod =
        getDesiredBaseServerPodConfigForDefaults(
            image, imagePullPolicy, ADMIN_SERVER_NAME, ADMIN_SERVER_PORT);
    pod.getSpec().hostname(DOMAIN_UID + "-" + ADMIN_SERVER_NAME.toLowerCase());
    pod.getSpec()
        .getContainers()
        .get(0)
        .getEnv()
        .add(0, newEnvVar().name("INTERNAL_OPERATOR_CERT").value(INTERNAL_OPERATOR_CERT_FILE));
    return pod;
  }

  private V1Pod getDesiredManagedServerPodConfigForDefaults(String image, String imagePullPolicy) {
    V1Pod pod =
        getDesiredBaseServerPodConfigForDefaults(
            image, imagePullPolicy, MANAGED_SERVER_NAME, MANAGED_SERVER_PORT);
    pod.getSpec()
        .getContainers()
        .get(0)
        .getEnv()
        .add(0, newEnvVar().name(MANAGED_OPTION1_NAME).value(MANAGED_OPTION1_VALUE));
    pod.getSpec()
        .getContainers()
        .get(0)
        .getEnv()
        .add(1, newEnvVar().name(MANAGED_OPTION2_NAME).value(MANAGED_OPTION2_VALUE));
    pod.getMetadata().putLabelsItem(CLUSTERNAME_LABEL, CLUSTER_NAME);
    pod.getSpec()
        .getContainers()
        .get(0)
        .addCommandItem(ADMIN_SERVER_NAME)
        .addCommandItem(String.valueOf(ADMIN_SERVER_PORT));
    return pod;
  }

  private void setDesiredPersistentVolumeClaim(V1Pod pod) {
    pod.getSpec()
        .getVolumes()
        .add(
            0,
            newVolume() // needs to be first in the list
                .name("weblogic-domain-storage-volume")
                .persistentVolumeClaim(
                    newPersistentVolumeClaimVolumeSource()
                        .claimName(WEBLOGIC_DOMAIN_PERSISTENT_VOLUME_CLAIM_NAME)));
  }

  private V1Pod getDesiredBaseServerPodConfigForDefaults(
      String image, String imagePullPolicy, String serverName, int port) {
    String serverNameLC = serverName.toLowerCase();
    return newPod()
        .metadata(
            newObjectMeta()
                .name(DOMAIN_UID + "-" + serverNameLC)
                .namespace(NAMESPACE)
                .putAnnotationsItem("prometheus.io/path", "/wls-exporter/metrics")
                .putAnnotationsItem("prometheus.io/port", "" + port)
                .putAnnotationsItem("prometheus.io/scrape", "true")
                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(DOMAINUID_LABEL, DOMAIN_UID)
                .putLabelsItem(SERVERNAME_LABEL, serverName))
        .spec(
            newPodSpec()
                .addContainersItem(
                    newContainer()
                        .name("weblogic-server")
                        .image(image)
                        .imagePullPolicy(imagePullPolicy)
                        .addCommandItem("/weblogic-operator/scripts/startServer.sh")
                        .addCommandItem(DOMAIN_UID)
                        .addCommandItem(serverName)
                        .addCommandItem(DOMAIN_NAME)
                        .lifecycle(
                            newLifecycle()
                                .preStop(
                                    newHandler()
                                        .exec(
                                            newExecAction()
                                                .addCommandItem(
                                                    "/weblogic-operator/scripts/stopServer.sh")
                                                .addCommandItem(DOMAIN_UID)
                                                .addCommandItem(serverName)
                                                .addCommandItem(DOMAIN_NAME))))
                        .livenessProbe(
                            newProbe()
                                .initialDelaySeconds(10)
                                .periodSeconds(10)
                                .timeoutSeconds(5)
                                .failureThreshold(1)
                                .exec(
                                    newExecAction()
                                        .addCommandItem(
                                            "/weblogic-operator/scripts/livenessProbe.sh")
                                        .addCommandItem(DOMAIN_NAME)
                                        .addCommandItem(serverName)))
                        .readinessProbe(
                            newProbe()
                                .initialDelaySeconds(2)
                                .periodSeconds(10)
                                .timeoutSeconds(5)
                                .failureThreshold(1)
                                .exec(
                                    newExecAction()
                                        .addCommandItem(
                                            "/weblogic-operator/scripts/readinessProbe.sh")
                                        .addCommandItem(DOMAIN_NAME)
                                        .addCommandItem(serverName)))
                        .addPortsItem(newContainerPort().containerPort(port).protocol("TCP"))
                        .addEnvItem(newEnvVar().name("DOMAIN_NAME").value(DOMAIN_NAME))
                        .addEnvItem(
                            newEnvVar().name("DOMAIN_HOME").value("/shared/domain/" + DOMAIN_NAME))
                        .addEnvItem(newEnvVar().name("ADMIN_NAME").value(ADMIN_SERVER_NAME))
                        .addEnvItem(newEnvVar().name("ADMIN_PORT").value("" + ADMIN_SERVER_PORT))
                        .addEnvItem(newEnvVar().name("SERVER_NAME").value(serverName))
                        .addEnvItem(newEnvVar().name("ADMIN_USERNAME").value(null))
                        .addEnvItem(newEnvVar().name("ADMIN_PASSWORD").value(null))
                        .addVolumeMountsItem(
                            newVolumeMount() // TBD - why is the mount created if the volume doesn't
                                // exist?
                                .name("weblogic-domain-storage-volume")
                                .mountPath("/shared"))
                        .addVolumeMountsItem(
                            newVolumeMount()
                                .name("weblogic-credentials-volume")
                                .mountPath("/weblogic-operator/secrets"))
                        .addVolumeMountsItem(
                            newVolumeMount()
                                .name("weblogic-domain-cm-volume")
                                .mountPath("/weblogic-operator/scripts")))
                .addVolumesItem(
                    newVolume()
                        .name("weblogic-credentials-volume")
                        .secret(
                            newSecretVolumeSource().secretName(WEBLOGIC_CREDENTIALS_SECRET_NAME)))
                .addVolumesItem(
                    newVolume()
                        .name("weblogic-domain-cm-volume")
                        .configMap(
                            newConfigMapVolumeSource()
                                .name("weblogic-domain-cm")
                                .defaultMode(365))));
  }
}
