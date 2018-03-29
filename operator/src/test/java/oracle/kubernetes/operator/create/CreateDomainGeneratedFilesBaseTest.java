// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static java.util.Arrays.asList;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import static oracle.kubernetes.operator.create.CreateDomainInputs.readInputsYamlFile;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.yamlEqualTo;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh
 * creates are correct when the admin node port is disabled, the t3 channel is disabled,
 * there is no weblogic domain image pull secret, and production mode is disabled.
 */
public abstract class CreateDomainGeneratedFilesBaseTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  private static final String PROPERTY_TRAEFIK_TOML = "traefik.toml";
  private static final String PROPERTY_UTILITY_SH = "utility.sh";
  private static final String PROPERTY_CREATE_DOMAIN_JOB_SH = "create-domain-job.sh";
  private static final String PROPERTY_READ_DOMAIN_SECRET_PY = "read-domain-secret.py";
  private static final String PROPERTY_CREATE_DOMAIN_SCRIPT_SH = "create-domain-script.sh";
  private static final String PROPERTY_CREATE_DOMAIN_PY = "create-domain.py";

  protected static CreateDomainInputs getInputs() {
    return inputs;
  }

  protected static GeneratedDomainYamlFiles getGeneratedFiles() {
    return generatedFiles;
  }

  protected ParsedCreateWeblogicDomainJobYaml getCreateWeblogicDomainJobYaml() {
    return getGeneratedFiles().getCreateWeblogicDomainJobYaml();
  }

  protected ParsedDomainCustomResourceYaml getDomainCustomResourceYaml() {
    return getGeneratedFiles().getDomainCustomResourceYaml();
  }

  protected ParsedTraefikSecurityYaml getTraefikSecurityYaml() {
    return getGeneratedFiles().getTraefikSecurityYaml();
  }

  protected ParsedTraefikYaml getTraefikYaml() {
    return getGeneratedFiles().getTraefikYaml();
  }

  protected ParsedWeblogicDomainPersistentVolumeClaimYaml getWeblogicDomainPersistentVolumeClaimYaml() {
    return getGeneratedFiles().getWeblogicDomainPersistentVolumeClaimYaml();
  }

  protected ParsedWeblogicDomainPersistentVolumeYaml getWeblogicDomainPersistentVolumeYaml() {
    return getGeneratedFiles().getWeblogicDomainPersistentVolumeYaml();
  }

  protected static void setup(CreateDomainInputs val) throws Exception {
    inputs = val;
    generatedFiles = GeneratedDomainYamlFiles.generateDomainYamlFiles(getInputs());
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatedCorrect_weblogicDomainInputsYaml() throws Exception {
    assertThat(
      readInputsYamlFile(generatedFiles.getDomainFiles().getCreateWeblogicDomainInputsYamlPath()),
      yamlEqualTo(readInputsYamlFile(generatedFiles.getInputsYamlPath())));
  }

  @Test
  public void createWeblogicDomainJobYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getCreateWeblogicDomainJobYaml().getObjectCount(),
      is(getCreateWeblogicDomainJobYaml().getExpectedObjectCount()));
  }

  @Test
  public void domainCustomResourceYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getDomainCustomResourceYaml().getObjectCount(),
      is(getDomainCustomResourceYaml().getExpectedObjectCount()));
  }

  @Test
  public void traefikSecurityYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getTraefikSecurityYaml().getObjectCount(),
      is(getTraefikSecurityYaml().getExpectedObjectCount()));
  }

  @Test
  public void traefikYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getTraefikYaml().getObjectCount(),
      is(getTraefikYaml().getExpectedObjectCount()));
  }

  @Test
  public void weblogicDomainPersistentVolumeClaimYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getWeblogicDomainPersistentVolumeClaimYaml().getObjectCount(),
      is(getWeblogicDomainPersistentVolumeClaimYaml().getExpectedObjectCount()));
  }

  @Test
  public void weblogicDomainPersistentVolumeYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getWeblogicDomainPersistentVolumeYaml().getObjectCount(),
      is(getWeblogicDomainPersistentVolumeYaml().getExpectedObjectCount()));
  }

  @Test
  public void generatesCorrect_createWeblogicDomainJob() throws Exception {
    assertThat(
      getActualCreateWeblogicDomainJob(),
      yamlEqualTo(getExpectedCreateWeblogicDomainJob()));
  }

  protected V1Job getActualCreateWeblogicDomainJob() {
    return getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob();
  }

  protected V1Job getExpectedCreateWeblogicDomainJob() {
    return
      newJob()
        .metadata(newObjectMeta()
          .name("domain-" + getInputs().getDomainUID() + "-job")
          .namespace(getInputs().getNamespace()))
        .spec(newJobSpec()
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem("app", "domain-" + getInputs().getDomainUID() + "-job")
              .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
            .spec(newPodSpec()
              .restartPolicy("Never")
              .addContainersItem(newContainer()
                .name("domain-job")
                .image("store/oracle/weblogic:12.2.1.3")
                .imagePullPolicy("IfNotPresent")
                .addCommandItem("/bin/sh")
                .addArgsItem("/u01/weblogic/create-domain-job.sh")
                .addEnvItem(newEnvVar()
                  .name("SHARED_PATH")
                  .value("/shared"))
                .addPortsItem(newContainerPort()
                  .containerPort(7001))
                .addVolumeMountsItem(newVolumeMount()
                  .name("config-map-scripts")
                  .mountPath("/u01/weblogic"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("pv-storage")
                  .mountPath("/shared"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("secrets")
                  .mountPath("/weblogic-operator/secrets")))
              .addVolumesItem(newVolume()
                .name("config-map-scripts")
                  .configMap(newConfigMapVolumeSource()
                    .name("domain-" + getInputs().getDomainUID() + "-scripts")))
              .addVolumesItem(newVolume()
                .name("pv-storage")
                .persistentVolumeClaim(newPersistentVolumeClaimVolumeSource()
                  .claimName(getInputs().getWeblogicDomainPersistentVolumeClaimName())))
              .addVolumesItem(newVolume()
                .name("secrets")
                  .secret(newSecretVolumeSource()
                    .secretName(getInputs().getWeblogicCredentialsSecretName()))))));
  }

  @Test
  public void generatesCorrect_createWeblogicDomainConfigMap() throws Exception {
    // The config map contains several properties that contain shell and wlst scripts
    // that we don't want to duplicate in the test.  However, part of their text
    // is computed from the inputs, so we want to validate that part of the info.
    // First, if it's present, extract these values from the config map
    // then empty them out.
    // Second, make sure the rest of the config map is as expected.
    // Third, make sure that these values contains the expected
    // expansion of the input properties.
    V1ConfigMap actual = getActualCreateWeblogicDomainJobConfigMap();

    // TBD - should 'expected' be a clone since we're going to modify it?
    // Since all the other test don't use getActualCreateWeblogicDomainJobConfigMap(),
    // it's currently safe to not clone it.
    String actualUtilitySh = getThenEmptyConfigMapDataValue(actual, PROPERTY_UTILITY_SH);
    String actualCreateDomainJobSh = getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_JOB_SH);
    String actualReadDomainSecretPy = getThenEmptyConfigMapDataValue(actual, PROPERTY_READ_DOMAIN_SECRET_PY);
    String actualCreateDomainScriptSh = getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_SCRIPT_SH);
    String actualCreateDomainPy = getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_PY);

    // don't allOf since we want to make sure the later assertThats are only called if the first one passes
    assertThat(actual, yamlEqualTo(getExpectedCreateWeblogicDomainJobConfigMap()));

    assertThatActualUtilityShIsCorrect(actualUtilitySh);
    assertThatActualCreateDomainJobShIsCorrect(actualCreateDomainJobSh);
    assertThatActualReadDomainSecretPyIsCorrect(actualReadDomainSecretPy);
    assertThatActualCreateDomainScriptShIsCorrect(actualCreateDomainScriptSh);
    assertThatActualCreateDomainPyIsCorrect(actualCreateDomainPy);
  }

  protected V1ConfigMap getActualCreateWeblogicDomainJobConfigMap() {
    return getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainConfigMap();
  }

  protected V1ConfigMap getExpectedCreateWeblogicDomainJobConfigMap() {
    return
      newConfigMap()
        .metadata(newObjectMeta()
          .name("domain-" + getInputs().getDomainUID() + "-scripts")
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
        .putDataItem(PROPERTY_UTILITY_SH, "")
        .putDataItem(PROPERTY_CREATE_DOMAIN_JOB_SH, "")
        .putDataItem(PROPERTY_READ_DOMAIN_SECRET_PY, "")
        .putDataItem(PROPERTY_CREATE_DOMAIN_SCRIPT_SH, "")
        .putDataItem(PROPERTY_CREATE_DOMAIN_PY, "");
  }

  protected void assertThatActualUtilityShIsCorrect(String actualUtilitySh) {
    // utility.sh doesn't depend on the inputs so don't bother checking its contents
  }

  protected void assertThatActualCreateDomainJobShIsCorrect(String actualCreateDomainJobSh) {
    /*
      create-domain-job.sh: |-
        script='%CREATE_DOMAIN_SCRIPT%'
        domainFolder=${SHARED_PATH}/domain/%DOMAIN_NAME%
    */
    assertThat(
      actualCreateDomainJobSh,
      containsRegexps(
        getInputs().getDomainName()));
  }

  protected void assertThatActualReadDomainSecretPyIsCorrect(String actualReadDomainSecretPy) {
    // read-domain-secret.py doesn't depend on the inputs so don't bother checking its contents
  }

  protected void assertThatActualCreateDomainScriptShIsCorrect(String actualCreateDomainScriptSh) {
    /*
      create-domain-script.sh: |-
        export DOMAIN_HOME=${SHARED_PATH}/domain/%DOMAIN_NAME%
            echo "AdminURL=http\://$3\:%ADMIN_PORT%" >> ${startProp}
        nmConnect(admin_username, admin_password, '$1-$2',  '5556', '%DOMAIN_NAME%', '${DOMAIN_HOME}', 'plain')
          nmConnect(admin_username, admin_password, '$1-$2',  '5556', '%DOMAIN_NAME%', '${DOMAIN_HOME}', 'plain')
        createNodeMgrHome %DOMAIN_UID% %ADMIN_SERVER_NAME%
        createStartScript %DOMAIN_UID% %ADMIN_SERVER_NAME%
        createStopScript  %DOMAIN_UID% %ADMIN_SERVER_NAME%
        while [ $index -lt %NUMBER_OF_MS% ]
          createNodeMgrHome %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index} %DOMAIN_UID%-%ADMIN_SERVER_NAME%
          createStartScript %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index}
          createStopScript  %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index}
    */
    assertThat(
      actualCreateDomainScriptSh,
      containsRegexps(
        getInputs().getDomainUID(),
        getInputs().getDomainName(),
        getInputs().getAdminServerName(),
        getInputs().getManagedServerNameBase(),
        getInputs().getDomainUID() + "-" + getInputs().getAdminServerName(),
        "index -lt " + getInputs().getConfiguredManagedServerCount()));
  }

  protected void assertThatActualCreateDomainPyIsCorrect(String actualCreateDomainPy) {
    /*
      create-domain.py: |-
        server_port        = %MANAGED_SERVER_PORT%
        cluster_name       = "%CLUSTER_NAME%"
        number_of_ms       = %NUMBER_OF_MS%
        print('domain_name        : [%DOMAIN_NAME%]');
        print('admin_port         : [%ADMIN_PORT%]');
        set('Name', '%DOMAIN_NAME%')
        setOption('DomainName', '%DOMAIN_NAME%')
        create('%DOMAIN_NAME%','Log')
        cd('/Log/%DOMAIN_NAME%');
        set('FileName', '/shared/logs/%DOMAIN_NAME%.log')
        set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME%')
        set('ListenPort', %ADMIN_PORT%)
        set('Name', '%ADMIN_SERVER_NAME%')
        cd('/Servers/%ADMIN_SERVER_NAME%/NetworkAccessPoints/T3Channel')
        set('PublicPort', %T3_CHANNEL_PORT%)
        set('PublicAddress', '%T3_PUBLIC_ADDRESS%')
        set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME%')
        set('ListenPort', %T3_CHANNEL_PORT%)
        cd('/Servers/%ADMIN_SERVER_NAME%')
        create('%ADMIN_SERVER_NAME%', 'Log')
        cd('/Servers/%ADMIN_SERVER_NAME%/Log/%ADMIN_SERVER_NAME%')
        set('FileName', '/shared/logs/%ADMIN_SERVER_NAME%.log')
        cd('/Security/%DOMAIN_NAME%/User/weblogic')
          machineName = '%DOMAIN_UID%-machine%s' % msIndex
          set('ListenAddress', '%DOMAIN_UID%-%MANAGED_SERVER_NAME_BASE%%s' % msIndex)
          name = '%MANAGED_SERVER_NAME_BASE%%s' % msIndex
          machineName = '%DOMAIN_UID%-machine%s' % msIndex
          set('ListenAddress', '%DOMAIN_UID%-%s' % name)
        cmo.setProductionModeEnabled(%PRODUCTION_MODE_ENABLED%)
        asbpFile=open('%s/servers/%ADMIN_SERVER_NAME%/security/boot.properties' % domain_path, 'w+')
          secdir='%s/servers/%MANAGED_SERVER_NAME_BASE%%s/security' % (domain_path, index+1)
    */
    assertThat(
      actualCreateDomainPy,
      containsRegexps(
        getInputs().getDomainName(),
        getInputs().getClusterName(),
        getInputs().getAdminServerName(),
        getInputs().getManagedServerNameBase(),
        getInputs().getDomainUID() + "-" + getInputs().getAdminServerName(),
        getInputs().getDomainUID() + "-" + getInputs().getManagedServerNameBase(),
        "setProductionModeEnabled\\(" + getInputs().getProductionModeEnabled() + "\\)",
        "server_port *= " + getInputs().getManagedServerPort(),
        "number_of_ms *= " + getInputs().getConfiguredManagedServerCount(),
        "set\\('ListenPort', " + getInputs().getAdminPort() + "\\)",
        "set\\('PublicPort', " + getInputs().getT3ChannelPort() + "\\)",
        "set\\('PublicAddress', '" + getInputs().getT3PublicAddress() + "'\\)"));
     // TBD should we check anything else?
  }

  @Test
  public void generatesCorrect_domain() throws Exception {
    assertThat(getActualDomain(), yamlEqualTo(getExpectedDomain()));
  }

  protected Domain getActualDomain() {
    return getDomainCustomResourceYaml().getDomain();
  }

  protected Domain getExpectedDomain() {
    return
      newDomain()
        .withMetadata(
          newObjectMeta()
            .name(getInputs().getDomainUID())
            .namespace(getInputs().getNamespace())
            .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
        .withSpec(newDomainSpec()
          .withDomainUID(getInputs().getDomainUID())
          .withDomainName(getInputs().getDomainName())
          .withImage("store/oracle/weblogic:12.2.1.3")
          .withImagePullPolicy("IfNotPresent")
          .withAdminSecret(newSecretReference()
            .name(getInputs().getWeblogicCredentialsSecretName()))
          .withAsName(getInputs().getAdminServerName())
          .withAsPort(Integer.parseInt(getInputs().getAdminPort()))
          .withStartupControl(getInputs().getStartupControl())
          .withServerStartup(newServerStartupList()
            .addElement(newServerStartup()
          .withDesiredState("RUNNING")
          .withServerName(getInputs().getAdminServerName())
          .withEnv(newEnvVarList()
            .addElement(newEnvVar()
              .name("JAVA_OPTIONS")
              .value(getInputs().getJavaOptions()))
            .addElement(newEnvVar()
              .name("USER_MEM_ARGS")
              .value("-Xms64m -Xmx256m ")))))
          .withClusterStartup(newClusterStartupList()
            .addElement(newClusterStartup()
              .withDesiredState("RUNNING")
              .withClusterName(getInputs().getClusterName())
              .withReplicas(Integer.parseInt(getInputs().getInitialManagedServerReplicas()))
              .withEnv(newEnvVarList()
                .addElement(newEnvVar()
                  .name("JAVA_OPTIONS")
                  .value(getInputs().getJavaOptions()))
                .addElement(newEnvVar()
                  .name("USER_MEM_ARGS")
                  .value("-Xms64m -Xmx256m "))))));
  }

  @Test
  public void generatesCorrect_traefikServiceAccount() throws Exception {
    assertThat(
      getActualTraefikServiceAccount(),
      yamlEqualTo(getExpectedTraefikServiceAccount()));
  }

  protected V1ServiceAccount getActualTraefikServiceAccount() {
    return getTraefikYaml().getTraefikServiceAccount();
  }

  protected V1ServiceAccount getExpectedTraefikServiceAccount() {
    return
      newServiceAccount()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC()));
  }

  @Test
  public void generatesCorrect_traefikDeployment() throws Exception {
    assertThat(
      getActualTraefikDeployment(),
      yamlEqualTo(getExpectedTraefikDeployment()));
  }

  protected ExtensionsV1beta1Deployment getActualTraefikDeployment() {
    return getTraefikYaml().getTraefikDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedTraefikDeployment() {
    return
      newDeployment()
        .apiVersion(API_VERSION_EXTENSIONS_V1BETA1) // TBD - why is traefik using this older version?
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .namespace(getInputs().getNamespace())
          .putLabelsItem("app", getTraefikScope())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
        .spec(newDeploymentSpec()
          .replicas(1)
          .selector(newLabelSelector()
            .putMatchLabelsItem("app", getTraefikScope()))
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem("app", getTraefikScope())
              .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
              .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
            .spec(newPodSpec()
              .serviceAccountName(getTraefikScope())
              .terminationGracePeriodSeconds(60L)
              .addContainersItem(newContainer()
                .name(getTraefikScope())
                .image("traefik:1.4.5")
                .resources(newResourceRequirements()
                  .putRequestsItem("cpu", Quantity.fromString("100m"))
                  .putRequestsItem("memory", Quantity.fromString("20Mi"))
                  .putLimitsItem("cpu", Quantity.fromString("100m"))
                  .putLimitsItem("memory", Quantity.fromString("30Mi")))
                .readinessProbe(newProbe()
                  .tcpSocket(newTCPSocketAction()
                    .port(newIntOrString(80)))
                  .failureThreshold(1)
                  .initialDelaySeconds(10)
                  .periodSeconds(10)
                  .successThreshold(1)
                  .timeoutSeconds(2))
                .livenessProbe(newProbe()
                  .tcpSocket(newTCPSocketAction()
                    .port(newIntOrString(80)))
                  .failureThreshold(3)
                  .initialDelaySeconds(10)
                  .periodSeconds(10)
                  .successThreshold(1)
                  .timeoutSeconds(2))
                .addVolumeMountsItem(newVolumeMount()
                  .name("config")
                  .mountPath("/config"))
                .addPortsItem(newContainerPort()
                  .name("http")
                  .containerPort(80)
                  .protocol("TCP"))
                .addPortsItem(newContainerPort()
                  .name("dash")
                  .containerPort(8080)
                  .protocol("TCP"))
                .addArgsItem("--configfile=/config/traefik.toml"))
              .addVolumesItem(newVolume()
                .name("config")
                  .configMap(newConfigMapVolumeSource()
                    .name(getTraefikScope()))))));
  }

  @Test
  public void generatesCorrect_traefikConfigMap() throws Exception {
    // The config map contains a 'traefik.toml' property that has a lot of text
    // that we don't want to duplicate in the test.  However, part of the text
    // is computed from the inputs, so we want to validate that part of the info.
    // First, if it's present, extract the trafik.toml value from the config map
    // then empty it out.
    // Second, make sure the rest of the config map is as expected.
    // Third, make sure that the traefik.tom value contains the expected
    // expansion of the input properties.
    V1ConfigMap actual = getActualTraefikConfigMap();
    String actualTraefikToml = getThenEmptyConfigMapDataValue(actual, PROPERTY_TRAEFIK_TOML);
    // don't allOf since we want to make sure the second assertThat is only called if the first one passes
    assertThat(actual, yamlEqualTo(getExpectedTraefikConfigMap()));
    assertThatActualTraefikTomlIsCorrect(actualTraefikToml);
  }

  protected V1ConfigMap getActualTraefikConfigMap() {
    return getTraefikYaml().getTraefikConfigMap();
  }

  protected V1ConfigMap getExpectedTraefikConfigMap() {
    return
      newConfigMap()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC())
          .putLabelsItem("app", getTraefikScope()))
        .putDataItem(PROPERTY_TRAEFIK_TOML, "");
  }

  protected void assertThatActualTraefikTomlIsCorrect(String actualTraefikToml) {
    assertThat(
      actualTraefikToml,
      containsString("labelselector = \"weblogic.domainUID=" + getInputs().getDomainUID() + ",weblogic.clusterName=" + getInputs().getClusterName() + "\""));
  }

  @Test
  public void generatesCorrect_traefikService() throws Exception {
    assertThat(
      getActualTraefikService(),
      yamlEqualTo(getExpectedTraefikService()));
  }

  protected V1Service getActualTraefikService() {
    return getTraefikYaml().getTraefikService();
  }

  protected V1Service getExpectedTraefikService() {
    return
      newService()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
        .spec(newServiceSpec()
          .type("NodePort")
          .putSelectorItem("app", getTraefikScope())
          .addPortsItem(newServicePort()
            .name("http")
            .targetPort(newIntOrString("http"))
            .port(80)
            .nodePort(Integer.parseInt(getInputs().getLoadBalancerWebPort()))));
  }

  @Test
  public void generatesCorrect_traefikDashboardService() throws Exception {
    assertThat(
      getActualTraefikDashboardService(),
      yamlEqualTo(getExpectedTraefikDashboardService()));
  }

  protected V1Service getActualTraefikDashboardService() {
    return getTraefikYaml().getTraefikDashboardService();
  }

  protected V1Service getExpectedTraefikDashboardService() {
    return
      newService()
        .metadata(newObjectMeta()
          .name(getTraefikScope() + "-dashboard")
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC())
          .putLabelsItem("app", getTraefikScope()))
        .spec(newServiceSpec()
          .type("NodePort")
          .putSelectorItem("app", getTraefikScope())
          .addPortsItem(newServicePort()
            .name("dash")
            .targetPort(newIntOrString("dash"))
            .port(8080)
            .nodePort(Integer.parseInt(getInputs().getLoadBalancerDashboardPort()))));
  }

  @Test
  public void generatesCorrect_traefikClusterRole() throws Exception {
    assertThat(
      getActualTraefikClusterRole(),
      yamlEqualTo(getExpectedTraefikClusterRole()));
  }

  protected V1beta1ClusterRole getActualTraefikClusterRole() {
    return getTraefikSecurityYaml().getTraefikClusterRole();
  }

  protected V1beta1ClusterRole getExpectedTraefikClusterRole() {
    return
      newClusterRole()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
        .addRulesItem(newPolicyRule()
          .addApiGroupsItem("")
          .resources(asList("pods", "services", "endpoints", "secrets"))
          .verbs(asList("get", "list", "watch")))
        .addRulesItem(newPolicyRule()
          .addApiGroupsItem("extensions")
          .addResourcesItem("ingresses")
          .verbs(asList("get", "list", "watch")));
  }

  @Test
  public void generatesCorrect_traefikDashboardClusterRoleBinding() throws Exception {
    assertThat(
      getActualTraefikDashboardClusterRoleBinding(),
      yamlEqualTo(getExpectedTraefikDashboardClusterRoleBinding()));
  }

  protected V1beta1ClusterRoleBinding getActualTraefikDashboardClusterRoleBinding() {
    return getTraefikSecurityYaml().getTraefikDashboardClusterRoleBinding();
  }

  protected V1beta1ClusterRoleBinding getExpectedTraefikDashboardClusterRoleBinding() {
    return
      newClusterRoleBinding()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID())
          .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
        .addSubjectsItem(newSubject()
          .kind("ServiceAccount")
          .name(getTraefikScope())
          .namespace(getInputs().getNamespace()))
        .roleRef(newRoleRef()
          .name(getTraefikScope())
          .apiGroup("rbac.authorization.k8s.io"));
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolume() throws Exception {
    assertThat(
      getActualWeblogicDomainPersistentVolume(),
      yamlEqualTo(getExpectedWeblogicDomainPersistentVolume()));
  }

  protected V1PersistentVolume getActualWeblogicDomainPersistentVolume() {
    return getWeblogicDomainPersistentVolumeYaml().getWeblogicDomainPersistentVolume();
  }

  protected V1PersistentVolume getExpectedWeblogicDomainPersistentVolume() {
    return
        newPersistentVolume()
          .metadata(newObjectMeta()
            .name(getInputs().getWeblogicDomainPersistentVolumeName())
            .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
          .spec(newPersistentVolumeSpec()
            .storageClassName(getInputs().getWeblogicDomainStorageClass())
            .putCapacityItem("storage", Quantity.fromString(getInputs().getWeblogicDomainStorageSize()))
            .addAccessModesItem("ReadWriteMany")
            .persistentVolumeReclaimPolicy("Retain"));
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolumeClaim() throws Exception {
    assertThat(
      getActualWeblogicDomainPersistentVolume(),
      yamlEqualTo(getExpectedWeblogicDomainPersistentVolume()));
  }

  protected V1PersistentVolumeClaim getActualWeblogicDomainPersistentVolumeClaim() {
    return getWeblogicDomainPersistentVolumeClaimYaml().getWeblogicDomainPersistentVolumeClaim();
  }

  protected V1PersistentVolumeClaim getExpectedWeblogicDomainPersistentVolumeClaim() {
    return
      newPersistentVolumeClaim()
        .metadata(newObjectMeta()
          .name(getInputs().getWeblogicDomainPersistentVolumeClaimName())
          .namespace(getInputs().getNamespace())
          .putLabelsItem("weblogic.domainUID", getInputs().getDomainUID()))
        .spec(newPersistentVolumeClaimSpec()
          .storageClassName(getInputs().getWeblogicDomainStorageClass())
          .addAccessModesItem("ReadWriteMany")
          .resources(newResourceRequirements()
            .putRequestsItem("storage", Quantity.fromString(getInputs().getWeblogicDomainStorageSize()))));
  }

  protected String getTraefikScope() {
    return getClusterLCScope() + "-traefik";
  }

  protected String getClusterLCScope() {
    return getInputs().getDomainUID() + "-" + getClusterNameLC();
  }

  protected String getClusterNameLC() {
    return getInputs().getClusterName().toLowerCase();
  }
}
