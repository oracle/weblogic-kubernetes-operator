// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.util.Collections.singletonList;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.HealthCheckHelperTest.AccessReviewCallFactoryStub.expectAccessCheck;
import static oracle.kubernetes.operator.HealthCheckHelperTest.AccessReviewCallFactoryStub.setMayAccessCluster;
import static oracle.kubernetes.operator.HealthCheckHelperTest.AccessReviewCallFactoryStub.setMayAccessNamespace;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.create;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.delete;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.deletecollection;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.get;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.list;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.patch;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.update;
import static oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation.watch;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_MIN_VERSION_CHECK_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK_FAILURE;
import static oracle.kubernetes.operator.logging.MessageKeys.PV_ACCESS_MODE_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID;
import static oracle.kubernetes.operator.logging.MessageKeys.VERIFY_ACCESS_DENIED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1ResourceAttributes;
import io.kubernetes.client.models.V1ResourceRule;
import io.kubernetes.client.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.models.VersionInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.SynchronousCallFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HealthCheckHelperTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    K8S_MIN_VERSION_CHECK_FAILED,
    K8S_VERSION_CHECK,
    K8S_VERSION_CHECK_FAILURE,
    DOMAIN_UID_UNIQUENESS_FAILED,
    PV_ACCESS_MODE_FAILED,
    PV_NOT_FOUND_FOR_DOMAIN_UID,
    VERIFY_ACCESS_DENIED
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
          "services");

  private static final List<String> CLUSTER_CRUD_RESOURCES =
      Arrays.asList(
          "customresourcedefinitions//apiextensions.k8s.io",
          "ingresses//extensions",
          "persistentvolumes");

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

  private static final String DOMAIN_UID_LABEL = "weblogic.domainUID";
  private static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";
  private static final String POD_LOGS = "pods/logs";
  private static final String DOMAINS = "domains//weblogic.oracle";
  private static final String NAMESPACES = "namespaces";
  private static final HealthCheckHelper.KubernetesVersion MINIMAL_KUBERNETES_VERSION =
      new HealthCheckHelper.KubernetesVersion(1, 7);
  private static final HealthCheckHelper.KubernetesVersion RULES_REVIEW_VERSION =
      new HealthCheckHelper.KubernetesVersion(1, 8);

  private HealthCheckHelper helper = new HealthCheckHelper(OPERATOR_NAMESPACE, TARGET_NAMESPACES);
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  @Before
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS);
    mementos.add(consoleControl);
    mementos.add(ClientFactoryStub.install());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenK8sMajorVersionLessThanOne_returnVersionZeroZero() throws Exception {
    ignoreMessage(K8S_MIN_VERSION_CHECK_FAILED);
    specifyK8sVersion("0", "", "");

    assertThat(helper.performK8sVersionCheck(), returnsVersion(0, 0));
  }

  private void specifyK8sVersion(String majorVersion, String minorVersion, String gitVersion)
      throws NoSuchFieldException {
    VersionCodeCallFactoryStub callFactory =
        VersionCodeCallFactoryStub.create(majorVersion, minorVersion, gitVersion);
    mementos.add(VersionCodeCallFactoryStub.install(callFactory));
  }

  abstract static class VersionCodeCallFactoryStub implements SynchronousCallFactory {
    private final VersionInfo versionInfo;

    VersionCodeCallFactoryStub(String majorVersion, String minorVersion, String gitVersion) {
      versionInfo = setVersionInfo(majorVersion, minorVersion, gitVersion);
    }

    private VersionInfo setVersionInfo(
        String majorVersion, String minorVersion, String gitVersion) {
      VersionInfo versionInfo;
      versionInfo = new VersionInfo().major(majorVersion).minor(minorVersion);
      versionInfo.setGitVersion(majorVersion + "." + minorVersion + "." + gitVersion);
      return versionInfo;
    }

    private static Memento install(VersionCodeCallFactoryStub callFactory)
        throws NoSuchFieldException {
      return StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", callFactory);
    }

    private static VersionCodeCallFactoryStub create(
        String majorVersion, String minorVersion, String gitVersion) {
      return createStrictStub(
          VersionCodeCallFactoryStub.class, majorVersion, minorVersion, gitVersion);
    }

    @Override
    public VersionInfo getVersionCode(ApiClient client) throws ApiException {
      return versionInfo;
    }
  }

  private void ignoreMessage(String message) {
    consoleControl.ignoreMessage(message);
  }

  private Matcher<HealthCheckHelper.KubernetesVersion> returnsVersion(int major, int minor) {
    return equalTo(new HealthCheckHelper.KubernetesVersion(major, minor));
  }

  @Test
  public void whenK8sMajorVersionLessThanOne_warnOfVersionTooLow() throws Exception {
    specifyK8sVersion("0", "", "");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsWarning(K8S_MIN_VERSION_CHECK_FAILED));
  }

  @Test // todo this doesn't seem correct behavior; shouldn't it return (2, 7)?
  public void whenK8sMajorVersionGreaterThanOne_returnVersionTwoZero() throws Exception {
    ignoreMessage(K8S_VERSION_CHECK);

    specifyK8sVersion("2", "7", "");

    assertThat(helper.performK8sVersionCheck(), returnsVersion(2, 0));
  }

  @Test
  public void whenK8sMajorVersionGreaterThanOne_logGitVersion() throws Exception {
    specifyK8sVersion("2", "", "");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsInfo(K8S_VERSION_CHECK));
  }

  @Test
  public void whenK8sMinorLessThanSeven_warnOfVersionTooLow() throws Exception {
    specifyK8sVersion("1", "6+", "");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsWarning(K8S_MIN_VERSION_CHECK_FAILED));
  }

  @Test
  public void whenK8sMinorGreaterThanSeven_returnVersionObject() throws Exception {
    ignoreMessage(K8S_VERSION_CHECK);
    specifyK8sVersion("1", "8", "3");

    assertThat(helper.performK8sVersionCheck(), returnsVersion(1, 8));
  }

  @Test
  public void whenK8sMinorGreaterThanSeven_logGitVersion() throws Exception {
    specifyK8sVersion("1", "8", "3");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsInfo(K8S_VERSION_CHECK));
  }

  @Test
  public void whenK8sMinorEqualSevenAndGitVersionThirdFieldLessThanFive_warnOfVersionTooLow()
      throws Exception {
    specifyK8sVersion("1", "7", "1+coreos.0");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsWarning(K8S_MIN_VERSION_CHECK_FAILED));
  }

  @Test
  public void whenK8sMinorEqualSevenAndGitVersionThirdFieldAtLeastFive_logGitVersion()
      throws Exception {
    specifyK8sVersion("1", "7", "5+coreos.0");

    helper.performK8sVersionCheck();

    assertThat(logRecords, containsInfo(K8S_VERSION_CHECK));
  }

  @Test
  public void whenAllDomainUidsUniquePerNamespace_logNoTrackedMessages() throws Exception {
    mementos.add(DomainListCallFactoryStub.install());
    DomainListCallFactoryStub.defineDomainUids(NS1, "uid1", "uid2");
    DomainListCallFactoryStub.defineDomainUids(NS2, "uid3", "uid4", "uid5");

    helper.performNonSecurityChecks();
  }

  @Test
  public void whenDuplicateDomainUidsWithinNamespace_logMessage() throws Exception {
    mementos.add(DomainListCallFactoryStub.install());
    DomainListCallFactoryStub.defineDomainUids(NS1, "uid1", "uid1");

    helper.performNonSecurityChecks();

    assertThat(logRecords, containsWarning(DOMAIN_UID_UNIQUENESS_FAILED));
  }

  @Test
  public void whenMissingPersistentVolumeForUid_logMessage() throws Exception {
    mementos.add(DomainListCallFactoryStub.install());
    DomainListCallFactoryStub.defineDomainUids(NS1, "uid1", "uid2");
    DomainListCallFactoryStub.removePersistentVolume("uid2");

    helper.performNonSecurityChecks();

    assertThat(logRecords, containsWarning(PV_NOT_FOUND_FOR_DOMAIN_UID));
  }

  @Test
  public void whenPersistentVolumeForUidLacksReadWriteAccess_logMessage() throws Exception {
    mementos.add(DomainListCallFactoryStub.install());
    DomainListCallFactoryStub.defineDomainUids(NS1, "uid1", "uid2");
    DomainListCallFactoryStub.setPersistentVolumeReadOnly("uid2");

    helper.performNonSecurityChecks();

    assertThat(logRecords, containsWarning(PV_ACCESS_MODE_FAILED));
  }

  @SuppressWarnings("SameParameterValue")
  abstract static class DomainListCallFactoryStub implements SynchronousCallFactory {
    private static DomainListCallFactoryStub callFactory;
    private Map<String, DomainList> namespacesToDomains = new HashMap<>();
    private List<V1PersistentVolume> persistentVolumes = new ArrayList<>();

    static Memento install() throws NoSuchFieldException {
      callFactory = createStrictStub(DomainListCallFactoryStub.class);
      return StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", callFactory);
    }

    static void defineDomainUids(String namespace, String... uids) {
      callFactory.namespacesToDomains.put(namespace, createDomainList(uids));
      for (String uid : uids) callFactory.persistentVolumes.add(createPersistentVolume(uid));
    }

    private static V1PersistentVolume createPersistentVolume(String uid) {
      return new V1PersistentVolume()
          .metadata(createUidMetadata(uid))
          .spec(createPersistentVolumeSpec());
    }

    private static V1ObjectMeta createUidMetadata(String uid) {
      return new V1ObjectMeta().labels(ImmutableMap.of(DOMAIN_UID_LABEL, uid));
    }

    private static V1PersistentVolumeSpec createPersistentVolumeSpec() {
      return new V1PersistentVolumeSpec().accessModes(singletonList(READ_WRITE_MANY_ACCESS));
    }

    private static DomainList createDomainList(String[] uids) {
      List<Domain> domains = new ArrayList<>();
      for (int i = 0; i < uids.length; i++) {
        domains.add(createDomain(i + 1, uids[i]));
      }

      return new DomainList().withItems(domains);
    }

    private static Domain createDomain(int i, String uid) {
      return new Domain()
          .withSpec(new DomainSpec().withDomainUID(uid))
          .withMetadata(new V1ObjectMeta().name("domain" + i));
    }

    @Override
    public V1PersistentVolumeList listPersistentVolumes(
        ApiClient client,
        String pretty,
        String _continue,
        String fieldSelector,
        Boolean includeUninitialized,
        String labelSelector,
        Integer limit,
        String resourceVersion,
        Integer timeoutSeconds,
        Boolean watch)
        throws ApiException {
      return new V1PersistentVolumeList().items(persistentVolumes);
    }

    @Override
    public DomainList getDomainList(
        ApiClient client,
        String namespace,
        String pretty,
        String _continue,
        String fieldSelector,
        Boolean includeUninitialized,
        String labelSelector,
        Integer limit,
        String resourceVersion,
        Integer timeoutSeconds,
        Boolean watch)
        throws ApiException {
      return Optional.ofNullable(namespacesToDomains.get(namespace))
          .orElse(new DomainList().withItems(Collections.emptyList()));
    }

    static void removePersistentVolume(String uid) {
      callFactory.persistentVolumes.removeIf(volume -> hasUid(volume, uid));
    }

    private static boolean hasUid(V1PersistentVolume volume, String uid) {
      return volume.getMetadata().getLabels().get(DOMAIN_UID_LABEL).equals(uid);
    }

    static void setPersistentVolumeReadOnly(String uid) {
      callFactory
          .persistentVolumes
          .stream()
          .filter(volume -> hasUid(volume, uid))
          .forEach(DomainListCallFactoryStub::setReadOnly);
    }

    private static void setReadOnly(V1PersistentVolume volume) {
      volume.getSpec().setAccessModes(Collections.emptyList());
    }
  }

  @Test
  public void whenRulesReviewNotSupported_requestsAccessReviewForEverything() throws Exception {
    expectAccessChecks();

    helper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION);

    assertThat(AccessReviewCallFactoryStub.getExpectedAccessChecks(), empty());
  }

  private void expectAccessChecks() throws NoSuchFieldException, ApiException {
    mementos.add(AccessReviewCallFactoryStub.install());
    TARGET_NAMESPACES.forEach(this::expectAccessReviewsByNamespace);
    expectClusterAccessChecks();
  }

  @Test
  public void whenRulesReviewNotSupportedAndNoNamespaceAccess_logWarning() throws Exception {
    expectAccessChecks();
    setMayAccessNamespace(false);

    helper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION);

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED));
  }

  @Test
  public void whenRulesReviewNotSupportedAndNoClusterAccess_logWarning() throws Exception {
    expectAccessChecks();
    setMayAccessCluster(false);

    helper.performSecurityChecks(MINIMAL_KUBERNETES_VERSION);

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED));
  }

  @Test
  public void whenRulesReviewSupported_accessGrantedForEverything() throws Exception {
    mementos.add(AccessReviewCallFactoryStub.install());

    helper.performSecurityChecks(RULES_REVIEW_VERSION);
  }

  @Test
  public void whenRulesReviewSupportedAndNoNamespaceAccess_logWarning() throws Exception {
    mementos.add(AccessReviewCallFactoryStub.install());
    setMayAccessNamespace(false);

    helper.performSecurityChecks(RULES_REVIEW_VERSION);

    assertThat(logRecords, containsWarning(VERIFY_ACCESS_DENIED));
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

  abstract static class AccessReviewCallFactoryStub
      implements oracle.kubernetes.operator.helpers.SynchronousCallFactory {
    private static AccessReviewCallFactoryStub callFactory;

    private List<V1ResourceAttributes> expectedAccessChecks = new ArrayList<>();
    private boolean mayAccessNamespace = true;
    private boolean mayAccessCluster = true;

    static Memento install() throws NoSuchFieldException {
      callFactory = createStrictStub(AccessReviewCallFactoryStub.class);
      return StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", callFactory);
    }

    static void setMayAccessNamespace(boolean mayAccessNamespace) {
      callFactory.mayAccessNamespace = mayAccessNamespace;
    }

    static void setMayAccessCluster(boolean mayAccessCluster) {
      callFactory.mayAccessCluster = mayAccessCluster;
    }

    static void expectAccessCheck(String namespace, String resource, Operation operation) {
      callFactory.expectedAccessChecks.add(
          createResourceAttributes(namespace, resource, operation));
    }

    static List<V1ResourceAttributes> getExpectedAccessChecks() {
      return Collections.unmodifiableList(callFactory.expectedAccessChecks);
    }

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

    @Override
    public V1SelfSubjectAccessReview createSelfSubjectAccessReview(
        ApiClient client, V1SelfSubjectAccessReview body, String pretty) throws ApiException {
      V1ResourceAttributes resourceAttributes = body.getSpec().getResourceAttributes();
      boolean allowed = isAllowedByDefault(resourceAttributes);
      if (!expectedAccessChecks.remove(resourceAttributes)) allowed = false;

      body.setStatus(new V1SubjectAccessReviewStatus().allowed(allowed));
      return body;
    }

    private boolean isAllowedByDefault(V1ResourceAttributes resourceAttributes) {
      return resourceAttributes.getNamespace() == null ? mayAccessCluster : mayAccessNamespace;
    }

    @Override
    public V1SelfSubjectRulesReview createSelfSubjectRulesReview(
        ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException {
      return new V1SelfSubjectRulesReview().status(createRulesStatus());
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
      return resourceStrings
          .stream()
          .map(AccessReviewCallFactoryStub::getApiGroup)
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
