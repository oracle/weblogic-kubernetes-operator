// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.LabelConstants.APP_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.VOYAGER_LOAD_BALANCER_V1;
import static oracle.kubernetes.operator.utils.CreateDomainInputs.LOAD_BALANCER_VOYAGER;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_GROUP_RBAC;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_RBAC_V1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_REGISTRATION_V1BETA1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_V1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.API_VERSION_VOYAGER_V1BETA1;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_CLUSTER_ROLE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_ROLE;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.KIND_SERVICE_ACCOUNT;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newAPIService;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newAPIServiceSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRole;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRoleBinding;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHTTPIngressBackend;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHTTPIngressPath;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newHTTPIngressRuleValue;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newIngress;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newIngressRule;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newIngressSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newIntOrString;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newObjectMeta;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPolicyRule;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRoleBinding;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRoleRef;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSecret;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newService;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServicePort;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceReference;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSubject;
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.appscode.voyager.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1APIService;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-domain.sh creates are correct
 * when the load balancer is voyager.
 */
public abstract class CreateDomainGeneratedFilesVoyagerTestBase
    extends CreateDomainGeneratedFilesOptionalFeaturesEnabledTestBase {

  protected static void defineDomainYamlFactory(DomainYamlFactory factory) throws Exception {
    setup(
        factory,
        withFeaturesEnabled(factory.newDomainValues()).loadBalancer(LOAD_BALANCER_VOYAGER));
  }

  @Test
  @Override
  public void generatesCorrect_loadBalancerDeployment() {
    assertThat(getActualVoyagerDeployment(), yamlEqualTo(getExpectedVoyagerDeployment()));
  }

  @Test
  public void generatesCorrect_loadBalancerSecret() {
    assertThat(getActualVoyagerSecret(), yamlEqualTo(getExpectedVoyagerSecret()));
  }

  @Test
  @Override
  public void generatesCorrect_loadBalancerService() {
    assertThat(getActualVoyagerService(), yamlEqualTo(getExpectedVoyagerService()));
  }

  @Test
  public void generatesCorrect_loadBalancerAPIService() {
    assertThat(getActualVoyagerAPIService(), yamlEqualTo(getExpectedVoyagerAPIService()));
  }

  @Test
  @Override
  public void generatesCorrect_loadBalancerServiceAccount() {
    assertThat(getActualVoyagerServiceAccount(), yamlEqualTo(getExpectedVoyagerServiceAccount()));
  }

  @Test
  @Override
  public void generatesCorrect_loadBalancerClusterRole() {
    assertThat(getActualVoyagerClusterRole(), yamlEqualTo(getExpectedVoyagerClusterRole()));
  }

  @Test
  @Override
  public void generatesCorrect_loadBalancerClusterRoleBinding() {
    assertThat(
        getActualVoyagerClusterRoleBinding(), yamlEqualTo(getExpectedVoyagerClusterRoleBinding()));
  }

  @Test
  public void generatesCorrect_loadBalancerAuthenticationReaderRoleBinding() {
    assertThat(
        getActualVoyagerAuthenticationReaderRoleBinding(),
        yamlEqualTo(getExpectedVoyagerAuthenticationReaderRoleBinding()));
  }

  @Test
  public void generatesCorrect_loadBalancerAuthDelegatorClusterRoleBinding() {
    assertThat(
        getActualVoyagerAuthDelegatorClusterRoleBinding(),
        yamlEqualTo(getExpectedVoyagerAuthDelegatorClusterRoleBinding()));
  }

  @Test
  public void generatesCorrect_loadBalancerAppsCodeEditClusterRole() {
    assertThat(
        getActualVoyagerAppsCodeEditClusterRole(),
        yamlEqualTo(getExpectedVoyagerAppsCodeEditClusterRole()));
  }

  @Test
  public void generatesCorrect_loadBalancerAppsCodeVidwClusterRole() {
    assertThat(
        getActualVoyagerAppsCodeViewClusterRole(),
        yamlEqualTo(getExpectedVoyagerAppsCodeViewClusterRole()));
  }

  @Test
  public void generatesCorrect_loadBalancerIngress() {
    assertThat(getActualVoyagerIngress(), yamlEqualTo(getExpectedVoyagerIngress()));
  }

  @Test
  public void generatesCorrect_loadBalancerService1() {
    assertThat(getActualVoyagerIngressService(), yamlEqualTo(getExpectedVoyagerIngressService()));
  }

  @Test
  public void loadBalancerIngressYaml_hasCorrectNumberOfObjects() {
    assertThat(
        getVoyagerIngressYaml().getObjectCount(),
        is(getVoyagerIngressYaml().getExpectedObjectCount()));
  }

  private V1Secret getActualVoyagerSecret() {
    return getVoyagerOperatorYaml().getVoyagerOperatorSecret();
  }

  private V1Secret getExpectedVoyagerSecret() {
    return newSecret()
        .apiVersion(API_VERSION_V1)
        .metadata(
            newObjectMeta()
                .name(getVoyagerName() + "-apiserver-cert")
                .namespace(getVoyagerName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .type("kubernetes.io/tls")
        .putDataItem(
            "tls.crt",
            Base64.decodeBase64(
                "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUM5akNDQWQ2Z0F3SUJBZ0lJTzhzQmdHcitZZEV3RFFZSktvWklodmNOQVFFTEJRQXdEVEVMTUFrR0ExVUUKQXhNQ1kyRXdIaGNOTVRnd05URTJNRE0wTlRFMVdoY05NVGt3TlRFMk1ETTBOVEUyV2pBUk1ROHdEUVlEVlFRRApFd1p6WlhKMlpYSXdnZ0VpTUEwR0NTcUdTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFESkZkUytubGVjCjBZVmt5UTUwMUgrVURvTXZLcWUxS1lQYUZlYXM2NlE4YjI5WkF2VzJ0bHBVd2tLQkJBM2JrYW1lTThmUTFqZXMKeW1aNUo1SUVYOHRFaERWcjFrMjJ6Zk9ranlxUjRvSEUwKzdqeGg1MjQvcy9nQWNNQjJNRlEyeXRyMkNlbGN5ZQpnd2JTSlhyTnRwY2MxNm1IRmFKZG5iYUtvM3Z1UnZGREZCQ2t1U3dvcHpqWGZrbURYYUNiMGRYcjJuVzdLeXJ3Cks3eldLZnlrNHhnVnJFVS9LL3I2cTRSTlVGYW5DejBJeVpwRjhJeHpMdW1oK3FUMGJ3S1dqSVlESkdIQTFHSDcKWUF5UUVEcE4rbkRHalJEeFVXWCs5R0lnRDJXak5OcEZHMzN6UjUyRS9tN3Y3ZXNROVdrUjI3ZlF6eHluZ29TegpJV0EzcGhwcnl0L1RBZ01CQUFHalZqQlVNQTRHQTFVZER3RUIvd1FFQXdJRm9EQVRCZ05WSFNVRUREQUtCZ2dyCkJnRUZCUWNEQVRBdEJnTlZIUkVFSmpBa2doeDJiM2xoWjJWeUxXOXdaWEpoZEc5eUxuWnZlV0ZuWlhJdWMzWmoKaHdSL0FBQUJNQTBHQ1NxR1NJYjNEUUVCQ3dVQUE0SUJBUUFWZDlNOXFucHZvdkdjWlM4a1N4UEl1cFNYamI1WAp5RUtsZTVib1p1YVdRRXhNMXdHOVc1R2R4Njhudkh1ajhMRnBjbzlXU3p5TmhLN3lKNCtIU0hBYmd1ZEY4VmtHCllCS1h4TGlJcnowVk81U1UrTnJGWURENVRlTXFXQXV5UWUvakwwVlJEOGRHZU9weTVRZUpPY0JTSWdocUk2NnoKNGRnd3pMLzNPVllRemVGdkd0VXUzMzdrRWJwUWx6UVovTFFEUzdqOUlaL2xzVFF4d1k4S2srMit3ZmU3dzhxRQpZY04vaGFPcmNKaHFQL3Fnd2pNcTF6QTJqUzdSUXhremdJNWIxdkhWYm1FUThFU2x2ZWdIL3M0MG1XcnV2YW9BClE1djFWSXUwWGZuSzBWZm9iZEduNXY4OWpIVTQyaFZ6K3FhRUFYU0dyV2dGSjJZQ21jandyQlhYCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0K"))
        .putDataItem(
            "tls.key",
            Base64.decodeBase64(
                "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlFcFFJQkFBS0NBUUVBeVJYVXZwNVhuTkdGWk1rT2ROUi9sQTZETHlxbnRTbUQyaFhtck91a1BHOXZXUUwxCnRyWmFWTUpDZ1FRTjI1R3BualBIME5ZM3JNcG1lU2VTQkYvTFJJUTFhOVpOdHMzenBJOHFrZUtCeE5QdTQ4WWUKZHVQN1A0QUhEQWRqQlVOc3JhOWducFhNbm9NRzBpVjZ6YmFYSE5lcGh4V2lYWjIyaXFONzdrYnhReFFRcExrcwpLS2M0MTM1SmcxMmdtOUhWNjlwMXV5c3E4Q3U4MWluOHBPTVlGYXhGUHl2NitxdUVUVkJXcHdzOUNNbWFSZkNNCmN5N3BvZnFrOUc4Q2xveUdBeVJod05SaCsyQU1rQkE2VGZwd3hvMFE4VkZsL3ZSaUlBOWxvelRhUlJ0OTgwZWQKaFA1dTcrM3JFUFZwRWR1MzBNOGNwNEtFc3lGZ042WWFhOHJmMHdJREFRQUJBb0lCQVFDR09rZG44c1NqRG8xUApxSkk0MUh4UTlac0dDaUFtNHc1N3JuRHI3dVFUMzRMaFZRTjJNcVY3dkt5dCtHblRycGtkM0l5K1Q3Q2NiQU1aClRwdSt4YjhtL21XMmxUZ05GYzlVZ3FpMDl4RU90VFhhMzY0SVNNaTNLNXdJb0ltdHdzTXg4VWE2dFY0QVZaQmgKQ05tL3BlbWJQQzZTMkpNb2tKV0FWLzdySUhuOS9xRHdmMWhrUEN4MTZ2dXRGWUlIclNuUU1USVdlZVAxY0VnUwpoKzdZbWozV3NmTTIvZjdUcldBZk5iOHhyYmdBaXRvYm0xNDVYRUh5eVhKV0FjTlcra3BlZCtkQ0tOYy9ud1ZvCmRIQUJiaU5RNFFUcU9XVGZpekZHYWFQZGhLVWNGYUJsbTlsUWtiQ2NvYW1XWFBZRUFmOE1YSGdRRDB2NXBkaXAKd0thUU1mRFJBb0dCQU9RSG1GNDBORU1lUHVVYmwvS3p1bDBGUFhzRXdwR0RPb3FDODc5VlN1Yngza2tua1ZtRwo4SUxyTSttRk85QldDYWNENTRZMjdzbTkzSDlBbllQYmhHSmo1ZWJvYUU5VFBNMTBqTnRXMU9NQUE4K3BiaUE0ClQ0TWZUTlQ1ejNXclRhak1FMEUzMVFTVFBqVDlvQmZxV1FQRXA2MHhaZkNrUmZLQWlTQVZJckVaQW9HQkFPSEEKSmxGTk9VODFrckNGREQxeDc2N015ZjF2Z0lsZlAxdENlMEQzRmNhR2x6SFA5WGE5MGI4dGxmWHBCa3lRZHZ4ZAoxeUgxQWVyMkJhVWJGY0t0ZmltbEhpSHVyUlFXZU94SW96VkVHKzlVejBwNC9Sb2Fwd3NPejQzMURTZkl5TGllClJURGNYNk14Y2hLWHdjaklrZ1hvR1ErUitGZjNSbnJwMjhIa2hSbkxBb0dCQU15TUU1M05ibVFXcVl5UzZBYUsKT2s1ZStQdDFGYU81OTdWd0tuNGpZSUV4elpnSnFsU0l1dzUxTmFmd0grdU9naklUU29nV2xyVFpYd1czVEpTUApRWDJRNXhYdXZFTU1BNnE1TmZFN1B0UXhtem1ZWG5VQWpqS3N6UnJ1eTY1ZDc5Zk8yQ2JVa256OEovMFkxWmNlClhLTUlzUENuTXk1ZDdYRE81REtuUXV0aEFvR0JBSisxemlZRXFUL1ZtZkxTSGVlMm5LZ2c3K0locVdFR2hvOHMKeUlBY2prWkdYOTc0emlMMGhkaG9Dc2pQMUFvRXhua2lkcG5xZVRIZVhmNEIzSEkvUlp0MjJvdU5ETnZDVGtoegoxeXRQQlNoYjZzODRLMi8xWFNwZ2p3eFNTcjFUdWxXS2UwN25DYTR0eEJOTlUrYVZwMkVRWS9KMUJhcE9JWW5CCnV4eEFiTDNqQW9HQUVDQTdZcGI3WGlFam5uN2dXcTJwWWNFZWZ2dUFOZDM3R3ljbDFVekQ3Z1RuanQzYnNTUysKOEVkOVVqeVJ5TzFiQUZqV0cvUU1CQ3I5Uk4vdU0ybDF4dExxTGZYbWN0QUxrd3pCdHVZYUR4djA5Q215OGZJdApudW9XN0RoVGRzUWJlUHdCY1JINks1dEJkNURERFFIclNTallrcVJ6RmJrS0JOaGhtVXhodGZVPQotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQo="));
  }

  private V1beta1APIService getActualVoyagerAPIService() {
    return getVoyagerOperatorYaml().getVoyagerOperatorAPIService();
  }

  private V1beta1APIService getExpectedVoyagerAPIService() {
    return newAPIService()
        .apiVersion(API_VERSION_REGISTRATION_V1BETA1)
        .metadata(
            newObjectMeta()
                .name("v1beta1.admission.voyager.appscode.com")
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .spec(
            newAPIServiceSpec()
                .caBundle(
                    Base64.decodeBase64(
                        "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUN1RENDQWFDZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRc0ZBREFOTVFzd0NRWURWUVFERXdKallUQWUKRncweE9EQTFNVFl3TXpRMU1UVmFGdzB5T0RBMU1UTXdNelExTVRWYU1BMHhDekFKQmdOVkJBTVRBbU5oTUlJQgpJakFOQmdrcWhraUc5dzBCQVFFRkFBT0NBUThBTUlJQkNnS0NBUUVBcS9HL2xtUlZvTXNyKy82VTIrWjloZE1oCjZINU1aSkZnT3pNaWtHZEFxTnVnNnZKRklhNld4clloVVcvVnRybkRETVkyRjVkYTZuTTI3cVdpdXJla2YvTGkKOFJXRnltTmVrNXpGV1NSUGttYXlOVzRCNmRDNmE0V1VxSmtVbUNKOC9WdnI2QkRqVkZWemNIV3JPeDJQU1g2UgovY0tEcytsYzVYbVB3V0hta1ZMWmhmOERMcURhRXV1SUFDTlE2dkFxT3dyVklwQVFsOHpJN0FQVHY4M0NzWjVqCk1XQmZVNXBsRnlNNmZGYnVqKzdtSWtqa01QM2JjaXdQV0FQcTB4VHdjU0NMeU9DQ1Rhd3BsUHhIRTRiYVpReEEKTnZNUmxEVEMvTUdUMGFaMFIremNXcHkwZ3RtcXAzblhTWTRLTW5qY2xDYjhYZGp6SUExTU8vaWR1SEw1cHdJRApBUUFCb3lNd0lUQU9CZ05WSFE4QkFmOEVCQU1DQXFRd0R3WURWUjBUQVFIL0JBVXdBd0VCL3pBTkJna3Foa2lHCjl3MEJBUXNGQUFPQ0FRRUFwaHU2eHVXQVVvNXkxdW9Gdkt6Y2tyOXA1MWpxWkJGdXAxV2JMOFkveXJUTVpRZkIKbUU3U240QVVCUS9xOEpFc1ZCcVJoRjFodHJtbzdUaDBDNVZNNmFZNnRaZDZuYWV5ZDlJaUt6ZkJyVnB5M08vUQpJaEZlZkNCTExienZlbnBwWnBkdUxKdEQ2VXRVb0FFTGdHNmR0Y2R4SUV6bzUxd0g4aVRKejBsQlBpY0prWjRkCnBXWUMyaFFOOFpRRU5aM29TTno5QWQrTkJXbWdXendsMXk4NmhxTTZHUkVKSCtpK3BZeWpkTk9qR3orbGVxU1AKa1lnbjZZVlA3TnZkcElYKzlzby9JUGF3RTJoRDAzOERtZVhscTFYRnhnc1FBVTRFd3JoSmU3WHMzWHZ3aFZlTwowYjBZWGVPNENkemtZNmtxNlpFci93Z0JxWEZjc3MwaHJ2WWZBUT09Ci0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0K"))
                .group("admission.voyager.appscode.com")
                .groupPriorityMinimum(1000)
                .versionPriority(15)
                .service(
                    newServiceReference()
                        .name(getVoyagerOperatorName())
                        .namespace(getVoyagerName()))
                .version("v1beta1"));
  }

  private V1beta1RoleBinding getActualVoyagerAuthenticationReaderRoleBinding() {
    return getVoyagerOperatorSecurityYaml().getVoyagerAuthenticationReaderRoleBinding();
  }

  private V1beta1RoleBinding getExpectedVoyagerAuthenticationReaderRoleBinding() {
    return newRoleBinding()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name("voyager-apiserver-extension-server-authentication-reader")
                .namespace("kube-system")
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .roleRef(
            newRoleRef()
                .apiGroup(API_GROUP_RBAC)
                .kind(KIND_ROLE)
                .name("extension-apiserver-authentication-reader"))
        .addSubjectsItem(
            newSubject()
                .kind(KIND_SERVICE_ACCOUNT)
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName()));
  }

  private V1beta1ClusterRoleBinding getActualVoyagerAuthDelegatorClusterRoleBinding() {
    return getVoyagerOperatorSecurityYaml().getVoyagerAuthDelegatorClusterRoleBinding();
  }

  private V1beta1ClusterRoleBinding getExpectedVoyagerAuthDelegatorClusterRoleBinding() {
    return newClusterRoleBinding()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name("voyager-apiserver-auth-delegator")
                .putLabelsItem(APP_LABEL, getVoyagerName()))
        .roleRef(
            newRoleRef()
                .kind(KIND_CLUSTER_ROLE)
                .apiGroup(API_GROUP_RBAC)
                .name("system:auth-delegator"))
        .addSubjectsItem(
            newSubject()
                .kind(KIND_SERVICE_ACCOUNT)
                .name(getVoyagerOperatorName())
                .namespace(getVoyagerName()));
  }

  private V1beta1ClusterRole getActualVoyagerAppsCodeEditClusterRole() {
    return getVoyagerOperatorSecurityYaml().getVoyagerAppsCodeEditClusterRole();
  }

  private V1beta1ClusterRole getExpectedVoyagerAppsCodeEditClusterRole() {
    return newClusterRole()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name("appscode:voyager:edit")
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem("rbac.authorization.k8s.io/aggregate-to-admin", "true")
                .putLabelsItem("rbac.authorization.k8s.io/aggregate-to-edit", "true"))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("voyager.appscode.com")
                .addResourcesItem("certificates")
                .addResourcesItem("ingresses")
                .addVerbsItem("create")
                .addVerbsItem("delete")
                .addVerbsItem("deletecollection")
                .addVerbsItem("get")
                .addVerbsItem("list")
                .addVerbsItem("patch")
                .addVerbsItem("update")
                .addVerbsItem("watch"));
  }

  private V1beta1ClusterRole getActualVoyagerAppsCodeViewClusterRole() {
    return getVoyagerOperatorSecurityYaml().getVoyagerAppsCodeViewClusterRole();
  }

  private V1beta1ClusterRole getExpectedVoyagerAppsCodeViewClusterRole() {
    return newClusterRole()
        .apiVersion(API_VERSION_RBAC_V1)
        .metadata(
            newObjectMeta()
                .name("appscode:voyager:view")
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem("rbac.authorization.k8s.io/aggregate-to-view", "true"))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("voyager.appscode.com")
                .addResourcesItem("certificates")
                .addResourcesItem("ingresses")
                .addVerbsItem("get")
                .addVerbsItem("list")
                .addVerbsItem("watch"));
  }

  private V1beta1Ingress getActualVoyagerIngress() {
    return getVoyagerIngressYaml().getVoyagerIngress();
  }

  private V1beta1Ingress getExpectedVoyagerIngress() {
    return newIngress()
        .apiVersion(API_VERSION_VOYAGER_V1BETA1)
        .metadata(
            newObjectMeta()
                .name(getVoyagerIngressName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName())
                .putAnnotationsItem("ingress.appscode.com/type", "NodePort")
                .putAnnotationsItem("ingress.appscode.com/stats", "true")
                .putAnnotationsItem("ingress.appscode.com/affinity", "cookie"))
        .spec(
            newIngressSpec()
                .addRulesItem(
                    newIngressRule()
                        .host("*")
                        .http(
                            newHTTPIngressRuleValue()
                                .nodePort(newIntOrString(getInputs().getLoadBalancerWebPort()))
                                .addPathsItem(
                                    newHTTPIngressPath()
                                        .backend(
                                            newHTTPIngressBackend()
                                                .serviceName(
                                                    getInputs().getDomainUID()
                                                        + "-cluster-"
                                                        + getClusterNameLC())
                                                .servicePort(
                                                    newIntOrString(
                                                        getInputs().getManagedServerPort())))))));
  }

  private V1Service getActualVoyagerIngressService() {
    return getVoyagerIngressYaml().getVoyagerIngressService();
  }

  private V1Service getExpectedVoyagerIngressService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name(getVoyagerIngressServiceName())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(APP_LABEL, getVoyagerIngressServiceName())
                .putLabelsItem(RESOURCE_VERSION_LABEL, VOYAGER_LOAD_BALANCER_V1)
                .putLabelsItem(DOMAINUID_LABEL, getInputs().getDomainUID())
                .putLabelsItem(DOMAINNAME_LABEL, getInputs().getDomainName()))
        .spec(
            newServiceSpec()
                .type("NodePort")
                .addPortsItem(
                    newServicePort()
                        .name("client")
                        .protocol("TCP")
                        .port(56789)
                        .targetPort(newIntOrString(56789))
                        .nodePort(Integer.valueOf(getInputs().getLoadBalancerDashboardPort())))
                .putSelectorItem("origin", getVoyagerName())
                .putSelectorItem("origin-name", getVoyagerIngressName()));
  }

  private String getVoyagerIngressName() {
    return getInputs().getDomainUID() + "-" + getVoyagerName();
  }

  private String getVoyagerIngressServiceName() {
    return getVoyagerIngressName() + "-stats";
  }
}
