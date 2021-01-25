// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

public class HealthCheckHelperTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
      DOMAIN_UID_UNIQUENESS_FAILED,
      PV_ACCESS_MODE_FAILED,
      PV_NOT_FOUND_FOR_DOMAIN_UID,
      VERIFY_ACCESS_DENIED,
      VERIFY_ACCESS_DENIED_WITH_NS
  };

  private static final String NS1 = "ns1";
  private static final String NS2 = "ns2";
  private static final String OPERATOR_NAMESPACE = "op1";
  private static final List<String> TARGET_NAMESPACES = Arrays.asList(NS1, NS2);
  private static final List<String> CRUD_RESOURCES =
      Arrays.asList(
          "configmaps",
          "events",
          "jobs//batch",
          "pods",
          "services");

  private static final List<String> CLUSTER_CRUD_RESOURCES =
        singletonList("customresourcedefinitions//apiextensions.k8s.io");

  private static final List<String> CLUSTER_READ_WATCH_RESOURCES =
        singletonList("namespaces");

  private static final List<String> CLUSTER_READ_UPDATE_RESOURCES =
      Arrays.asList("domains//weblogic.oracle", "domains/status/weblogic.oracle");

  private static final List<String> CREATE_AND_GET_RESOURCES =
      Arrays.asList("pods/exec", "tokenreviews//authentication.k8s.io",
          "selfsubjectrulesreviews//authorization.k8s.io");

  private static final List<String> READ_WATCH_RESOURCES =
        singletonList("secrets");

  private static final List<Operation> CRUD_OPERATIONS =
      Arrays.asList(get, list, watch, create, update, patch, delete, deletecollection);

  private static final List<Operation> CRD_OPERATIONS =
      Arrays.asList(get, list, watch, create, update, patch);

  private static final List<Operation> READ_ONLY_OPERATIONS = Arrays.asList(get, list);

  private static final List<Operation> READ_WATCH_OPERATIONS = Arrays.asList(get, list, watch);

  private static final List<Operation> READ_UPDATE_OPERATIONS =
      Arrays.asList(get, list, watch, update, patch);

  private static final List<Operation> CREATE_GET_OPERATIONS =
      Arrays.asList(create, get);

  private static final String POD_LOGS = "pods/log";

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final CallTestSupport testSupport = new CallTestSupport();
  private final AccessChecks accessChecks = new AccessChecks();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
    mementos.add(TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS));
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.installSynchronousCallDispatcher());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenRulesReviewSupported_accessGrantedForEverything() {
    expectSelfSubjectRulesReview();

    for (String ns : TARGET_NAMESPACES) {
      V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(ns);
      HealthCheckHelper.verifyAccess(status, ns, true);
    }
  }

  @Test
  public void whenRulesReviewSupportedAndNoDomainNamespaceAccess_logWarning() {
    accessChecks.setMayAccessNamespace(false);
    expectSelfSubjectRulesReview();

    for (String ns : TARGET_NAMESPACES) {
      V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(ns);
      HealthCheckHelper.verifyAccess(status, ns, true);
    }

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  // HERE

  @Test
  public void whenRulesReviewSupportedAndNoOperatorNamespaceAccess_logWarning() {
    accessChecks.setMayAccessNamespace(false);
    expectSelfSubjectRulesReview();

    V1SubjectRulesReviewStatus status = HealthCheckHelper.getSelfSubjectRulesReviewStatus(OPERATOR_NAMESPACE);
    HealthCheckHelper.verifyAccess(status, OPERATOR_NAMESPACE, false);

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED_WITH_NS));
  }

  private void expectSelfSubjectRulesReview() {
    testSupport
        .createCannedResponse("createSelfSubjectRulesReview")
        .ignoringBody()
        .returning(new V1SelfSubjectRulesReview().status(accessChecks.createRulesStatus()));
  }

  @SuppressWarnings("SameParameterValue")
  static class AccessChecks {
    private static final boolean MAY_ACCESS_CLUSTER = true;

    private boolean mayAccessNamespace = true;

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

    void setMayAccessNamespace(boolean mayAccessNamespace) {
      this.mayAccessNamespace = mayAccessNamespace;
    }

    private V1SubjectRulesReviewStatus createRulesStatus() {
      return new V1SubjectRulesReviewStatus().resourceRules(createRules());
    }

    private List<V1ResourceRule> createRules() {
      List<V1ResourceRule> rules = new ArrayList<>();
      if (mayAccessNamespace) {
        addNamespaceRules(rules);
      }
      if (MAY_ACCESS_CLUSTER) {
        addClusterRules(rules);
      }
      return rules;
    }

    private void addNamespaceRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CRUD_RESOURCES, CRUD_OPERATIONS));
      rules.add(createRule(READ_WATCH_RESOURCES, READ_WATCH_OPERATIONS));
      rules.add(createRule(singletonList(POD_LOGS), READ_ONLY_OPERATIONS));
      rules.add(createRule(CREATE_AND_GET_RESOURCES, CREATE_GET_OPERATIONS));
    }

    private void addClusterRules(List<V1ResourceRule> rules) {
      rules.add(createRule(CLUSTER_CRUD_RESOURCES, CRD_OPERATIONS));
      rules.add(createRule(CLUSTER_READ_UPDATE_RESOURCES, READ_UPDATE_OPERATIONS));
      rules.add(createRule(CLUSTER_READ_WATCH_RESOURCES, READ_WATCH_OPERATIONS));
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
  }
}