// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.MainTest.NamespaceStatusMatcher.isNamespaceStarting;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest extends ThreadFactoryTestBase {

  private static final String NS = "default";
  private static final String DOMAIN_UID = "domain-uid-for-testing";
  private Method getDomainNamespaces;

  private static final String NAMESPACE_STATUS_MAP = "namespaceStatuses";
  private static final String NAMESPACE_STOPPING_MAP = "namespaceStoppingMap";

  private static final String REGEXP = "^weblogic";
  private static final String NS_WEBLOGIC1 = "weblogic-alpha";
  private static final String NS_WEBLOGIC2 = "weblogic-beta";
  private static final String NS_WEBLOGIC3 = "weblogic-gamma";
  private static final String NS_OTHER1 = "other-alpha";
  private static final String NS_OTHER2 = "other-beta";

  private static final String LABEL = "weblogic-operator";
  private static final String VALUE = "enabled";

  private static final V1Namespace NAMESPACE_WEBLOGIC1
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC1).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC2
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC2).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC3
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC3).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_OTHER1
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_OTHER1));
  private static final V1Namespace NAMESPACE_OTHER2
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_OTHER2));

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(Main.class, "version", new KubernetesVersion(1, 16)));
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "INSTANCE", this));
    mementos.add(StaticStubSupport.install(Main.class, "engine", testSupport.getEngine()));
    mementos.add(StaticStubSupport.install(Main.class, NAMESPACE_STATUS_MAP, createNamespaceStatuses()));
    mementos.add(StaticStubSupport.install(Main.class, NAMESPACE_STOPPING_MAP, createNamespaceFlags()));
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
    Field field = Main.class.getDeclaredField(NAMESPACE_STATUS_MAP);
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
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
            NAMESPACE_OTHER1, NAMESPACE_OTHER2);

    Main.DomainNamespaceSelectionStrategy selectionStrategy = Main.DomainNamespaceSelectionStrategy.List;
    Collection<String> domainNamespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(Main.readExistingNamespaces(selectionStrategy, domainNamespaces, false));

    assertThat(getNamespaceStatusMap(), aMapWithSize(3));
    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC1), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC2), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC3), isNamespaceStarting())));
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
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
            NAMESPACE_OTHER1, NAMESPACE_OTHER2);

    Main.DomainNamespaceSelectionStrategy selectionStrategy = Main.DomainNamespaceSelectionStrategy.RegExp;
    TuningParameters.getInstance().put("domainNamespaceRegExp", REGEXP);
    testSupport.runSteps(Main.readExistingNamespaces(selectionStrategy, null, false));

    assertThat(getNamespaceStatusMap(), aMapWithSize(3));
    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC1), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC2), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC3), isNamespaceStarting())));
  }

  @Test
  public void withLabelSelector_onReadExistingNamespaces_startsNamespaces()
          throws IllegalAccessException, NoSuchFieldException {
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
            NAMESPACE_OTHER1, NAMESPACE_OTHER2);

    Main.DomainNamespaceSelectionStrategy selectionStrategy = Main.DomainNamespaceSelectionStrategy.LabelSelector;
    TuningParameters.getInstance().put("domainNamespaceLabelSelector", LABEL + "=" + VALUE);
    testSupport.runSteps(Main.readExistingNamespaces(selectionStrategy, null, false));

    assertThat(getNamespaceStatusMap(), aMapWithSize(3));
    assertThat(getNamespaceStatusMap(),
               allOf(hasEntry(is(NS_WEBLOGIC1), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC2), isNamespaceStarting()),
                     hasEntry(is(NS_WEBLOGIC3), isNamespaceStarting())));
  }

  @Test
  public void getDomainNamespaces_withEmptyValue_should_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getDomainNamespaces("", NS);
    assertTrue(namespaces.contains("default"));
  }

  @Test
  public void getDomainNamespaces_withNonEmptyValue_should_not_return_default()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getDomainNamespaces("dev-domain", NS);
    assertFalse(namespaces.contains("default"));
  }

  @Test
  public void getDomainNamespaces_with_single_target_should_return_it()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getDomainNamespaces("dev-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
  }

  @Test
  public void getDomainNamespaces_with_multiple_targets_should_include_all()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces =
        invoke_getDomainNamespaces("dev-domain,domain1,test-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("domain1"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getDomainNamespaces_should_remove_leading_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getDomainNamespaces(" test-domain, dev-domain", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  @Test
  public void getDomainNamespaces_should_remove_trailing_spaces()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Collection<String> namespaces = invoke_getDomainNamespaces("dev-domain ,test-domain ", NS);
    assertTrue(namespaces.contains("dev-domain"));
    assertTrue(namespaces.contains("test-domain"));
  }

  private V1ObjectMeta createMetadata(DateTime creationTimestamp) {
    return new V1ObjectMeta()
        .name(DOMAIN_UID)
        .namespace(NS)
        .creationTimestamp(creationTimestamp)
        .resourceVersion("1");
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

  @SuppressWarnings({"unchecked", "SameParameterValue"})
  private Collection<String> invoke_getDomainNamespaces(String tnValue, String namespace)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getDomainNamespaces == null) {
      getDomainNamespaces =
          Main.class.getDeclaredMethod("getDomainNamespacesList", String.class, String.class);
      getDomainNamespaces.setAccessible(true);
    }
    return (Collection<String>) getDomainNamespaces.invoke(null, tnValue, namespace);
  }
}
