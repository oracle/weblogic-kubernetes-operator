// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static java.util.Arrays.asList;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1PersistentVolume;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-domain-operator.sh
 * creates are correct when the admin node port is disabled, the t3 channel is disabled,
 * there is no image pull secret, and production mode is disabled.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesDisabledTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  private static final String PROPERTY_TRAEFIK_TOML = "traefik.toml";
  private static final String PROPERTY_UTILITY_SH = "utility.sh";
  private static final String PROPERTY_CREATE_DOMAIN_JOB_SH = "create-domain-job.sh";
  private static final String PROPERTY_READ_DOMAIN_SECRET_PY = "read-domain-secret.py";
  private static final String PROPERTY_CREATE_DOMAIN_SCRIPT_SH = "create-domain-script.sh";
  private static final String PROPERTY_CREATE_DOMAIN_PY = "create-domain.py";

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateDomainInputs.newInputs(); // defaults to admin node port off, t3 channel off
    generatedFiles = GeneratedDomainYamlFiles.generateDomainYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_createWeblogicDomainYaml_createWeblogicDomainJob() throws Exception {
    assertThat(
      generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob(),
      yamlEqualTo(generatedFiles.getCreateWeblogicDomainJobYaml().getExpectedBaseCreateWeblogicDomainJob()));
  }

  @Test
  public void generatesCorrect_createWeblogicDomainYaml_createWeblogicDomainConfigMap() throws Exception {
    // The config map contains several properties that contain shell and wlst scripts
    // that we don't want to duplicate in the test.  However, part of their text
    // is computed from the inputs, so we want to validate that part of the info.
    // First, if it's present, extract these values from the config map
    // then empty them out.
    // Second, make sure the rest of the config map is as expected.
    // Third, make sure that these values contains the expected
    // expansion of the input properties.
    V1ConfigMap have = generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainConfigMap();
    String utilityShVal = getThenEmptyConfigMapDataValue(have, PROPERTY_UTILITY_SH);
    String createDomainJobShVal = getThenEmptyConfigMapDataValue(have, PROPERTY_CREATE_DOMAIN_JOB_SH);
    String readDomainSecretPyVal = getThenEmptyConfigMapDataValue(have, PROPERTY_READ_DOMAIN_SECRET_PY);
    String createDomainScriptShVal = getThenEmptyConfigMapDataValue(have, PROPERTY_CREATE_DOMAIN_SCRIPT_SH);
    String createDomainPyVal = getThenEmptyConfigMapDataValue(have, PROPERTY_CREATE_DOMAIN_PY);
    // don't allOf since we want to make sure the second assertThat is only called if the first passes
    assertThat(
      have,
      yamlEqualTo(
        newConfigMap()
          .metadata(newObjectMeta()
            .name("domain-" + inputs.getDomainUid() + "-scripts")
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
          .putDataItem(PROPERTY_UTILITY_SH, "")
          .putDataItem(PROPERTY_CREATE_DOMAIN_JOB_SH, "")
          .putDataItem(PROPERTY_READ_DOMAIN_SECRET_PY, "")
          .putDataItem(PROPERTY_CREATE_DOMAIN_SCRIPT_SH, "")
          .putDataItem(PROPERTY_CREATE_DOMAIN_PY, "")));

    // utility.sh and read-domain-secret.py don't depend on the inputs so don't bother checking their text

    /*
      create-domain-job.sh: |-
        script='%CREATE_DOMAIN_SCRIPT%'
        domainFolder=${SHARED_PATH}/domain/%DOMAIN_NAME%
    */
    assertThat(
      createDomainJobShVal,
      containsRegexps(
        inputs.getCreateDomainScript(),
        inputs.getDomainName()));

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
      createDomainScriptShVal,
      containsRegexps(
        inputs.getDomainUid(),
        inputs.getDomainName(),
        inputs.getAdminServerName(),
        inputs.getManagedServerNameBase(),
        inputs.getDomainUid() + "-" + inputs.getAdminServerName(),
        "index -lt " + inputs.getManagedServerCount()));

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
      createDomainPyVal,
      containsRegexps(
        inputs.getDomainName(),
        inputs.getClusterName(),
        inputs.getAdminServerName(),
        inputs.getManagedServerNameBase(),
        inputs.getDomainUid() + "-" + inputs.getAdminServerName(),
        inputs.getDomainUid() + "-" + inputs.getManagedServerNameBase(),
        "setProductionModeEnabled\\(" + inputs.getProductionModeEnabled() + "\\)",
        "server_port *= " + inputs.getManagedServerPort(),
        "number_of_ms *= " + inputs.getManagedServerCount(),
        "set\\('ListenPort', " + inputs.getAdminPort() + "\\)",
        "set\\('PublicPort', " + inputs.getT3ChannelPort() + "\\)",
        "set\\('PublicAddress', '" + inputs.getT3PublicAddress() + "'\\)"));
     // TBD should we check anything else?
  }

  @Test
  public void generatesCorrect_domainCustomResourceYaml_domain() throws Exception {
    assertThat(
      generatedFiles.getDomainCustomResourceYaml().getDomain(),
      yamlEqualTo(generatedFiles.getDomainCustomResourceYaml().getBaseExpectedDomain()));
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikServiceAccount() throws Exception {
    assertThat(
      generatedFiles.getTraefikYaml().getTraefikServiceAccount(),
      yamlEqualTo(
        newServiceAccount()
          .metadata(newObjectMeta()
            .name(getTraefikScope())
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid())
            .putLabelsItem("weblogic.clusterName", getClusterNameLC()))));
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikDeployment() throws Exception {
//System.out.println("MOREAUT_DEBUG traefik deployment =\n" + newYaml().dump(generatedFiles.getTraefikYaml().getTraefikDeployment()));
/*
kind: Deployment
apiVersion: extensions/v1beta1
metadata:
  name: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
  namespace: %NAMESPACE%
  labels:
    app: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
    weblogic.domainUID: %DOMAIN_UID%
    weblogic.clusterName: %CLUSTER_NAME_LC%
spec:
  replicas: 1
  selector:
    matchLabels:
      app: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
  template:
    metadata:
      labels:
        app: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
        weblogic.domainUID: %DOMAIN_UID%
        weblogic.clusterName: %CLUSTER_NAME_LC%
    spec:
      serviceAccountName: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
      terminationGracePeriodSeconds: 60
      containers:
      - image: traefik:1.4.5
        name: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
        resources:
          requests:
            cpu: "100m"
            memory: "20Mi"
          limits:
            cpu: "100m"
            memory: "30Mi"
        readinessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 1
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
        livenessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 3
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
        volumeMounts:
        - mountPath: /config
          name: config
        ports:
        - name: http
          containerPort: 80
          protocol: TCP
        - name: dash
          containerPort: 8080
          protocol: TCP
        args:
        - --configfile=/config/traefik.toml
      volumes:
      - name: config
        configMap:
          name: %DOMAIN_UID%-%CLUSTER_NAME_LC%-traefik
*/
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikConfigMap() throws Exception {
    // The config map contains a 'traefik.toml' property that has a lot of text
    // that we don't want to duplicate in the test.  However, part of the text
    // is computed from the inputs, so we want to validate that part of the info.
    // First, if it's present, extract the trafik.toml value from the config map
    // then empty it out.
    // Second, make sure the rest of the config map is as expected.
    // Third, make sure that the traefik.tom value contains the expected
    // expansion of the input properties.
    V1ConfigMap have = generatedFiles.getTraefikYaml().getTraefikConfigMap();
    String traefikTomlVal = getThenEmptyConfigMapDataValue(have, PROPERTY_TRAEFIK_TOML);
    // don't allOf since we want to make sure the second assertThat is only called if the first passes
    assertThat(
      have,
      yamlEqualTo(
        newConfigMap()
          .metadata(newObjectMeta()
            .name(getTraefikScope())
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid())
            .putLabelsItem("weblogic.clusterName", getClusterNameLC())
            .putLabelsItem("app", getTraefikScope()))
          .putDataItem(PROPERTY_TRAEFIK_TOML, "")));
    assertThat(
      traefikTomlVal,
      containsString("labelselector = \"weblogic.domainUID=" + inputs.getDomainUid() + ",weblogic.clusterName=" + inputs.getClusterName() + "\""));
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikService() throws Exception {
    assertThat(
      generatedFiles.getTraefikYaml().getTraefikService(),
      yamlEqualTo(
        newService()
          .metadata(newObjectMeta()
            .name(getTraefikScope())
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid())
            .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
          .spec(newServiceSpec()
            .type("NodePort")
            .putSelectorItem("app", getTraefikScope())
            .addPortsItem(newServicePort()
              .name("http")
              .targetPort(newIntOrString("http"))
              .port(80)
              .nodePort(Integer.parseInt(inputs.getLoadBalancerWebPort()))))));
  }

  @Test
  public void generatesCorrect_traefikYaml_traefikDashboardService() throws Exception {
    assertThat(
      generatedFiles.getTraefikYaml().getTraefikDashboardService(),
      yamlEqualTo(
        newService()
          .metadata(newObjectMeta()
            .name(getTraefikScope() + "-dashboard")
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid())
            .putLabelsItem("weblogic.clusterName", getClusterNameLC())
            .putLabelsItem("app", getTraefikScope()))
          .spec(newServiceSpec()
            .type("NodePort")
            .putSelectorItem("app", getTraefikScope())
            .addPortsItem(newServicePort()
              .name("dash")
              .targetPort(newIntOrString("dash"))
              .port(8080)
              .nodePort(Integer.parseInt(inputs.getLoadBalancerAdminPort()))))));
  }

  @Test
  public void generatesCorrect_traefikSecurityYaml_traefikClusterRole() throws Exception {
    assertThat(
      generatedFiles.getTraefikSecurityYaml().getTraefikClusterRole(),
      yamlEqualTo(
        newClusterRole()
          .metadata(newObjectMeta()
            .name(getTraefikScope())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("")
            .resources(asList("pods", "services", "endpoints", "secrets"))
            .verbs(asList("get", "list", "watch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("extensions")
            .addResourcesItem("ingresses")
            .verbs(asList("get", "list", "watch")))));
  }

  @Test
  public void generatesCorrect_traefikSecurityYaml_traefikDashboardClusterRoleBinding() throws Exception {
    assertThat(
      generatedFiles.getTraefikSecurityYaml().getTraefikDashboardClusterRoleBinding(),
      yamlEqualTo(
        newClusterRoleBinding()
          .metadata(newObjectMeta()
            .name(getTraefikScope())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid())
            .putLabelsItem("weblogic.clusterName", getClusterNameLC()))
          .addSubjectsItem(newSubject()
            .kind("ServiceAccount")
            .name(getTraefikScope())
            .namespace(inputs.getNamespace()))
          .roleRef(newRoleRef()
            .name(getTraefikScope())
            .apiGroup("rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolumeYaml_weblogicDomainPersistentVolume() throws Exception {
    V1PersistentVolume want =
      generatedFiles.getWeblogicDomainPersistentVolumeYaml().getExpectedBaseCreateWeblogicDomainPersistentVolume();
    want.getSpec()
      .hostPath(newHostPathVolumeSource()
        .path(inputs.getPersistencePath()));
    assertThat(
      generatedFiles.getWeblogicDomainPersistentVolumeYaml().getWeblogicDomainPersistentVolume(),
      yamlEqualTo(want));
  }

  @Test
  public void generatesCorrect_weblogicDomainPersistentVolumeClaimYaml_weblogicDomainPersistentVolumeClaim() throws Exception {
    assertThat(
      generatedFiles.getWeblogicDomainPersistentVolumeClaimYaml().getWeblogicDomainPersistentVolumeClaim(),
      yamlEqualTo(
        newPersistentVolumeClaim()
          .metadata(newObjectMeta()
            .name(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeClaimName())
            .namespace(inputs.getNamespace())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
          .spec(newPersistentVolumeClaimSpec()
            .storageClassName(inputs.getDomainUid())
            .addAccessModesItem("ReadWriteMany")
            .resources(newResourceRequirements()
              .putRequestsItem("storage", inputs.getPersistenceSize())))));
  }

  private String getTraefikScope() {
    return getClusterLCScope() + "-traefik";
  }

  private String getClusterLCScope() {
    return inputs.getDomainUid() + "-" + getClusterNameLC();
  }

  private String getClusterNameLC() {
    return inputs.getClusterName().toLowerCase();
  }
}
