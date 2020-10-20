// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.Main.Namespaces.SelectionStrategy;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.MainTest.NamespaceStatusMatcher.isNamespaceStarting;
import static oracle.kubernetes.operator.TuningParametersImpl.DEFAULT_CALL_LIMIT;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest extends ThreadFactoryTestBase {

  /** More than one chunk's worth of namespaces. */
  private static final int MULTICHUNK_LAST_NAMESPACE_NUM = DEFAULT_CALL_LIMIT + 1;

  /** Less than one chunk's worth of namespaces. */
  private static final int LAST_NAMESPACE_NUM = DEFAULT_CALL_LIMIT - 1;

  private static final String NS = "default";
  private static final String DOMAIN_UID = "domain-uid-for-testing";

  private static final String NAMESPACE_STATUS_MAP = "namespaceStatuses";
  private static final String NAMESPACE_STOPPING_MAP = "namespaceStoppingMap";

  private static final String REGEXP = "regexp";
  private static final String NS_WEBLOGIC1 = "weblogic-alpha";
  private static final String NS_WEBLOGIC2 = "weblogic-beta-" + REGEXP;
  private static final String NS_WEBLOGIC3 = "weblogic-gamma";
  private static final String NS_WEBLOGIC4 = "other-alpha-" + REGEXP;
  private static final String NS_WEBLOGIC5 = "other-beta";

  private static final String LABEL = "weblogic-operator";
  private static final String VALUE = "enabled";

  private static final V1Namespace NAMESPACE_WEBLOGIC1
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC1).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC2
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC2));
  private static final V1Namespace NAMESPACE_WEBLOGIC3
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC3).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC4
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC4));
  private static final V1Namespace NAMESPACE_WEBLOGIC5
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC5).putLabelsItem(LABEL, VALUE));

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(loggerControl);
    mementos.add(testSupport.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(Main.class, "version", new KubernetesVersion(1, 16)));
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "INSTANCE", this));
    mementos.add(StaticStubSupport.install(Main.class, "engine", testSupport.getEngine()));
    mementos.add(StaticStubSupport.install(DomainNamespaces.class, NAMESPACE_STATUS_MAP, createNamespaceStatuses()));
    mementos.add(StaticStubSupport.install(DomainNamespaces.class, NAMESPACE_STOPPING_MAP, createNamespaceFlags()));
    mementos.add(NoopWatcherStarter.install());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  @SuppressWarnings("unchecked")
  private Map<String, NamespaceStatus> getNamespaceStatusMap()
          throws NoSuchFieldException, IllegalAccessException {
    Field field = DomainNamespaces.class.getDeclaredField(NAMESPACE_STATUS_MAP);
    field.setAccessible(true);
    return (Map<String, NamespaceStatus>) field.get(null);
  }

  private Map<String, NamespaceStatus> createNamespaceStatuses() {
    return new ConcurrentHashMap<>();
  }

  private Map<String, AtomicBoolean> createNamespaceFlags() {
    return new ConcurrentHashMap<>();
  }

  @Test
  public void withNamespaceList_onReadExistingNamespaces_startsNamespaces()
      throws IllegalAccessException, NoSuchFieldException {
    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.List);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
          String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
                                NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());

    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC1), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC2), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC3), isNamespaceStarting())));
    assertThat(getNamespaceStatusMap(), aMapWithSize(3));
  }

  @SuppressWarnings("unused")
  static class NamespaceStatusMatcher extends TypeSafeDiagnosingMatcher<NamespaceStatus> {

    static NamespaceStatusMatcher isNamespaceStarting() {
      return new NamespaceStatusMatcher();
    }

    @Override
    protected boolean matchesSafely(NamespaceStatus item, Description mismatchDescription) {
      if (item.isNamespaceStarting().get()) {
        return true;
      }

      mismatchDescription.appendText("isNamespaceStarting is false");
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("NamespaceStatus with isNamespaceStarting true");
    }
  }

  @Test
  public void withRegExp_onReadExistingNamespaces_startsNamespaces()
          throws IllegalAccessException, NoSuchFieldException {
    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.RegExp);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParameters.getInstance().put("domainNamespaceRegExp", REGEXP);
    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());

    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC2), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC4), isNamespaceStarting())));
    assertThat(getNamespaceStatusMap(), aMapWithSize(2));
  }

  @Test
  public void withLabelSelector_onReadExistingNamespaces_startsNamespaces()
          throws IllegalAccessException, NoSuchFieldException {
    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.LabelSelector);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParameters.getInstance().put("domainNamespaceLabelSelector", LABEL + "=" + VALUE);
    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());

    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC1), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC3), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC5), isNamespaceStarting())));
    assertThat(getNamespaceStatusMap(), aMapWithSize(3));
  }

  private V1ObjectMeta createMetadata(DateTime creationTimestamp) {
    return new V1ObjectMeta()
        .name(DOMAIN_UID)
        .namespace(NS)
        .creationTimestamp(creationTimestamp)
        .resourceVersion("1");
  }

  @Test
  public void whenConfiguredDomainNamespaceMissing_logWarning() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.List);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM - 1);

    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());

    assertThat(logRecords, containsWarning(MessageKeys.NAMESPACE_IS_MISSING));
  }

  @Test
  public void whenNamespacesListedInOneChunk_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.List);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM);

    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());
  }

  private void defineSelectionStrategy(SelectionStrategy selectionStrategy) {
    TuningParameters.getInstance().put(Main.Namespaces.SELECTION_STRATEGY_KEY, selectionStrategy.toString());
  }

  @Test
  public void whenNamespacesListedInMultipleChunks_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(Main.Namespaces.SelectionStrategy.List);
    String namespaceString = "NS1,NS" + MULTICHUNK_LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(MULTICHUNK_LAST_NAMESPACE_NUM);

    testSupport.runSteps(new Main.Namespaces(false).readExistingNamespaces());
  }

  private void createNamespaces(int lastNamespaceNum) {
    IntStream.rangeClosed(1, lastNamespaceNum)
          .boxed()
          .map(i -> "NS" + i)
          .map(this::createNamespace)
          .forEach(testSupport::defineResources);
  }

  private V1Namespace createNamespace(String ns) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(ns));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_same_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    V1ObjectMeta domain2Meta = createMetadata(creationDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_newer_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.plusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_doNotDelete_with_older_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.minusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertTrue(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void afterReadingExistingResourcesForNamespace_WatchersAreDefined() {
    testSupport.runSteps(Main.readExistingResources(NS));

    assertThat(DomainNamespaces.getConfigMapWatcher(NS), notNullValue());
    assertThat(DomainNamespaces.getDomainWatcher(NS), notNullValue());
    assertThat(DomainNamespaces.getEventWatcher(NS), notNullValue());
    assertThat(DomainNamespaces.getPodWatcher(NS), notNullValue());
    assertThat(DomainNamespaces.getServiceWatcher(NS), notNullValue());
  }


  @Test
  public void afterReadingExistingResourcesForNamespace_ScriptConfigMapIsDefined() {
    testSupport.runSteps(Main.readExistingResources(NS));

    assertThat(getScriptMap(NS), notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  private V1ConfigMap getScriptMap(String ns) {
    return testSupport.<V1ConfigMap>getResources(KubernetesTestSupport.CONFIG_MAP).stream()
          .filter(m -> isScriptConfigMap(m, ns))
          .findFirst()
          .orElse(null);
  }

  @SuppressWarnings("SameParameterValue")
  private boolean isScriptConfigMap(V1ConfigMap configMap, String ns) {
    return Optional.ofNullable(configMap.getMetadata()).filter(meta -> isScriptConfigMapMetadata(meta, ns)).isPresent();
  }

  private boolean isScriptConfigMapMetadata(V1ObjectMeta meta, String ns) {
    return SCRIPT_CONFIG_MAP_NAME.equals(meta.getName()) && ns.equals(meta.getNamespace());
  }
  // when namespace added, resources defined, config map is defined and watchers are started (?)
}
