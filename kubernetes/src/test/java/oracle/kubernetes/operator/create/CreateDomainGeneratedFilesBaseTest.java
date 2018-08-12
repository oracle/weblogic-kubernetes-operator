// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.LabelConstants.APP_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.APACHE_LOAD_BALANCER_V1;
import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V1;
import static oracle.kubernetes.operator.VersionConstants.TRAEFIK_LOAD_BALANCER_V1;
import static oracle.kubernetes.operator.VersionConstants.VOYAGER_LOAD_BALANCER_V1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_GROUP_RBAC;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_APPS_V1BETA1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_EXTENSIONS_V1BETA1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_RBAC_V1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_CLUSTER_ROLE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_SERVICE_ACCOUNT;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.containsRegexps;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.getThenEmptyConfigMapDataValue;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRole;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRoleBinding;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterStartup;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterStartupList;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newConfigMap;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newConfigMapVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newContainer;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newContainerPort;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDeployment;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDeploymentSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDomain;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDomainSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVar;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVarList;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHTTPGetAction;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHostPathVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newIntOrString;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newJob;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newJobSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newLabelSelector;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newLocalObjectReferenceList;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newObjectMeta;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPersistentVolume;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPersistentVolumeClaim;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPersistentVolumeClaimSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPersistentVolumeClaimVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPersistentVolumeSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPodSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPodTemplateSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPolicyRule;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newProbe;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newResourceRequirements;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRoleRef;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSecretReference;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSecretVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServerStartup;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServerStartupList;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newService;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceAccount;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServicePort;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSubject;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newTCPSocketAction;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newToleration;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolume;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolumeMount;
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import oracle.kubernetes.operator.utils.DomainValues;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import oracle.kubernetes.operator.utils.GeneratedDomainYamlFiles;
import oracle.kubernetes.operator.utils.ParsedApacheSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedApacheYaml;
import oracle.kubernetes.operator.utils.ParsedCreateWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDeleteWeblogicDomainJobYaml;
import oracle.kubernetes.operator.utils.ParsedDomainCustomResourceYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedTraefikYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerIngressYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorSecurityYaml;
import oracle.kubernetes.operator.utils.ParsedVoyagerOperatorYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeClaimYaml;
import oracle.kubernetes.operator.utils.ParsedWeblogicDomainPersistentVolumeYaml;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the admin node port is disabled, the t3 channel is disabled, there is no weblogic domain
 * image pull secret, and production mode is disabled.
 */
public abstract class CreateDomainGeneratedFilesBaseTest {

  private static DomainValues inputs;
  private static GeneratedDomainYamlFiles generatedFiles;
  private static DomainYamlFactory factory;

  private static final String PROPERTY_TRAEFIK_TOML = "traefik.toml";
  private static final String PROPERTY_UTILITY_SH = "utility.sh";
  private static final String PROPERTY_CREATE_DOMAIN_JOB_SH = "create-domain-job.sh";
  private static final String PROPERTY_READ_DOMAIN_SECRET_PY = "read-domain-secret.py";
  private static final String PROPERTY_CREATE_DOMAIN_SCRIPT_SH = "create-domain-script.sh";
  private static final String PROPERTY_CREATE_DOMAIN_PY = "create-domain.py";

  protected static DomainValues getInputs() {
    return inputs;
  }

  private static GeneratedDomainYamlFiles getGeneratedFiles() {
    return generatedFiles;
  }

  private ParsedCreateWeblogicDomainJobYaml getCreateWeblogicDomainJobYaml() {
    return getGeneratedFiles().getCreateWeblogicDomainJobYaml();
  }

  private ParsedDeleteWeblogicDomainJobYaml getDeleteWeblogicDomainJobYaml() {
    return getGeneratedFiles().getDeleteWeblogicDomainJobYaml();
  }

  private ParsedDomainCustomResourceYaml getDomainCustomResourceYaml() {
    return getGeneratedFiles().getDomainCustomResourceYaml();
  }

  private ParsedTraefikSecurityYaml getTraefikSecurityYaml() {
    return getGeneratedFiles().getTraefikSecurityYaml();
  }

  private ParsedTraefikYaml getTraefikYaml() {
    return getGeneratedFiles().getTraefikYaml();
  }

  protected ParsedApacheSecurityYaml getApacheSecurityYaml() {
    return getGeneratedFiles().getApacheSecurityYaml();
  }

  protected ParsedApacheYaml getApacheYaml() {
    return getGeneratedFiles().getApacheYaml();
  }

  protected ParsedVoyagerIngressYaml getVoyagerIngressYaml() {
    return getGeneratedFiles().getVoyagerIngressYaml();
  }

  protected ParsedVoyagerOperatorSecurityYaml getVoyagerOperatorSecurityYaml() {
    return getGeneratedFiles().getVoyagerOperatorSecurityYaml();
  }

  protected ParsedVoyagerOperatorYaml getVoyagerOperatorYaml() {
    return getGeneratedFiles().getVoyagerOperatorYaml();
  }

  protected ParsedWeblogicDomainPersistentVolumeClaimYaml
      getWeblogicDomainPersistentVolumeClaimYaml() {
    return getGeneratedFiles().getWeblogicDomainPersistentVolumeClaimYaml();
  }

  protected ParsedWeblogicDomainPersistentVolumeYaml getWeblogicDomainPersistentVolumeYaml() {
    return getGeneratedFiles().getWeblogicDomainPersistentVolumeYaml();
  }

  protected static void setup(DomainYamlFactory factory, DomainValues values) throws Exception {
    CreateDomainGeneratedFilesBaseTest.factory = factory;
    inputs = values;
    generatedFiles = factory.generate(values);
  }

  @Test
  public void generatesCorrect_createWeblogicDomainJob() {
    assertThat(
        getActualCreateWeblogicDomainJob(), yamlEqualTo(getExpectedCreateWeblogicDomainJob()));
  }

  protected V1Job getActualCreateWeblogicDomainJob() {
    return getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob();
  }

  protected V1Job getActualDeleteWeblogicDomainJob() {
    return getDeleteWeblogicDomainJobYaml().getDeleteWeblogicDomainJob();
  }

  protected V1Job getExpectedCreateWeblogicDomainJob() {
    return newJob()
        .metadata(
            newObjectMeta()
                .name(getInputs().getDomainUID() + "-create-weblogic-domain-job")
                .namespace(getInputs().getNamespace()))
        .spec(
            newJobSpec()
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
                                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                                .putLabelsItem(
                                    APP_LABEL,
                                    getInputs().getDomainUID() + "-create-weblogic-domain-job"))
                        .spec(
                            newPodSpec()
                                .restartPolicy("Never")
                                .addContainersItem(
                                    newContainer()
                                        .name("create-weblogic-domain-job")
                                        .image("store/oracle/weblogic:12.2.1.3")
                                        .imagePullPolicy("IfNotPresent")
                                        .addCommandItem("/bin/sh")
                                        .addArgsItem("/u01/weblogic/create-domain-job.sh")
                                        .addEnvItem(
                                            newEnvVar().name("SHARED_PATH").value("/shared"))
                                        .addPortsItem(newContainerPort().containerPort(7001))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("create-weblogic-domain-job-cm-volume")
                                                .mountPath("/u01/weblogic"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-domain-storage-volume")
                                                .mountPath("/shared"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-credentials-volume")
                                                .mountPath("/weblogic-operator/secrets")))
                                .addVolumesItem(
                                    newVolume()
                                        .name("create-weblogic-domain-job-cm-volume")
                                        .configMap(
                                            newConfigMapVolumeSource()
                                                .name(
                                                    getInputs().getDomainUID()
                                                        + "-create-weblogic-domain-job-cm")))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-domain-storage-volume")
                                        .persistentVolumeClaim(
                                            newPersistentVolumeClaimVolumeSource()
                                                .claimName(
                                                    getInputs()
                                                        .getWeblogicDomainPersistentVolumeClaimName())))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-credentials-volume")
                                        .secret(
                                            newSecretVolumeSource()
                                                .secretName(
                                                    getInputs()
                                                        .getWeblogicCredentialsSecretName()))))));
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
    String actualCreateDomainJobSh =
        getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_JOB_SH);
    String actualReadDomainSecretPy =
        getThenEmptyConfigMapDataValue(actual, PROPERTY_READ_DOMAIN_SECRET_PY);
    String actualCreateDomainScriptSh =
        getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_SCRIPT_SH);
    String actualCreateDomainPy = getThenEmptyConfigMapDataValue(actual, PROPERTY_CREATE_DOMAIN_PY);

    // don't allOf since we want to make sure the later assertThats are only called if the first one
    // passes
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
    return newConfigMap()
        .metadata(
            newObjectMeta()
                .name(getInputs().getDomainUID() + "-create-weblogic-domain-job-cm")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
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
    assertThat(actualCreateDomainJobSh, containsRegexps(getInputs().getDomainName()));
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

  private Domain getActualDomain() {
    return getDomainCustomResourceYaml().getDomain();
  }

  protected Domain getExpectedDomain() {
    return newDomain()
        .withMetadata(
            newObjectMeta()
                .name(getInputs().getDomainUID())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .withSpec(
            newDomainSpec()
                .withDomainUID(getInputs().getDomainUID())
                .withDomainName(getInputs().getDomainName())
                .withImage("store/oracle/weblogic:12.2.1.3")
                .withImagePullPolicy("IfNotPresent")
                .withAdminSecret(
                    newSecretReference().name(getInputs().getWeblogicCredentialsSecretName()))
                .withAsName(getInputs().getAdminServerName())
                .withAsPort(Integer.parseInt(getInputs().getAdminPort()))
                .withStartupControl(getInputs().getStartupControl())
                .withServerStartup(
                    newServerStartupList()
                        .addElement(
                            newServerStartup()
                                .withDesiredState("RUNNING")
                                .withServerName(getInputs().getAdminServerName())
                                .withEnv(
                                    newEnvVarList()
                                        .addElement(
                                            newEnvVar()
                                                .name("JAVA_OPTIONS")
                                                .value(getInputs().getJavaOptions()))
                                        .addElement(
                                            newEnvVar()
                                                .name("USER_MEM_ARGS")
                                                .value("-Xms64m -Xmx256m ")))))
                .withClusterStartup(
                    newClusterStartupList()
                        .addElement(
                            newClusterStartup()
                                .withDesiredState("RUNNING")
                                .withClusterName(getInputs().getClusterName())
                                .withReplicas(
                                    Integer.parseInt(getInputs().getInitialManagedServerReplicas()))
                                .withEnv(
                                    newEnvVarList()
                                        .addElement(
                                            newEnvVar()
                                                .name("JAVA_OPTIONS")
                                                .value(getInputs().getJavaOptions()))
                                        .addElement(
                                            newEnvVar()
                                                .name("USER_MEM_ARGS")
                                                .value("-Xms64m -Xmx256m "))))));
  }

  @Test
  public void generatesCorrect_loadBalancerServiceAccount() throws Exception {
    assertThat(getActualTraefikServiceAccount(), yamlEqualTo(getExpectedTraefikServiceAccount()));
  }

  protected V1ServiceAccount getActualApacheServiceAccount() {
    return getApacheYaml().getApacheServiceAccount();
  }

  protected V1ServiceAccount getExpectedApacheServiceAccount() {
    return newServiceAccount()
        .metadata(
            newObjectMeta()
                .name(getApacheName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(APP_LABEL, getApacheAppName()));
  }

  protected V1ServiceAccount getActualTraefikServiceAccount() {
    return getTraefikYaml().getTraefikServiceAccount();
  }

  protected V1ServiceAccount getExpectedTraefikServiceAccount() {
    return newServiceAccount()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()));
  }

  protected V1ServiceAccount getActualVoyagerServiceAccount() {
    return getVoyagerOperatorSecurityYaml().getVoyagerServiceAccount();
  }

  protected V1ServiceAccount getExpectedVoyagerServiceAccount() {
    return newServiceAccount()
        .metadata(
            newObjectMeta()
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()));
  }

  @Test
  public void generatesCorrect_loadBalancerDeployment() throws Exception {
    assertThat(getActualTraefikDeployment(), yamlEqualTo(getExpectedTraefikDeployment()));
  }

  protected ExtensionsV1beta1Deployment getActualApacheDeployment() {
    return getApacheYaml().getApacheDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedApacheDeployment() {
    return newDeployment()
        .apiVersion(API_VERSION_EXTENSIONS_V1BETA1) // TBD - why is apache using this older version?
        .metadata(
            newObjectMeta()
                .name(getApacheName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(APP_LABEL, getApacheAppName()))
        .spec(
            newDeploymentSpec()
                .replicas(1)
                .selector(
                    newLabelSelector()
                        .putMatchLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                        .putMatchLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                        .putMatchLabelsItem(APP_LABEL, getApacheAppName()))
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                                .putLabelsItem(APP_LABEL, getApacheAppName()))
                        .spec(
                            newPodSpec()
                                .serviceAccountName(getApacheName())
                                .terminationGracePeriodSeconds(60L)
                                .addContainersItem(
                                    newContainer()
                                        .name(getApacheName())
                                        .image("store/oracle/apache:12.2.1.3")
                                        .imagePullPolicy("Never")
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("WEBLOGIC_CLUSTER")
                                                .value(
                                                    getInputs().getDomainUID()
                                                        + "-cluster-"
                                                        + getClusterNameLC()
                                                        + ":"
                                                        + getInputs().getManagedServerPort()))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("LOCATION")
                                                .value(getInputs().getLoadBalancerAppPrepath()))
                                        // .addEnvItem(newEnvVar()
                                        //  .name("WEBLOGIC_HOST")
                                        //  .value(getInputs().getDomainUID() + "-" +
                                        // getInputs().getAdminServerName()))
                                        // .addEnvItem(newEnvVar()
                                        //  .name("WEBLOGIC_PORT")
                                        //  .value(getInputs().getAdminPort()))
                                        .readinessProbe(
                                            newProbe()
                                                .tcpSocket(
                                                    newTCPSocketAction().port(newIntOrString(80)))
                                                .failureThreshold(1)
                                                .initialDelaySeconds(10)
                                                .periodSeconds(10)
                                                .successThreshold(1)
                                                .timeoutSeconds(2))
                                        .livenessProbe(
                                            newProbe()
                                                .tcpSocket(
                                                    newTCPSocketAction().port(newIntOrString(80)))
                                                .failureThreshold(3)
                                                .initialDelaySeconds(10)
                                                .periodSeconds(10)
                                                .successThreshold(1)
                                                .timeoutSeconds(2))))));
  }

  protected ExtensionsV1beta1Deployment getActualTraefikDeployment() {
    return getTraefikYaml().getTraefikDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedTraefikDeployment() {
    return newDeployment()
        .apiVersion(
            API_VERSION_EXTENSIONS_V1BETA1) // TBD - why is traefik using this older version?
        .metadata(
            newObjectMeta()
                .name(getTraefikScope())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(
            newDeploymentSpec()
                .replicas(1)
                .selector(
                    newLabelSelector()
                        .putMatchLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                        .putMatchLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
                        .spec(
                            newPodSpec()
                                .serviceAccountName(getTraefikScope())
                                .terminationGracePeriodSeconds(60L)
                                .addContainersItem(
                                    newContainer()
                                        .name("traefik")
                                        .image("traefik:1.4.5")
                                        .resources(
                                            newResourceRequirements()
                                                .putRequestsItem("cpu", Quantity.fromString("100m"))
                                                .putRequestsItem(
                                                    "memory", Quantity.fromString("20Mi"))
                                                .putLimitsItem("cpu", Quantity.fromString("100m"))
                                                .putLimitsItem(
                                                    "memory", Quantity.fromString("30Mi")))
                                        .readinessProbe(
                                            newProbe()
                                                .tcpSocket(
                                                    newTCPSocketAction().port(newIntOrString(80)))
                                                .failureThreshold(1)
                                                .initialDelaySeconds(10)
                                                .periodSeconds(10)
                                                .successThreshold(1)
                                                .timeoutSeconds(2))
                                        .livenessProbe(
                                            newProbe()
                                                .tcpSocket(
                                                    newTCPSocketAction().port(newIntOrString(80)))
                                                .failureThreshold(3)
                                                .initialDelaySeconds(10)
                                                .periodSeconds(10)
                                                .successThreshold(1)
                                                .timeoutSeconds(2))
                                        .addVolumeMountsItem(
                                            newVolumeMount().name("config").mountPath("/config"))
                                        .addPortsItem(
                                            newContainerPort()
                                                .name("http")
                                                .containerPort(80)
                                                .protocol("TCP"))
                                        .addPortsItem(
                                            newContainerPort()
                                                .name("dash")
                                                .containerPort(8080)
                                                .protocol("TCP"))
                                        .addArgsItem("--configfile=/config/traefik.toml"))
                                .addVolumesItem(
                                    newVolume()
                                        .name("config")
                                        .configMap(
                                            newConfigMapVolumeSource()
                                                .name(getTraefikScope() + "-cm"))))));
  }

  protected ExtensionsV1beta1Deployment getActualVoyagerDeployment() {
    return getVoyagerOperatorYaml().getVoyagerOperatorDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedVoyagerDeployment() {
    return newDeployment()
        .apiVersion(API_VERSION_APPS_V1BETA1)
        .metadata(
            newObjectMeta()
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .spec(
            newDeploymentSpec()
                .replicas(1)
                .selector(newLabelSelector().putMatchLabelsItem(APP_LABEL, getVoyagerName()))
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(APP_LABEL, getVoyagerName())
                                .putAnnotationsItem(
                                    "scheduler.alpha.kubernetes.io/critical-pod", ""))
                        .spec(
                            newPodSpec()
                                .serviceAccountName(getVoyagerOperatorName())
                                .imagePullSecrets(newLocalObjectReferenceList())
                                .addContainersItem(
                                    newContainer()
                                        .name(getVoyagerName())
                                        .addArgsItem("run")
                                        .addArgsItem("--v=3")
                                        .addArgsItem("--rbac=true")
                                        .addArgsItem("--cloud-provider=")
                                        .addArgsItem("--cloud-config=")
                                        .addArgsItem("--ingress-class=")
                                        .addArgsItem("--restrict-to-operator-namespace=false")
                                        .addArgsItem("--docker-registry=appscode")
                                        .addArgsItem("--haproxy-image-tag=1.7.10-6.0.0")
                                        .addArgsItem("--secure-port=8443")
                                        .addArgsItem("--audit-log-path=-")
                                        .addArgsItem("--tls-cert-file=/var/serving-cert/tls.crt")
                                        .addArgsItem(
                                            "--tls-private-key-file=/var/serving-cert/tls.key")
                                        .image("appscode/voyager:6.0.0")
                                        .addPortsItem(newContainerPort().containerPort(8443))
                                        .addPortsItem(newContainerPort().containerPort(56790))
                                        .addPortsItem(newContainerPort().containerPort(56791))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .mountPath("/etc/kubernetes")
                                                .name("cloudconfig")
                                                .readOnly(true))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .mountPath("/var/serving-cert")
                                                .name("serving-cert"))
                                        .readinessProbe(
                                            newProbe()
                                                .httpGet(
                                                    newHTTPGetAction()
                                                        .path("/healthz")
                                                        .port(newIntOrString(8443))
                                                        .scheme("HTTPS"))))
                                .addVolumesItem(
                                    newVolume()
                                        .hostPath(newHostPathVolumeSource().path("/etc/kubernetes"))
                                        .name("cloudconfig"))
                                .addVolumesItem(
                                    newVolume()
                                        .name("serving-cert")
                                        .secret(
                                            newSecretVolumeSource()
                                                .defaultMode(420)
                                                .secretName(getVoyagerName() + "-apiserver-cert")))
                                .addTolerationsItem(
                                    newToleration()
                                        .key("CriticalAddonsOnly")
                                        .operator("Exists")))));
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
    // don't allOf since we want to make sure the second assertThat is only called if the first one
    // passes
    assertThat(actual, yamlEqualTo(getExpectedTraefikConfigMap()));
    assertThatActualTraefikTomlIsCorrect(actualTraefikToml);
  }

  protected V1ConfigMap getActualTraefikConfigMap() {
    return getTraefikYaml().getTraefikConfigMap();
  }

  protected V1ConfigMap getExpectedTraefikConfigMap() {
    return newConfigMap()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope() + "-cm")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .putDataItem(PROPERTY_TRAEFIK_TOML, "");
  }

  protected void assertThatActualTraefikTomlIsCorrect(String actualTraefikToml) {
    assertThat(
        actualTraefikToml,
        containsString(
            "labelselector = \""
                + DOMAINUID_LABEL
                + "="
                + getInputs().getDomainUID()
                + ","
                + CLUSTERNAME_LABEL
                + "="
                + getInputs().getClusterName()
                + "\""));
  }

  @Test
  public void generatesCorrect_loadBalancerService() throws Exception {
    assertThat(getActualTraefikService(), yamlEqualTo(getExpectedTraefikService()));
  }

  protected V1Service getActualApacheService() {
    return getApacheYaml().getApacheService();
  }

  protected V1Service getExpectedApacheService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name(getApacheName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .spec(
            newServiceSpec()
                .type("NodePort")
                .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putSelectorItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putSelectorItem(APP_LABEL, getApacheAppName())
                .addPortsItem(
                    newServicePort()
                        .name("rest-https")
                        .port(80)
                        .nodePort(Integer.parseInt(getInputs().getLoadBalancerWebPort()))));
  }

  protected V1Service getActualTraefikService() {
    return getTraefikYaml().getTraefikService();
  }

  protected V1Service getExpectedTraefikService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(
            newServiceSpec()
                .type("NodePort")
                .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putSelectorItem(CLUSTERNAME_LABEL, getInputs().getClusterName())
                .addPortsItem(
                    newServicePort()
                        .name("http")
                        .targetPort(newIntOrString("http"))
                        .port(80)
                        .nodePort(Integer.parseInt(getInputs().getLoadBalancerWebPort()))));
  }

  protected V1Service getActualVoyagerService() {
    return getVoyagerOperatorYaml().getVoyagerOperatorService();
  }

  protected V1Service getExpectedVoyagerService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .spec(
            newServiceSpec()
                .putSelectorItem(APP_LABEL, getVoyagerName())
                .addPortsItem(
                    newServicePort().name("admission").port(443).targetPort(newIntOrString(8443)))
                .addPortsItem(
                    newServicePort().name("ops").port(56790).targetPort(newIntOrString(56790)))
                .addPortsItem(
                    newServicePort().name("acme").port(56791).targetPort(newIntOrString(56791))));
  }

  @Test
  public void generatesCorrect_loadBalancerDashboardService() throws Exception {
    assertThat(
        getActualTraefikDashboardService(), yamlEqualTo(getExpectedTraefikDashboardService()));
  }

  protected V1Service getActualTraefikDashboardService() {
    return getTraefikYaml().getTraefikDashboardService();
  }

  protected V1Service getExpectedTraefikDashboardService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope() + "-dashboard")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .spec(
            newServiceSpec()
                .type("NodePort")
                .putSelectorItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putSelectorItem(CLUSTERNAME_LABEL, getInputs().getClusterName())
                .addPortsItem(
                    newServicePort()
                        .name("dash")
                        .targetPort(newIntOrString("dash"))
                        .port(8080)
                        .nodePort(Integer.parseInt(getInputs().getLoadBalancerDashboardPort()))));
  }

  @Test
  public void generatesCorrect_loadBalancerClusterRole() throws Exception {
    assertThat(getActualTraefikClusterRole(), yamlEqualTo(getExpectedTraefikClusterRole()));
  }

  protected V1beta1ClusterRole getActualApacheClusterRole() {
    return getApacheSecurityYaml().getApacheClusterRole();
  }

  protected V1beta1ClusterRole getExpectedApacheClusterRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getApacheName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("pods", "services", "endpoints", "secrets"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("extensions")
                .addResourcesItem("ingresses")
                .verbs(asList("get", "list", "watch")));
  }

  protected V1beta1ClusterRole getActualTraefikClusterRole() {
    return getTraefikSecurityYaml().getTraefikClusterRole();
  }

  protected V1beta1ClusterRole getExpectedTraefikClusterRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("pods", "services", "endpoints", "secrets"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("extensions")
                .addResourcesItem("ingresses")
                .verbs(asList("get", "list", "watch")));
  }

  protected V1beta1ClusterRole getActualVoyagerClusterRole() {
    return getVoyagerOperatorSecurityYaml().getVoyagerClusterRole();
  }

  protected V1beta1ClusterRole getExpectedVoyagerClusterRole() {
    return newClusterRole()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("apiextensions.k8s.io")
                .addResourcesItem("customresourcedefinitions")
                .addVerbsItem("*"))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("extensions")
                .addResourcesItem("thirdpartyresources")
                .addVerbsItem("*"))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("voyager.appscode.com")
                .resources(asList("*"))
                .verbs(asList("*")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("monitoring.coreos.com")
                .addResourcesItem("servicemonitors")
                .verbs(asList("get", "list", "watch", "create", "update", "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("apps")
                .addResourcesItem("deployments")
                .verbs(asList("*")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("extensions")
                .addResourcesItem("deployments")
                .addResourcesItem("daemonsets")
                .addResourcesItem("ingresses")
                .verbs(asList("*")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("replicationcontrollers")
                .addResourcesItem("services")
                .addResourcesItem("endpoints")
                .addResourcesItem("configmaps")
                .verbs(asList("*")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("secrets")
                .verbs(asList("get", "list", "watch", "create", "update", "patch")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("namespaces")
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("events")
                .verbs(asList("create")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("pods")
                .verbs(asList("list", "watch", "delete", "deletecollection")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("nodes")
                .verbs(asList("list", "watch", "get")))
        .addRulesItem(
            newPolicyRule()
                .apiGroups(asList(""))
                .addResourcesItem("serviceaccounts")
                .verbs(asList("get", "create", "delete", "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem(API_GROUP_RBAC)
                .addResourcesItem("rolebindings")
                .addResourcesItem("roles")
                .verbs(asList("get", "create", "delete", "patch")));
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
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getApacheName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, APACHE_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .addSubjectsItem(
            newSubject()
                .kind(KIND_SERVICE_ACCOUNT)
                .name(getApacheName())
                .namespace(getInputs().getNamespace()))
        .roleRef(newRoleRef().name(getApacheName()).apiGroup(API_GROUP_RBAC));
  }

  protected V1beta1ClusterRoleBinding getActualTraefikDashboardClusterRoleBinding() {
    return getTraefikSecurityYaml().getTraefikDashboardClusterRoleBinding();
  }

  protected V1beta1ClusterRoleBinding getExpectedTraefikDashboardClusterRoleBinding() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getTraefikScope())
                .putLabelsItem(RESOURCE_VERSION_LABEL, TRAEFIK_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putLabelsItem(CLUSTERNAME_LABEL, getInputs().getClusterName()))
        .addSubjectsItem(
            newSubject()
                .kind(KIND_SERVICE_ACCOUNT)
                .name(getTraefikScope())
                .namespace(getInputs().getNamespace()))
        .roleRef(newRoleRef().name(getTraefikScope()).apiGroup(API_GROUP_RBAC));
  }

  protected V1beta1ClusterRoleBinding getActualVoyagerClusterRoleBinding() {
    return getVoyagerOperatorSecurityYaml().getVoyagerClusterRoleBinding();
  }

  protected V1beta1ClusterRoleBinding getExpectedVoyagerClusterRoleBinding() {
    return newClusterRoleBinding()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .roleRef(
            newRoleRef()
                .apiGroup(API_GROUP_RBAC)
                .kind(KIND_CLUSTER_ROLE)
                .name(getVoyagerOperatorName()))
        .addSubjectsItem(
            newSubject()
                .kind(KIND_SERVICE_ACCOUNT)
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName()));
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
    return newPersistentVolume()
        .metadata(
            newObjectMeta()
                .name(getInputs().getWeblogicDomainPersistentVolumeName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .spec(
            newPersistentVolumeSpec()
                .storageClassName(getInputs().getWeblogicDomainStorageClass())
                .putCapacityItem(
                    "storage", Quantity.fromString(getInputs().getWeblogicDomainStorageSize()))
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
    return newPersistentVolumeClaim()
        .metadata(
            newObjectMeta()
                .name(getInputs().getWeblogicDomainPersistentVolumeClaimName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .spec(
            newPersistentVolumeClaimSpec()
                .storageClassName(getInputs().getWeblogicDomainStorageClass())
                .addAccessModesItem("ReadWriteMany")
                .resources(
                    newResourceRequirements()
                        .putRequestsItem(
                            "storage",
                            Quantity.fromString(getInputs().getWeblogicDomainStorageSize()))));
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

  protected String getApacheName() {
    return getInputs().getDomainUID() + "-" + getApacheAppName();
  }

  protected String getApacheAppName() {
    return "apache-webtier";
  }

  protected String getVoyagerName() {
    return "voyager";
  }

  protected String getVoyagerOperatorName() {
    return getVoyagerName() + "-operator";
  }
}
