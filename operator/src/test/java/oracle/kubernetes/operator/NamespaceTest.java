// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.util.function.Function.identity;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class NamespaceTest {
  private static final String NAMESPACES_PROPERTY = "OPERATOR_TARGET_NAMESPACES";
  private static final String ADDITIONAL_NAMESPACE = "NS3";

  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final TuningParameters.WatchTuning tuning = new TuningParameters.WatchTuning(30, 0);
  private List<Memento> mementos = new ArrayList<>();
  private Set<String> currentNamespaces = new HashSet<>();
  private Map<String,String> helmValues = new HashMap<>();
  private Function<String,String> getTestHelmValue = helmValues::get;

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.preserve(Main.class, "namespaceStatuses"));
    mementos.add(StaticStubSupport.preserve(Main.class, "isNamespaceStopping"));
    mementos.add(StaticStubSupport.install(Main.class, "getHelmVariable", getTestHelmValue));

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
      throws NoSuchFieldException {
    addTargetNamespace(NS);
    addTargetNamespace(ADDITIONAL_NAMESPACE);
    cacheStartedNamespaces();
    JobWatcher oldWatcher = JobWatcher.getOrCreateFor(domain);

    deleteTargetNamespace(NS);
    Main.recheckDomains().run();

    assertThat(JobWatcher.getOrCreateFor(domain), not(sameInstance(oldWatcher)));
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
    StaticStubSupport.install(Main.class, "isNamespaceStopping", createNamespaceFlags());
  }

  private Map<String, NamespaceStatus> createNamespaceStatuses() {
    return currentNamespaces.stream()
        .collect(Collectors.toMap(identity(), a -> new NamespaceStatus()));
  }

  private Map<String, AtomicBoolean> createNamespaceFlags() {
    return currentNamespaces.stream()
        .collect(Collectors.toMap(identity(), a -> new AtomicBoolean()));
  }
}
