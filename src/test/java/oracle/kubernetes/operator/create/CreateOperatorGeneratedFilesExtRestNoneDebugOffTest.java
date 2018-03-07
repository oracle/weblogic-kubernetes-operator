// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1PolicyRule;
import io.kubernetes.client.models.V1beta1RoleBinding;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    // TBD - rework to new pattern
    weblogicOperatorYaml().assertThatOperatorConfigMapIsCorrect(inputs, ""); // no external operator cert
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorSecrets() throws Exception {
    // TBD - rework to new pattern
    weblogicOperatorYaml().assertThatOperatorSecretsAreCorrect(inputs, ""); // no external operator key
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorDeployment() throws Exception {
    // TBD
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_externalOperatorService() throws Exception {
    // TBD - rework to new pattern
    weblogicOperatorYaml().assertThatExternalOperatorServiceIsCorrect(inputs, false, false);
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_internalOperatorService() throws Exception {
    // TBD - rework to new pattern
    /* Expected yaml:
      apiVersion: v1
      kind: Service
      metadata:
        name: internal-weblogic-operator-service
        namespace: inputs.getNamespace()
      spec:
        type: ClusterIP
        selector:
          app: weblogic-operator
        ports:
          - port: 8082
            name: rest-https
    */
    V1Service service = weblogicOperatorYaml().getInternalOperatorService();
    List<V1ServicePort> ports =
      weblogicOperatorYaml().assertThatServiceExistsThenReturnPorts(
        service,
        "internal-weblogic-operator-service",
        inputs.getNamespace(),
        "ClusterIP"
      );
    assertThat(ports.size(), is(1));
    V1ServicePort port = ports.get(0);
    assertThat(port, notNullValue());
    assertThat(port.getName(), equalTo("rest-https"));
    assertThat(port.getPort(), is(8082));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorNamespace() throws Exception {
    String name = inputs.getNamespace();
    V1Namespace want =
      newNamespace(name);
    V1Namespace have =
      weblogicOperatorSecurityYaml().getOperatorNamespace();
    assertThat(have, equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorServiceAccount() throws Exception {
    String name = inputs.getServiceAccount();
    V1ServiceAccount want =
      newServiceAccount(name, inputs.getNamespace());
    V1ServiceAccount have =
      weblogicOperatorSecurityYaml().getOperatorServiceAccount();
    assertThat(have, equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRole() throws Exception {
    String name = "weblogic-operator-cluster-role";
    V1beta1ClusterRole want =
      newClusterRole(name)
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addApiGroupsItem("")
            .resources(asList("namespaces", "persistentvolumes"))
            .verbs(asList("get", "list", "watch"))
        )
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addApiGroupsItem("apiextensions.k8s.io")
            .addResourcesItem("customresourcedefinitions")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"))
        )
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains")
            .verbs(asList("get", "list", "watch", "update", "patch"))
        )
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains/status")
            .addVerbsItem("update")
        )
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addApiGroupsItem("extensions")
            .addResourcesItem("ingresses")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"))
        );
    V1beta1ClusterRole have =
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRole();
    assertThat(have, equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRoleNonResource() throws Exception {
    String name = "weblogic-operator-cluster-role-nonresource";
    V1beta1ClusterRole want =
      newClusterRole(name)
        .addRulesItem(
          (new V1beta1PolicyRule())
            .addNonResourceURLsItem("/version/*")
            .addVerbsItem("get")
        );

    V1beta1ClusterRole have =
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRoleNonResource();

    assertThat(have, equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBinding() throws Exception {
    String name = inputs.getNamespace() + "-operator-rolebinding";
    V1beta1ClusterRoleBinding want =
      newClusterRoleBinding(name)
        .addSubjectsItem(
          newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), "")
        )
        .roleRef(
          newRoleRef("weblogic-operator-cluster-role", "rbac.authorization.k8s.io")
        );
    V1beta1ClusterRoleBinding have =
      weblogicOperatorSecurityYaml().getOperatorRoleBinding();
    assertThat(have, equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingNonResource() throws Exception {
    assertThat(weblogicOperatorSecurityYaml().getOperatorRoleBindingNonResource(),
               equalTo(newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-nonresource")
                          .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
                          .roleRef(newRoleRef("weblogic-operator-cluster-role-nonresource", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingDiscovery() throws Exception {
    assertThat(weblogicOperatorSecurityYaml().getOperatorRoleBindingDiscovery(),
               equalTo(newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-discovery")
                          .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
                          .roleRef(newRoleRef("system:discovery", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingAuthDelegator() throws Exception {
    assertThat(weblogicOperatorSecurityYaml().getOperatorRoleBindingAuthDelegator(),
               equalTo(newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-auth-delegator")
                          .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
                          .roleRef(newRoleRef("system:auth-delegator", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorNamespaceRole() throws Exception {
    assertThat(weblogicOperatorSecurityYaml().getWeblogicOperatorNamespaceRole(),
               equalTo(new V1beta1ClusterRole()
                            .apiVersion("rbac.authorization.k8s.io/v1beta1")
                            .kind("ClusterRole")
                            .metadata(new V1ObjectMeta().name("weblogic-operator-namespace-role"))
                            .addRulesItem(new V1beta1PolicyRuleBuilder()
                                                 .withResources("secrets", "persistentvolumeclaims")
                                                 .withVerbs("get", "list", "watch")
                                                .create())
                            .addRulesItem(new V1beta1PolicyRuleBuilder()
                                                 .withResources("services", "pods", "networkpolicies")
                                                 .withVerbs("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")
                                                .create())));
  }

  private class V1beta1PolicyRuleBuilder {
    String[] resources = new String[0];
    String[] verbs = new String[0];

    V1beta1PolicyRuleBuilder withResources(String... resources) {
      this.resources = resources;
      return this;
    }

    V1beta1PolicyRuleBuilder withVerbs(String... verbs) {
      this.verbs = verbs;
      return this;
    }

    V1beta1PolicyRule create() {
      V1beta1PolicyRule rule = new V1beta1PolicyRule();
      rule.addApiGroupsItem("");
      for (String resource : resources)
        rule.addResourcesItem(resource);
      for (String verb : verbs)
        rule.addVerbsItem(verb);
      return rule;
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorRoleBinding() throws Exception {
    assertThat(weblogicOperatorSecurityYaml().getWeblogicOperatorRoleBinding(),
               equalTo(new V1beta1RoleBinding()
                          .apiVersion("rbac.authorization.k8s.io/v1beta1")
                          .kind("RoleBinding")
                          .metadata(new V1ObjectMeta().name("weblogic-operator-rolebinding")
                                                      .namespace(lastEntry(inputs.getTargetNamespaces())))
                          .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
                          .roleRef(newRoleRef("weblogic-operator-namespace-role", ""))
               ));
  }


  //TODO - this is a workaround for the fact that the getWeblogicOperatorRoleBinding() method only returns the last
  //TODO - binding created, rather than all of them.
  private String lastEntry(String targetNamespaces) {
    String[] split = targetNamespaces.split(",");
    return split[split.length-1];
  }

  private ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml() {
    return generatedFiles.getWeblogicOperatorSecurityYaml();
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
