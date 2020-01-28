// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ResourceAttributes;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.create;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.delete;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.deletecollection;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.get;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.list;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.patch;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.update;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.watch;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.PV_ACCESS_MODE_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID;
import static oracle.kubernetes.operator.logging.MessageKeys.VERIFY_ACCESS_DENIED;
import static oracle.kubernetes.operator.logging.MessageKeys.VERIFY_ACCESS_DENIED_WITH_NS;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class HealthCheckHelperTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    DOMAIN_UID_UNIQUENESS_FAILED,
    PV_ACCESS_MODE_FAILED,
    PV_NOT_FOUND_FOR_DOMAIN_UID,
    VERIFY_ACCESS_DENIED,
    VERIFY_ACCESS_DENIED_WITH_NS
  };

  private static final String OPERATOR_NAMESPACE = "operator";
  private static final String NS1 = "ns1";
  private static final String NS2 = "ns2";
  private static final List<String> TARGET_NAMESPACES = Arrays.asList(NS1, NS2);
  private static final List<String> CRUD_RESOURCES =
      Arrays.asList(
          "configmaps",
          "cronjobs//batch",
          "events",
          "jobs//batch",
          "networkpolicies//extensions",
          "persistentvolumeclaims",
          "pods",
          "podpresets//settings.k8s.io",
          "podsecuritypolicies//extensions",
          "podtemplates",
          "ingresses//extensions",
          "services");

  private static final List<String> CLUSTER_CRUD_RESOURCES =
      Arrays.asList("customresourcedefinitions//apiextensions.k8s.io", "persistentvolumes");

  private static final List<String> CREATE_ONLY_RESOURCES =
      Arrays.asList("pods/exec", "tokenreviews//authentication.k8s.io");

  private static final List<String> READ_WATCH_RESOURCES =
      Arrays.asList("secrets", "storageclasses//storage.k8s.io");

  private static final List<Operation> CRUD_OPERATIONS =
      Arrays.asList(get, list, watch, create, update, patch, delete, deletecollection);

  private static final List<Operation> READ_ONLY_OPERATIONS = Arrays.asList(get, list);

  private static final List<Operation> READ_WATCH_OPERATIONS = Arrays.asList(get, list, watch);

  private static final List<Operation> READ_UPDATE_OPERATIONS =
      Arrays.asList(get, list, watch, update, patch);

  private static final String POD_LOGS = "pods/log";
  private static final String DOMAINS = "domains//weblogic.oracle";
  private static final String NAMESPACES = "namespaces";
  private static final KubernetesVersion MINIMAL_KUBERNETES_VERSION = new KubernetesVersion(1, 7);
  private static final KubernetesVersion RULES_REVIEW_VERSION = new KubernetesVersion(1, 8);

  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private AccessChecks accessChecks = new AccessChecks();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger()
          .ignoringLoggedExceptions(ApiException.class)
          .collectLogMessages(logRecords, LOG_KEYS));
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.install());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  @Ignore
  public void whenRulesReviewNotSupported_requestsAccessReviewForEverything() {
    expectAccessChecks();

    for (String ns : TARGET_NAMESPACES) {
      HealthCheckHelper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION, OPERATOR_NAMESPACE, ns);
    }

    assertThat(accessChecks.getExpectedAccessChecks(), empty());
  }

  private void expectAccessChecks() {
    TARGET_NAMESPACES.forEach(this::expectAccessReviewsByNamespace);
    expectClusterAccessChecks();
  }

  @Test
  public void whenRulesReviewNotSupportedAndNoNamespaceAccess_logWarning() {
    expectAccessChecks();
    accessChecks.setMayAccessNamespace(false);
    populateSelfSubjectAccessReviewOnCreate();

    for (String ns : TARGET_NAMESPACES) {
      HealthCheckHelper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION, OPERATOR_NAMESPACE, ns);
    }

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED));
    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  void populateSelfSubjectAccessReviewOnCreate() {
    testSupport.doOnCreate(KubernetesTestSupport.SELF_SUBJECT_ACCESS_REVIEW,
        (V1SelfSubjectAccessReview r) -> accessChecks.populateSubjectAccessReview(r));
  }

  @Test
  public void whenRulesReviewNotSupportedAndNoClusterAccess_logWarning() {
    expectAccessChecks();
    accessChecks.setMayAccessCluster(false);
    populateSelfSubjectAccessReviewOnCreate();

    for (String ns : TARGET_NAMESPACES) {
      HealthCheckHelper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION, OPERATOR_NAMESPACE, ns);
    }

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED));
    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  @Test
  public void whenRulesReviewSupported_accessGrantedForEverything() {
    populateSelfSubjectRulesReviewOnCreate();

    for (String ns : TARGET_NAMESPACES) {
      HealthCheckHelper.performSecurityChecks(RULES_REVIEW_VERSION, OPERATOR_NAMESPACE, ns);
    }
  }

  @Test
  public void whenRulesReviewSupportedAndNoNamespaceAccess_logWarning() {
    accessChecks.setMayAccessNamespace(false);
    populateSelfSubjectRulesReviewOnCreate();

    for (String ns : TARGET_NAMESPACES) {
      HealthCheckHelper.performSecurityChecks(RULES_REVIEW_VERSION, OPERATOR_NAMESPACE, ns);
    }

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  @Test
  public void whenRulesReviewFailsAndMayNotAccessSpecifiedResource_dontAllowClusterAccess() {
    expectAccessChecks();
    accessChecks.setMayAccessCluster(false);
    testSupport.failOnCreate(KubernetesTestSupport.SELF_SUBJECT_RULES_REVIEW, null, null, 500);
    populateSelfSubjectAccessReviewOnCreate();

    assertThat(
          HealthCheckHelper.isClusterResourceAccessAllowed(RULES_REVIEW_VERSION, Resource.CRDS, Operation.get),
          is(false));
  }

  @Test
  public void whenRulesReviewFailsAndMayAccessSpecifiedResource_allowClusterAccess() {
    expectAccessChecks();
    accessChecks.setMayAccessCluster(true);
    testSupport.failOnCreate(KubernetesTestSupport.SELF_SUBJECT_RULES_REVIEW, null, null, 500);
    populateSelfSubjectAccessReviewOnCreate();

    assertThat(
          HealthCheckHelper.isClusterResourceAccessAllowed(RULES_REVIEW_VERSION, Resource.CRDS, Operation.get),
          is(true));
  }

  @Test
  public void whenRulesReviewPermitsAccess_allowClusterAccess() {
    populateSelfSubjectRulesReviewOnCreate();

    assertThat(
          HealthCheckHelper.isClusterResourceAccessAllowed(RULES_REVIEW_VERSION, Resource.CRDS, Operation.get),
          is(true));
  }

  @Test
  public void whenRulesReviewDoNotAccess_dontAllowClusterAccess() {
    accessChecks.setMayAccessCluster(false);
    populateSelfSubjectRulesReviewOnCreate();

    assertThat(
          HealthCheckHelper.isClusterResourceAccessAllowed(RULES_REVIEW_VERSION, Resource.CRDS, Operation.get),
          is(false));
  }

  private void populateSelfSubjectRulesReviewOnCreate() {
    testSupport.doOnCreate(KubernetesTestSupport.SELF_SUBJECT_RULES_REVIEW,
        (V1SelfSubjectRulesReview r) -> accessChecks.populateSubjectRulesReview(r));
  }

  private void expectAccessReviewsByNamespace(String namespace) {
    CRUD_RESOURCES.forEach(resource -> expectCrudAccessChecks(namespace, resource));
    READ_WATCH_RESOURCES.forEach(resource -> expectReadWatchAccessChecks(namespace, resource));

    READ_ONLY_OPERATIONS.forEach(operation -> expectAccessCheck(namespace, POD_LOGS, operation));
    CREATE_ONLY_RESOURCES.forEach(resource -> expectAccessCheck(namespace, resource, create));
  }

  private void expectCrudAccessChecks(String namespace, String resource) {
    CRUD_OPERATIONS.forEach(operation -> expectAccessCheck(namespace, resource, operation));
  }

  private void expectReadWatchAccessChecks(String namespace, String resource) {
    READ_WATCH_OPERATIONS.forEach(operation -> expectAccessCheck(namespace, resource, operation));
  }

  private void expectClusterAccessChecks() {
    CLUSTER_CRUD_RESOURCES.forEach(this::expectClusterCrudAccessChecks);
    READ_UPDATE_OPERATIONS.forEach(operation -> expectClusterAccessCheck(DOMAINS, operation));
    READ_WATCH_OPERATIONS.forEach(operation -> expectClusterAccessCheck(NAMESPACES, operation));
  }

  private void expectClusterCrudAccessChecks(String resource) {
    CRUD_OPERATIONS.forEach(operation -> expectClusterAccessCheck(resource, operation));
  }

  private void expectClusterAccessCheck(String resource, Operation operation) {
    expectAccessCheck(null, resource, operation);
  }

  private void expectAccessCheck(String namespace, String resource, Operation operation) {
    accessChecks.expectAccessCheck(namespace, resource, operation);
  }

  @SuppressWarnings("SameParameterValue")
  static class AccessChecks {

    private List<V1ResourceAttributes> expectedAccessChecks = new ArrayList<>();
    private boolean mayAccessNamespace = true;
    private boolean mayAccessCluster = true;

    private static V1ResourceAttributes createResourceAttributes(
        String namespace, String resource, Operation operation) {
      return new V1ResourceAttributes()
          .verb(operation.toString())
          .resource(getResource(resource))
          .subresource(getSubresource(resource))
          .group(getApiGroup(resource))
          .namespace(namespace);
    }

    private static String getResource(String resourceString) {
      return resourceString.split("/")[0];
    }

    private static String getSubresource(String resourceString) {
      String[] split = resourceString.split("/");
      return split.length <= 1 ? "" : split[1];
    }

    private static String getApiGroup(String resourceString) {
      String[] split = resourceString.split("/");
      return split.length <= 2 ? "" : split[2];
    }

    private void expectAccessCheck(String namespace, String resource, Operation operation) {
      this.expectedAccessChecks.add(createResourceAttributes(namespace, resource, operation));
    }

    void setMayAccessNamespace(boolean mayAccessNamespace) {
      this.mayAccessNamespace = mayAccessNamespace;
    }

    void setMayAccessCluster(boolean mayAccessCluster) {
      this.mayAccessCluster = mayAccessCluster;
    }

    List<V1ResourceAttributes> getExpectedAccessChecks() {
      return Collections.unmodifiableList(expectedAccessChecks);
    }

    private boolean isAllowedByDefault(V1ResourceAttributes resourceAttributes) {
      return resourceAttributes.getNamespace() == null ? mayAccessCluster : mayAccessNamespace;
    }

    private void populateSubjectRulesReview(V1SelfSubjectRulesReview review) {
      review.status(createRulesStatus());
    }

    private V1SubjectRulesReviewStatus createRulesStatus() {
      return new V1SubjectRulesReviewStatus().resourceRules(createRules());
    }

    private List<V1ResourceRule> createRules() {
      List<V1ResourceRule> rules = new ArrayList<>();
      if (mayAccessNamespace) addNamespaceRules(rules);
      if (mayAccessCluster) addClusterRules(rules);
      return rules;
    }

    private void addNamespaceRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CRUD_RESOURCES, CRUD_OPERATIONS));
      rules.add(createRule(READ_WATCH_RESOURCES, READ_WATCH_OPERATIONS));
      rules.add(createRule(singletonList(POD_LOGS), READ_ONLY_OPERATIONS));
      rules.add(createRule(CREATE_ONLY_RESOURCES, singletonList(create)));
    }

    private void addClusterRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CLUSTER_CRUD_RESOURCES, CRUD_OPERATIONS));
      rules.add(createRule(singletonList(DOMAINS), READ_UPDATE_OPERATIONS));
      rules.add(createRule(singletonList(NAMESPACES), READ_WATCH_OPERATIONS));
    }

    private V1ResourceRule createRule(List<String> resourceStrings, List<Operation> operations) {
      return new V1ResourceRule()
          .apiGroups(getApiGroups(resourceStrings))
          .resources(getResources(resourceStrings))
          .verbs(toVerbs(operations));
    }

    private List<String> getApiGroups(List<String> resourceStrings) {
      return resourceStrings.stream()
          .map(AccessChecks::getApiGroup)
          .distinct()
          .collect(Collectors.toList());
    }

    private List<String> getResources(List<String> resourceStrings) {
      return resourceStrings.stream().map(this::getFullResource).collect(Collectors.toList());
    }

    private String getFullResource(String resourceString) {
      String resource = getResource(resourceString);
      String subresource = getSubresource(resourceString);
      return subresource.length() == 0 ? resource : resource + "/" + subresource;
    }

    private List<String> toVerbs(List<Operation> operations) {
      return operations.stream().map(Enum::name).collect(Collectors.toList());
    }

    private boolean isResourceCheckAllowed(V1SelfSubjectAccessReview body) {
      V1ResourceAttributes resourceAttributes = body.getSpec().getResourceAttributes();
      return isAllowedByDefault(resourceAttributes)
          && expectedAccessChecks.remove(resourceAttributes);
    }

    private V1SelfSubjectAccessReview populateSubjectAccessReview(V1SelfSubjectAccessReview body) {
      body.setStatus(new V1SubjectAccessReviewStatus().allowed(isResourceCheckAllowed(body)));
      return body;
    }
  }
}
