// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.util.List;
import static java.util.Arrays.asList;

import io.kubernetes.client.models.V1Service;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-operator.sh
 * creates are correct when external rest is none, the remote debug port is disabled,
 * elk is disabled and there is no image pull secret.
 */
public class CreateOperatorGeneratedFilesExtRestNoneDebugOffTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs(); // defaults to external rest none, debug off
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorConfigMap() throws Exception {
    assertThat(
      weblogicOperatorYaml().getOperatorConfigMap(),
      equalTo(weblogicOperatorYaml().getExpectedOperatorConfigMap("")) // no external operator cert
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorSecrets() throws Exception {
    ParsedWeblogicOperatorYaml.assertThat_secretsAreEqual(
      weblogicOperatorYaml().getOperatorSecrets(),
      weblogicOperatorYaml().getExpectedOperatorSecrets("") // no external operator key
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorDeployment() throws Exception {
    assertThat(
      weblogicOperatorYaml().getOperatorDeployment(),
      equalTo(weblogicOperatorYaml().getBaseExpectedOperatorDeployment())
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_externalOperatorService() throws Exception {
    assertThat(weblogicOperatorYaml().getExternalOperatorService(), nullValue());
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_internalOperatorService() throws Exception {
    V1Service want =
      newService("internal-weblogic-operator-service", inputs.getNamespace());
    want.getSpec()
      .type("ClusterIP")
      .putSelectorItem("app", "weblogic-operator")
      .addPortsItem(newServicePort("rest-https").port(8082));
    assertThat(
      weblogicOperatorYaml().getInternalOperatorService(),
      equalTo(want)
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorNamespace() throws Exception {
    String name = inputs.getNamespace();
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorNamespace(),
      equalTo(newNamespace(name))
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorServiceAccount() throws Exception {
    String name = inputs.getServiceAccount();
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorServiceAccount(),
      equalTo(newServiceAccount(name, inputs.getNamespace()))
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRole() throws Exception {
    String name = "weblogic-operator-cluster-role";
    assertThat(
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRole(),
      equalTo(
        newClusterRole(name)
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("")
            .resources(asList("namespaces", "persistentvolumes"))
            .verbs(asList("get", "list", "watch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("apiextensions.k8s.io")
            .addResourcesItem("customresourcedefinitions")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains")
            .verbs(asList("get", "list", "watch", "update", "patch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains/status")
            .addVerbsItem("update"))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("extensions")
            .addResourcesItem("ingresses")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))
      )
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRoleNonResource() throws Exception {
    String name = "weblogic-operator-cluster-role-nonresource";
    assertThat(
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRoleNonResource(),
      equalTo(
        newClusterRole(name)
          .addRulesItem(newPolicyRule()
            .addNonResourceURLsItem("/version/*")
            .addVerbsItem("get"))
      )
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBinding() throws Exception {
    String name = inputs.getNamespace() + "-operator-rolebinding";
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorRoleBinding(),
      equalTo(
        newClusterRoleBinding(name)
        .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
        .roleRef(newRoleRef("weblogic-operator-cluster-role", "rbac.authorization.k8s.io"))
      )
    );
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingNonResource() throws Exception {
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingDiscovery() throws Exception {
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingAuthDelegator() throws Exception {
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorNamespaceRole() throws Exception {
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorRoleBinding() throws Exception {
    // TBD
  }

  private ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml() {
    return generatedFiles.getWeblogicOperatorSecurityYaml();
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
