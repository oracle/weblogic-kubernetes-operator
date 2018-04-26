// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static java.util.Arrays.asList;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;

import static oracle.kubernetes.operator.LabelConstants.*;
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

  protected ParsedApacheSecurityYaml getApacheSecurityYaml() {
    return getGeneratedFiles().getApacheSecurityYaml();
  }

  protected ParsedApacheYaml getApacheYaml() {
    return getGeneratedFiles().getApacheYaml();
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
  public void loadBalancerSecurityYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
      getTraefikSecurityYaml().getObjectCount(),
      is(getTraefikSecurityYaml().getExpectedObjectCount()));
  }

  @Test
  public void loadBalancerYaml_hasCorrectNumberOfObjects() throws Exception {
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
          .name(getInputs().getDomainUID() + "-create-weblogic-domain-job")
          .namespace(getInputs().getNamespace()))
        .spec(newJobSpec()
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
              .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
              .putLabelsItem(APP_LABEL, getInputs().getDomainUID() + "-create-weblogic-domain-job"))
            .spec(newPodSpec()
              .restartPolicy("Never")
              .addContainersItem(newContainer()
                .name("create-weblogic-domain-job")
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
                  .name("create-weblogic-domain-job-cm-volume")
                  .mountPath("/u01/weblogic"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("weblogic-domain-storage-volume")
                  .mountPath("/shared"))
                .addVolumeMountsItem(newVolumeMount()
                  .name("weblogic-credentials-volume")
                  .mountPath("/weblogic-operator/secrets")))
              .addVolumesItem(newVolume()
                .name("create-weblogic-domain-job-cm-volume")
                  .configMap(newConfigMapVolumeSource()
                    .name(getInputs().getDomainUID() + "-create-weblogic-domain-job-cm")))
              .addVolumesItem(newVolume()
                .name("weblogic-domain-storage-volume")
                .persistentVolumeClaim(newPersistentVolumeClaimVolumeSource()
                  .claimName(getInputs().getWeblogicDomainPersistentVolumeClaimName())))
              .addVolumesItem(newVolume()
                .name("weblogic-credentials-volume")
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
          .name(getInputs().getDomainUID() + "-create-weblogic-domain-job-cm")
          .namespace(getInputs().getNamespace())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
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
        #!/bin/bash
        #

        # Include common utility functions
        source /u01/weblogic/utility.sh

        export DOMAIN_HOME=${SHARED_PATH}/domain/%DOMAIN_NAME%

        # Create the domain
        wlst.sh -skipWLSModuleScanning /u01/weblogic/create-domain.py

        echo "Successfully Completed"
    */
    assertThat(
      actualCreateDomainScriptSh,
      containsRegexps(
        getInputs().getDomainName(),
        "wlst.sh -skipWLSModuleScanning /u01/weblogic/create-domain.py"));
  }

  protected void assertThatActualCreateDomainPyIsCorrect(String actualCreateDomainPy) {
    /*
      create-domain.py: |-
        server_port        = %MANAGED_SERVER_PORT%
        cluster_name       = "%CLUSTER_NAME%"
        number_of_ms       = %NUMBER_OF_MS%
        cluster_type       = "%CLUSTER_TYPE%"
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
        cmo.setProductionModeEnabled(%PRODUCTION_MODE_ENABLED%)
        asbpFile=open('%s/servers/%ADMIN_SERVER_NAME%/security/boot.properties' % domain_path, 'w+')
          secdir='%s/servers/%MANAGED_SERVER_NAME_BASE%%s/security' % (domain_path, index+1)
        set('ServerNamePrefix', '%MANAGED_SERVER_NAME_BASE%")
    */
    assertThat(
      actualCreateDomainPy,
      containsRegexps(
        getInputs().getDomainName(),
        getInputs().getClusterName(),
        getInputs().getClusterType(),
        getInputs().getAdminServerName(),
        getInputs().getManagedServerNameBase(),
        getInputs().getDomainUID() + "-" + getInputs().getAdminServerName(),
        "setProductionModeEnabled\\(" + getInputs().getProductionModeEnabled() + "\\)",
        "server_port *= " + getInputs().getManagedServerPort(),
        "number_of_ms *= " + getInputs().getConfiguredManagedServerCount(),
        "set\\('ListenPort', " + getInputs().getAdminPort() + "\\)",
        "set\\('PublicPort', " + getInputs().getT3ChannelPort() + "\\)",
        "set\\('PublicAddress', '" + getInputs().getT3PublicAddress() + "'\\)",
        "set\\('ServerNamePrefix', \"" + getInputs().getManagedServerNameBase() + "\"\\)"));
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
            .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
            .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
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
  public void generatesCorrect_loadBalancerServiceAccount() throws Exception {
    assertThat(
      getActualTraefikServiceAccount(),
      yamlEqualTo(getExpectedTraefikServiceAccount()));
  }

  protected V1ServiceAccount getActualApacheServiceAccount() {
    return getApacheYaml().getApacheServiceAccount();
  }

  protected V1ServiceAccount getExpectedApacheServiceAccount() {
    return
      newServiceAccount()
        .metadata(newObjectMeta()
          .name(getApacheName())
          .namespace(getInputs().getNamespace())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(APP_LABEL, getApacheAppName()));
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
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()));
  }

  @Test
  public void generatesCorrect_loadBalancerDeployment() throws Exception {
    assertThat(
      getActualTraefikDeployment(),
      yamlEqualTo(getExpectedTraefikDeployment()));
  }

  protected ExtensionsV1beta1Deployment getActualApacheDeployment() {
    return getApacheYaml().getApacheDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedApacheDeployment() {
    return
      newDeployment()
        .apiVersion(API_VERSION_EXTENSIONS_V1BETA1) // TBD - why is apache using this older version?
        .metadata(newObjectMeta()
          .name(getApacheName())
          .namespace(getInputs().getNamespace())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(APP_LABEL, getApacheAppName()))
        .spec(newDeploymentSpec()
          .replicas(1)
          .selector(newLabelSelector()
            .putMatchLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
            .putMatchLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
            .putMatchLabelsItem(APP_LABEL, getApacheAppName()))
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
              .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
              .putLabelsItem(APP_LABEL, getApacheAppName()))
            .spec(newPodSpec()
              .serviceAccountName(getApacheName())
              .terminationGracePeriodSeconds(60L)
              .addContainersItem(newContainer()
                .name(getApacheName())
                .image("12213-apache:latest")
                .imagePullPolicy("Never")
                .addEnvItem(newEnvVar()
                  .name("WEBLOGIC_CLUSTER")
                  .value(getInputs().getDomainUID() + "-cluster-" + getClusterNameLC() + ":" + getInputs().getManagedServerPort()))
                .addEnvItem(newEnvVar()
                  .name("LOCATION")
                  .value(getInputs().getLoadBalancerAppPrepath()))
                .addEnvItem(newEnvVar()
                  .name("WEBLOGIC_HOST")
                  .value(getInputs().getDomainUID() + "-" + getInputs().getAdminServerName()))
                .addEnvItem(newEnvVar()
                  .name("WEBLOGIC_PORT")
                  .value(getInputs().getAdminPort()))
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
              )
            )
          )
        );
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
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(newDeploymentSpec()
          .replicas(1)
          .selector(newLabelSelector()
            .putMatchLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
            .putMatchLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
          .template(newPodTemplateSpec()
            .metadata(newObjectMeta()
              .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
              .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
              .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
            .spec(newPodSpec()
              .serviceAccountName(getTraefikScope())
              .terminationGracePeriodSeconds(60L)
              .addContainersItem(newContainer()
                .name("traefik")
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
                    .name(getTraefikScope() + "-cm"))))));
  }

  @Test
  public void generatesCorrect_loadBalancerConfigMap() throws Exception {
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
          .name(getTraefikScope() + "-cm")
          .namespace(getInputs().getNamespace())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .putDataItem(PROPERTY_TRAEFIK_TOML, "");
  }

  protected void assertThatActualTraefikTomlIsCorrect(String actualTraefikToml) {
    assertThat(
      actualTraefikToml,
      containsString("labelselector = \"" + DOMAINUID_LABEL + "=" + getInputs().getDomainUID() + "," + CLUSTERNAME_LABEL + "=" + getInputs().getClusterName() + "\""));
  }

  @Test
  public void generatesCorrect_loadBalancerService() throws Exception {
    assertThat(
      getActualTraefikService(),
      yamlEqualTo(getExpectedTraefikService()));
  }

  protected V1Service getActualApacheService() {
    return getApacheYaml().getApacheService();
  }

  protected V1Service getExpectedApacheService() {
    return
      newService()
        .metadata(newObjectMeta()
          .name(getApacheName())
          .namespace(getInputs().getNamespace())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .spec(newServiceSpec()
          .type("NodePort")
          .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putSelectorItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putSelectorItem(APP_LABEL, getApacheAppName())
          .addPortsItem(newServicePort()
            .name("rest-https")
            .port(80)
            .nodePort(Integer.parseInt(getInputs().getLoadBalancerWebPort()))));
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
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(newServiceSpec()
          .type("NodePort")
          .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putSelectorItem(CLUSTERNAME_LABEL, getInputs().getClusterName())
          .addPortsItem(newServicePort()
            .name("http")
            .targetPort(newIntOrString("http"))
            .port(80)
            .nodePort(Integer.parseInt(getInputs().getLoadBalancerWebPort()))));
  }

  @Test
  public void generatesCorrect_loadBalancerDashboardService() throws Exception {
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
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(newServiceSpec()
          .type("NodePort")
          .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putSelectorItem(CLUSTERNAME_LABEL, getInputs().getClusterName())
          .addPortsItem(newServicePort()
            .name("dash")
            .targetPort(newIntOrString("dash"))
            .port(8080)
            .nodePort(Integer.parseInt(getInputs().getLoadBalancerDashboardPort()))));
  }

  @Test
  public void generatesCorrect_loadBalancerClusterRole() throws Exception {
    assertThat(
      getActualTraefikClusterRole(),
      yamlEqualTo(getExpectedTraefikClusterRole()));
  }

  protected V1beta1ClusterRole getActualApacheClusterRole() {
    return getApacheSecurityYaml().getApacheClusterRole();
  }

  protected V1beta1ClusterRole getExpectedApacheClusterRole() {
    return
      newClusterRole()
        .metadata(newObjectMeta()
          .name(getApacheName())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .addRulesItem(newPolicyRule()
          .addApiGroupsItem("")
          .resources(asList("pods", "services", "endpoints", "secrets"))
          .verbs(asList("get", "list", "watch")))
        .addRulesItem(newPolicyRule()
          .addApiGroupsItem("extensions")
          .addResourcesItem("ingresses")
          .verbs(asList("get", "list", "watch")));
  }

  protected V1beta1ClusterRole getActualTraefikClusterRole() {
    return getTraefikSecurityYaml().getTraefikClusterRole();
  }

  protected V1beta1ClusterRole getExpectedTraefikClusterRole() {
    return
      newClusterRole()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
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
  public void generatesCorrect_loadBalancerClusterRoleBinding() throws Exception {
    assertThat(
      getActualTraefikDashboardClusterRoleBinding(),
      yamlEqualTo(getExpectedTraefikDashboardClusterRoleBinding()));
  }

  protected V1beta1ClusterRoleBinding getActualApacheClusterRoleBinding() {
    return getApacheSecurityYaml().getApacheClusterRoleBinding();
  }

  protected V1beta1ClusterRoleBinding getExpectedApacheClusterRoleBinding() {
    return
      newClusterRoleBinding()
        .metadata(newObjectMeta()
          .name(getApacheName())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .addSubjectsItem(newSubject()
          .kind("ServiceAccount")
          .name(getApacheName())
          .namespace(getInputs().getNamespace()))
        .roleRef(newRoleRef()
          .name(getApacheName())
          .apiGroup("rbac.authorization.k8s.io"));
  }

  protected V1beta1ClusterRoleBinding getActualTraefikDashboardClusterRoleBinding() {
    return getTraefikSecurityYaml().getTraefikDashboardClusterRoleBinding();
  }

  protected V1beta1ClusterRoleBinding getExpectedTraefikDashboardClusterRoleBinding() {
    return
      newClusterRoleBinding()
        .metadata(newObjectMeta()
          .name(getTraefikScope())
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
          .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
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
            .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
            .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
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
          .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
          .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
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

  protected String getApacheName() { return getInputs().getDomainUID() + "-" + getApacheAppName(); }

  protected String getApacheAppName() { return "apache-webtier";}
}
