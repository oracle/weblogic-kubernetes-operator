// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ServiceAccount;

/**
 * Parses a generated weblogic-operator-security.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorSecurityYaml {

  public V1Namespace operatorNamespace;
  public V1ServiceAccount operatorServiceAccount;
  public V1beta1ClusterRole weblogicOperatorClusterRole;
  public V1beta1ClusterRole weblogicOperatorClusterRoleNonResource;
  public V1beta1ClusterRoleBinding operatorRoleBinding;
  public V1beta1ClusterRoleBinding operatorRoleBindingNonResource;
  public V1beta1ClusterRoleBinding operatorRoleBindingDiscovery;
  public V1beta1ClusterRoleBinding operatorRoleBindingAuthDelegator;
  public V1beta1ClusterRole weblogicOperatorNamespaceRole;
  public V1beta1RoleBinding weblogicOperatorRoleBinding;

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    ParsedKubernetesYaml parsed = new ParsedKubernetesYaml(yamlPath);
    int count = 0;

    operatorNamespace = parsed.getNamespace(inputs.namespace);
    assertThat(operatorNamespace, notNullValue());
    count++;

    operatorServiceAccount = parsed.getServiceAccount(inputs.serviceAccount);
    assertThat(operatorServiceAccount, notNullValue());
    count++;

    weblogicOperatorClusterRole = parsed.getClusterRole("weblogic-operator-cluster-role");
    assertThat(weblogicOperatorClusterRole, notNullValue());
    count++;

    weblogicOperatorClusterRoleNonResource = parsed.getClusterRole("weblogic-operator-cluster-role-nonresource");
    assertThat(weblogicOperatorClusterRoleNonResource, notNullValue());
    count++;

    operatorRoleBinding = parsed.getClusterRoleBinding(inputs.namespace + "-operator-rolebinding");
    assertThat(operatorRoleBinding, notNullValue());
    count++;

    operatorRoleBindingNonResource = parsed.getClusterRoleBinding(inputs.namespace + "-operator-rolebinding-nonresource");
    assertThat(operatorRoleBindingNonResource, notNullValue());
    count++;

    operatorRoleBindingDiscovery = parsed.getClusterRoleBinding(inputs.namespace + "-operator-rolebinding-discovery");
    assertThat(operatorRoleBindingDiscovery, notNullValue());
    count++;

    operatorRoleBindingAuthDelegator = parsed.getClusterRoleBinding(inputs.namespace + "-operator-rolebinding-auth-delegator");
    assertThat(operatorRoleBindingAuthDelegator, notNullValue());
    count++;

    weblogicOperatorNamespaceRole = parsed.getClusterRole("weblogic-operator-namespace-role");
    assertThat(weblogicOperatorNamespaceRole, notNullValue());
    count++;

    weblogicOperatorRoleBinding = parsed.getRoleBinding("weblogic-operator-rolebinding");
    assertThat(weblogicOperatorRoleBinding, notNullValue());
    count++;

    assertThat(count, is(parsed.getInstanceCount()));
  }
}

