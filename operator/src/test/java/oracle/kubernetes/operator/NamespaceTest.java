// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.helpers.HelmAccess.OPERATOR_DOMAIN_NAMESPACES;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class NamespaceTest {

  private static final String ADDITIONAL_NS1 = "EXTRA_NS1";
  private static final String ADDITIONAL_NS2 = "EXTRA_NS2";
  public static final String NAMESPACE_STOPPING_MAP = "namespaceStoppingMap";

  KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final WatchTuning tuning = new WatchTuning(30, 0, 5);
  private final List<Memento> mementos = new ArrayList<>();
  private final Set<String> currentNamespaces = new HashSet<>();
  private final DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.preserve(DomainNamespaces.class, "namespaceStatuses"));
    mementos.add(StaticStubSupport.preserve(DomainNamespaces.class, NAMESPACE_STOPPING_MAP));
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StaticStubSupport.install(Main.class, "processor", dp));
    mementos.add(testSupport.install());
    AtomicBoolean stopping = new AtomicBoolean(true);
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
  public void givenJobWatcherForNamespace_afterNamespaceDeletedAndRecreatedHaveDifferentWatcher() {
    initializeNamespaces();
    JobWatcher oldWatcher = DomainNamespaces.getJobWatcher(NS);

    deleteNamespace(NS);
    processNamespaces();
    defineNamespaces(NS);

    testSupport.runSteps(Main.createDomainRecheckSteps(DateTime.now()));
    assertThat(DomainNamespaces.getJobWatcher(NS), not(sameInstance(oldWatcher)));
  }

  @Test
  public void whenDomainNamespaceRemovedFromDomainNamespaces_stopDomainWatchers() {
    initializeNamespaces();
    AtomicBoolean stopping = DomainNamespaces.isStopping(NS);

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(stopping.get(), is(true));
  }

  private void initializeNamespaces() {
    defineNamespaces(NS, ADDITIONAL_NS1, ADDITIONAL_NS2);
    specifyDomainNamespaces(NS, ADDITIONAL_NS2);
    processNamespaces();
  }

  private void defineNamespaces(String... namespaces) {
    Arrays.stream(namespaces).forEach(ns -> testSupport.defineResources(createNamespace(ns), createDomain(ns)));
  }

  private V1Namespace createNamespace(String n) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(n));
  }

  private Domain createDomain(String ns) {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(ns).name(createUid(ns)));
  }

  @NotNull
  private String createUid(String ns) {
    return "uid-" + ns;
  }

  private void specifyDomainNamespaces(String... namespaces) {
    Arrays.stream(namespaces).forEach(this::addDomainNamespace);
  }

  @SuppressWarnings("SameParameterValue")
  private void deleteNamespace(String namespaceName) {
    testSupport.deleteNamespace(namespaceName);
  }

  private void processNamespaces() {
    testSupport.withClearPacket().runSteps(new Main.Namespaces(false).readExistingNamespaces());
  }

  @Test
  public void whenDomainNamespaceRemovedFromDomainNamespaces_isNoLongerInManagedNamespaces() {
    initializeNamespaces();

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(DomainNamespaces.getNamespaces(), not(contains(NS)));
  }

  @Test
  public void whenDomainNamespaceRemovedFromDomainNamespaces_doNotShutdownDomain() {
    initializeNamespaces();

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(getDomainsInNamespace(NS), notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  private Domain getDomainsInNamespace(String namespace) {
    return testSupport.<Domain>getResources(DOMAIN).stream()
          .filter(d -> d.getDomainUid().equals(createUid(namespace)))
          .findFirst()
          .orElse(null);
  }

  @Test
  public void whenDomainNamespaceDeleted_stopDomainWatchers() {
    initializeNamespaces();
    AtomicBoolean stopping = DomainNamespaces.isStopping(NS);

    deleteNamespace(NS);
    processNamespaces();

    assertThat(stopping.get(), is(true));
  }

  @Test
  public void whenDomainNamespaceDeleted_isNoLongerInManagedNamespaces() {
    initializeNamespaces();

    deleteNamespace(NS);
    processNamespaces();

    assertThat(DomainNamespaces.getNamespaces(), not(contains(NS)));
  }

  private void addDomainNamespace(String namespace) {
    currentNamespaces.add(namespace);
    HelmAccessStub.defineVariable(OPERATOR_DOMAIN_NAMESPACES, String.join(",", currentNamespaces));
  }

  @SuppressWarnings("SameParameterValue")
  private void unspecifyDomainNamespace(String namespace) {
    currentNamespaces.remove(namespace);
    HelmAccessStub.defineVariable(OPERATOR_DOMAIN_NAMESPACES, String.join(",", currentNamespaces));
  }

  abstract static class DomainProcessorStub implements DomainProcessor {
    List<String> nameSpaces = new ArrayList<>();

    @Override
    public void stopNamespace(String ns) {
      throw new RuntimeException("Should not be calling this");
    }
  }

}
