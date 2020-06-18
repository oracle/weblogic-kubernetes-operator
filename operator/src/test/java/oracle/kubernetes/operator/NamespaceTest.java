// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.util.function.Function.identity;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class NamespaceTest {

  private static final String NAMESPACES_PROPERTY = "OPERATOR_TARGET_NAMESPACES";
  private static final String ADDITIONAL_NAMESPACE = "NS3";
  public static final String NAMESPACE_STOPPING_MAP = "namespaceStoppingMap";

  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final TuningParameters.WatchTuning tuning = new TuningParameters.WatchTuning(30, 0);
  private List<Memento> mementos = new ArrayList<>();
  private Set<String> currentNamespaces = new HashSet<>();
  private Map<String,String> helmValues = new HashMap<>();
  private Function<String,String> getTestHelmValue = helmValues::get;
  private Method stopNamespace = null;
  private DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.preserve(Main.class, "namespaceStatuses"));
    mementos.add(StaticStubSupport.preserve(Main.class, NAMESPACE_STOPPING_MAP));
    mementos.add(StaticStubSupport.install(Main.class, "getHelmVariable", getTestHelmValue));
    mementos.add(TuningParametersStub.install(120));
    mementos.add(StaticStubSupport.install(Main.class, "processor", dp));
    AtomicBoolean stopping = new AtomicBoolean(true);
    JobWatcher.defineFactory(r -> createDaemonThread(), tuning, ns -> stopping);
  }

  private Thread createDaemonThread() {
    Thread thread = new Thread();
    thread.setDaemon(true);
    return thread;
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void givenJobWatcherForNamespace_afterNamespaceDeletedAndRecreatedHaveDifferentWatcher()
      throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    addTargetNamespace(NS);
    addTargetNamespace(ADDITIONAL_NAMESPACE);
    cacheStartedNamespaces();
    JobWatcher oldWatcher = JobWatcher.getOrCreateFor(domain);

    // Stop the namespace before removing as a target namespace so operator will stop it.
    invoke_stopNamespace(NS, true);
    deleteTargetNamespace(NS);
    Main.recheckDomains().run();

    assertThat(JobWatcher.getOrCreateFor(domain), not(sameInstance(oldWatcher)));
  }

  @Test
  public void whenNamespaceNotInTargetNamespaceList_namespaceRemovedFromNamespaceStoppingMap()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException {
    addTargetNamespace(NS);
    cacheStartedNamespaces();

    // Stop the namespace that is not in targetNamespace list
    invoke_stopNamespace(NS, false);

    Map<String, AtomicBoolean> namespaceStoppingMap = getNamespaceStoppingMap();

    // Verify 'namespace' removed from 'namespaceStoppingMap'
    assertThat(namespaceStoppingMap, anEmptyMap());
  }

  @Test
  public void whenNamespaceInTargetNamespaceList_namespaceExistsInNamespaceStoppingMap()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, NoSuchFieldException {
    addTargetNamespace(NS);
    addTargetNamespace(ADDITIONAL_NAMESPACE);
    cacheStartedNamespaces();

    // Stop the namespace that is in targetNamespace list
    invoke_stopNamespace(ADDITIONAL_NAMESPACE, true);

    // Stop the namespace that is NOT in targetNamespace list
    invoke_stopNamespace(NS, false);

    Map<String, AtomicBoolean> namespaceStoppingMap = getNamespaceStoppingMap();

    // Verify that 'namespaceStoppingMap' has only namespace that was in targetNamespace list
    assertThat(namespaceStoppingMap, aMapWithSize(1));
    assertThat(namespaceStoppingMap, hasKey(ADDITIONAL_NAMESPACE));
  }

  @Test
  public void whenNamespaceStopping_domainProcessorStopNamespaceInvoked()
      throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    addTargetNamespace(NS);
    addTargetNamespace(ADDITIONAL_NAMESPACE);
    cacheStartedNamespaces();

    Map<String, AtomicBoolean> namespaceStoppingMap = getNamespaceStoppingMap();

    // set 'namespace' to stopping
    namespaceStoppingMap.put(NS, new AtomicBoolean(true));

    // Stop the namespace
    invoke_stopNamespace(NS, false);

    assertThat(dp.nameSpaces, hasSize(1));
    assertThat(NS, equalTo(dp.nameSpaces.get(0)));
  }

  @Test
  public void whenNamespaceNotStopping_domainProcessorStopNamespaceNotInvoked()
      throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    addTargetNamespace(NS);
    cacheStartedNamespaces();

    Map<String, AtomicBoolean> namespaceStoppingMap = getNamespaceStoppingMap();

    // Stop the namespace not in targetNamespace list
    invoke_stopNamespace(NS, false);

    // Verify DomainProcessor::stopNamespace not called since namespace is active (i.e. not stopping)
    assertThat(dp.nameSpaces, is(empty()));
  }

  private Map<String, AtomicBoolean> getNamespaceStoppingMap()
      throws NoSuchFieldException, IllegalAccessException {
    Field field = Main.class.getDeclaredField(NAMESPACE_STOPPING_MAP);
    field.setAccessible(true);
    return (Map<String, AtomicBoolean>) field.get(null);
  }

  private void addTargetNamespace(String namespace) {
    currentNamespaces.add(namespace);
    helmValues.put(NAMESPACES_PROPERTY, String.join(",", currentNamespaces));
  }

  @SuppressWarnings("SameParameterValue")
  private void deleteTargetNamespace(String namespace) {
    currentNamespaces.remove(namespace);
    helmValues.put(NAMESPACES_PROPERTY, String.join(",", currentNamespaces));
  }

  private void cacheStartedNamespaces() throws NoSuchFieldException {
    StaticStubSupport.install(Main.class, "namespaceStatuses", createNamespaceStatuses());
    StaticStubSupport.install(Main.class, NAMESPACE_STOPPING_MAP, createNamespaceFlags());
  }

  private Map<String, NamespaceStatus> createNamespaceStatuses() {
    return currentNamespaces.stream()
        .collect(Collectors.toMap(identity(), a -> new NamespaceStatus()));
  }

  private Map<String, AtomicBoolean> createNamespaceFlags() {
    return currentNamespaces.stream()
        .collect(Collectors.toMap(identity(), a -> new AtomicBoolean()));
  }

  @SuppressWarnings("unchecked")
  private void invoke_stopNamespace(String namespace, boolean inTargetNamespaceList)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (stopNamespace == null) {
      stopNamespace =
          Main.class.getDeclaredMethod("stopNamespace", String.class, Boolean.TYPE);
      stopNamespace.setAccessible(true);
    }
    stopNamespace.invoke(null, namespace, inTargetNamespaceList);
  }

  abstract static class TuningParametersStub implements TuningParameters {

    int domainPresenceRecheckIntervalSeconds;

    public static Memento install(int newValue) throws NoSuchFieldException {
      return StaticStubSupport.install(
        TuningParametersImpl.class, "INSTANCE", createStrictStub(TuningParametersStub.class, newValue));
    }

    TuningParametersStub(int domainPresenceRecheckIntervalSeconds) {
      this.domainPresenceRecheckIntervalSeconds = domainPresenceRecheckIntervalSeconds;
    }

    @Override
    public MainTuning getMainTuning() {
      return new MainTuning(2, 2, domainPresenceRecheckIntervalSeconds, 2, 2, 2, 2L, 2L);
    }
  }

  abstract static class DomainProcessorStub implements DomainProcessor {
    ArrayList<String> nameSpaces = new ArrayList<>();

    @Override
    public void stopNamespace(String ns) {
      Optional.ofNullable(ns).ifPresent(nspace -> nameSpaces.add(nspace));
    }
  }
}
