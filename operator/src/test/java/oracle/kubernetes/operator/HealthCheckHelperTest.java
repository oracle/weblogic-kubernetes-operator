// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.RbacAuthorizationV1beta1Api;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1PolicyRule;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1beta1RoleRef;
import io.kubernetes.client.models.V1beta1Subject;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.create.CreateOperatorInputs;
import oracle.kubernetes.operator.create.OperatorFiles;
import oracle.kubernetes.operator.create.ParsedWeblogicOperatorSecurityYaml;
import oracle.kubernetes.operator.create.UserProjects;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ClientFactory;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper.KubernetesVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFormatter;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.ContainerResolver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.create.CreateOperatorInputs.readDefaultInputsFile;
import static oracle.kubernetes.operator.create.ExecCreateOperator.execCreateOperator;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.*;

public class HealthCheckHelperTest {

  private KubernetesVersion version;
  private HealthCheckHelper unitHealthCheckHelper;

  private final static String UNIT_NAMESPACE = "unit-test-namespace";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static ByteArrayOutputStream bos = new ByteArrayOutputStream();
  private static Handler hdlr = new StreamHandler(bos, new LoggingFormatter());
  private List<Handler> savedHandlers = new ArrayList<>();
  
  private UserProjects userProjects;

  @Before
  public void setUp() throws Exception {
    savedHandlers = TestUtils.removeConsoleHandlers(LOGGER.getUnderlyingLogger());
    LOGGER.getUnderlyingLogger().addHandler(hdlr);

    if (!TestUtils.isKubernetesAvailable()) return;

    ContainerResolver.getInstance().getContainer().getComponents().remove(
        ProcessingConstants.MAIN_COMPONENT_NAME);
    ClientPool.getInstance().drain();

    createNamespace(UNIT_NAMESPACE);

    unitHealthCheckHelper = new HealthCheckHelper(UNIT_NAMESPACE, Collections.singleton(UNIT_NAMESPACE));

    userProjects = UserProjects.createUserProjectsDirectory();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.restoreConsoleHandlers(LOGGER.getUnderlyingLogger(), savedHandlers);
    LOGGER.getUnderlyingLogger().removeHandler(hdlr);

    // Delete anything we created
    if (userProjects != null) {
      userProjects.remove();
    }
  }

  @Test
  public void testAccountNoPrivs() throws Exception {
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());
    
    // Create service account
    ApiClient apiClient= Config.defaultClient();
    CoreV1Api core = new CoreV1Api(apiClient);
    V1ServiceAccount alice = new V1ServiceAccount();
    alice.setMetadata(new V1ObjectMeta().name("alice"));
    try {
      alice = core.createNamespacedServiceAccount(UNIT_NAMESPACE, alice, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
    }
    alice = core.readNamespacedServiceAccount("alice", UNIT_NAMESPACE, "false", false, false);
    
    /* If we need to authenticate as sa rather than just impersonate
    String secretName = alice.getSecrets().get(0).getName();
    V1Secret secret = core.readNamespacedSecret(secretName, UNIT_NAMESPACE, "false", false, false);
    String token = new String(secret.getData().get("token"), StandardCharsets.UTF_8);
    */
    
    applyMinimumSecurity(apiClient, UNIT_NAMESPACE, "alice");

    ContainerResolver.getInstance().getContainer().getComponents().put(
        ProcessingConstants.MAIN_COMPONENT_NAME,
        Component.createFor(
            ClientFactory.class, new ClientFactory() {
              @Override
              public ApiClient get() {
                try {
                  //return ClientBuilder.standard().setAuthentication(
                  //    new AccessTokenAuthentication(token)).build();
                  ApiClient client = ClientBuilder.standard().build();
                  client.addDefaultHeader("Impersonate-User", "system:serviceaccount:unit-test-namespace:alice");
                  return client;
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            },
            new CallBuilderFactory(null)));
    
    ClientPool.getInstance().drain();
    
    version = unitHealthCheckHelper.performK8sVersionCheck();
    unitHealthCheckHelper.performSecurityChecks(version);
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertTrue("Log output did not contain Access Denied error: " + logOutput,
        logOutput.contains("Access denied"));
    bos.reset();
  }

  @Test
  public void testAccountPrivs() throws Exception {
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());
    
    // Create service account
    ApiClient apiClient= Config.defaultClient();
    CoreV1Api core = new CoreV1Api(apiClient);
    V1ServiceAccount theo = new V1ServiceAccount();
    theo.setMetadata(new V1ObjectMeta().name("theo"));
    try {
      theo = core.createNamespacedServiceAccount(UNIT_NAMESPACE, theo, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
    }
    theo = core.readNamespacedServiceAccount("theo", UNIT_NAMESPACE, "false", false, false);
    
    applySecurity(apiClient, UNIT_NAMESPACE, "theo");
    
    ContainerResolver.getInstance().getContainer().getComponents().put(
        ProcessingConstants.MAIN_COMPONENT_NAME,
        Component.createFor(
            ClientFactory.class, new ClientFactory() {
              @Override
              public ApiClient get() {
                try {
                  //return ClientBuilder.standard().setAuthentication(
                  //    new AccessTokenAuthentication(token)).build();
                  ApiClient client = ClientBuilder.standard().build();
                  client.addDefaultHeader("Impersonate-User", "system:serviceaccount:unit-test-namespace:theo");
                  return client;
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            },
            new CallBuilderFactory(null)));
    
    ClientPool.getInstance().drain();
    
    version = unitHealthCheckHelper.performK8sVersionCheck();
    unitHealthCheckHelper.performSecurityChecks(version);
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertFalse("Log output must not contain Access Denied error: " + logOutput,
        logOutput.contains("Access denied"));
    bos.reset();
  }
  
  private void applyMinimumSecurity(ApiClient apiClient, String namespace, String sa) throws Exception {
    V1beta1ClusterRole clusterRole = new V1beta1ClusterRole();
    clusterRole.setMetadata(new V1ObjectMeta()
        .name("test-role")
        .putLabelsItem("weblogic.operatorName", namespace));
    clusterRole.addRulesItem(new V1beta1PolicyRule().addNonResourceURLsItem("/version/*").addVerbsItem("get"));
    clusterRole.addRulesItem(new V1beta1PolicyRule().addResourcesItem("selfsubjectrulesreviews")
        .addApiGroupsItem("authorization.k8s.io").addVerbsItem("create"));
    V1beta1ClusterRoleBinding clusterRoleBinding = new V1beta1ClusterRoleBinding();
    clusterRoleBinding.setMetadata(new V1ObjectMeta()
        .name(namespace + "-test-role")
        .putLabelsItem("weblogic.operatorName", namespace));
    clusterRoleBinding.addSubjectsItem(new V1beta1Subject()
        .kind("ServiceAccount")
        .apiGroup("")
        .name(sa).namespace(namespace));
    clusterRoleBinding.roleRef(new V1beta1RoleRef()
        .kind("ClusterRole")
        .apiGroup("rbac.authorization.k8s.io")
        .name("test-role"));
    RbacAuthorizationV1beta1Api rbac = new RbacAuthorizationV1beta1Api(apiClient);

    try {
      rbac.createClusterRole(clusterRole, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRole(clusterRole.getMetadata().getName(), clusterRole, "false");
    }
    try {
      rbac.createClusterRoleBinding(clusterRoleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), clusterRoleBinding, "false");
    }
  }
  
  private void applySecurity(ApiClient apiClient, String namespace, String sa) throws Exception {
    CreateOperatorInputs inputs = readDefaultInputsFile()
        .namespace(namespace)
        .targetNamespaces(namespace)
        .serviceAccount(sa);
    
    OperatorFiles operatorFiles = new OperatorFiles(userProjects.getPath(), inputs);
    assertThat(execCreateOperator(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
    ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml =
      new ParsedWeblogicOperatorSecurityYaml(operatorFiles.getWeblogicOperatorSecurityYamlPath(), inputs);
    
    // apply roles and bindings
    V1beta1ClusterRole clusterRole;
    V1beta1ClusterRoleBinding clusterRoleBinding;
    V1beta1RoleBinding roleBinding;
    
    RbacAuthorizationV1beta1Api rbac = new RbacAuthorizationV1beta1Api(apiClient);
    
    clusterRole = weblogicOperatorSecurityYaml.getWeblogicOperatorClusterRole();
    try {
      rbac.createClusterRole(clusterRole, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRole(clusterRole.getMetadata().getName(), clusterRole, "false");
    }
    clusterRole = weblogicOperatorSecurityYaml.getWeblogicOperatorClusterRoleNonResource();
    try {
      rbac.createClusterRole(clusterRole, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRole(clusterRole.getMetadata().getName(), clusterRole, "false");
    }
    clusterRole = weblogicOperatorSecurityYaml.getWeblogicOperatorNamespaceRole();
    try {
      rbac.createClusterRole(clusterRole, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRole(clusterRole.getMetadata().getName(), clusterRole, "false");
    }
    
    clusterRoleBinding = weblogicOperatorSecurityYaml.getOperatorRoleBinding();
    try {
      rbac.createClusterRoleBinding(clusterRoleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), clusterRoleBinding, "false");
    }
    clusterRoleBinding = weblogicOperatorSecurityYaml.getOperatorRoleBindingNonResource();
    try {
      rbac.createClusterRoleBinding(clusterRoleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), clusterRoleBinding, "false");
    }
    clusterRoleBinding = weblogicOperatorSecurityYaml.getOperatorRoleBindingDiscovery();
    try {
      rbac.createClusterRoleBinding(clusterRoleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), clusterRoleBinding, "false");
    }
    clusterRoleBinding = weblogicOperatorSecurityYaml.getOperatorRoleBindingAuthDelegator();
    try {
      rbac.createClusterRoleBinding(clusterRoleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), clusterRoleBinding, "false");
    }
    
    roleBinding = weblogicOperatorSecurityYaml.getWeblogicOperatorRoleBinding(namespace);
    try {
      rbac.createNamespacedRoleBinding(namespace, roleBinding, "false");
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.CONFLICT) {
        throw api;
      }
      rbac.replaceNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace, roleBinding, "false");
    }
  }

  // Create a named namespace
  private V1Namespace createNamespace(String name) throws Exception {
    CallBuilderFactory factory = new CallBuilderFactory(null);
    try {
      V1Namespace existing = factory.create().readNamespace(name);
      if (existing != null)
        return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    V1Namespace body = new V1Namespace();

    // Set the required api version and kind of resource
    body.setApiVersion("v1");
    body.setKind("Namespace");

    // Setup the standard object metadata
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    body.setMetadata(meta);

    return factory.create().createNamespace(body);
  }
}
