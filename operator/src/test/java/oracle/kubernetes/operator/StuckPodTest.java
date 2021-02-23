// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.logging.MessageKeys.POD_FORCE_DELETED;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class StuckPodTest {

  private static final long DELETION_GRACE_PERIOD_SECONDS = 5L;
  private static final String SERVER_POD_1 = "name1";
  private static final String SERVER_POD_2 = "name2";
  private static final String FOREIGN_POD = "foreign";
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Domain domain = createTestDomain();
  private final MainDelegateStub mainDelegate = createStrictStub(MainDelegateStub.class, testSupport);
  private final StuckPodProcessing processing = new StuckPodProcessing(mainDelegate);
  private final V1Pod managedPod1 = defineManagedPod(SERVER_POD_1);
  private final V1Pod managedPod2 = defineManagedPod(SERVER_POD_2);
  private final V1Pod foreignPod = defineForeignPod(FOREIGN_POD);
  private Integer gracePeriodSeconds;
  private TestUtils.ConsoleHandlerMemento consoleMemento;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(consoleMemento = TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());
    mementos.add(NoopWatcherStarter.install());

    testSupport.defineResources(domain, managedPod1, managedPod2, foreignPod);
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();
    
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenServerPodNotDeleted_ignoreIt() {
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS);

    processing.checkStuckPods(NS);

    assertThat(getSelectedPod(SERVER_POD_1), notNullValue());
  }

  @Test
  public void whenServerPodNotStuck_ignoreIt() {
    markAsDelete(getSelectedPod(SERVER_POD_1));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS - 1);

    processing.checkStuckPods(NS);

    assertThat(getSelectedPod(SERVER_POD_1), notNullValue());
  }

  @Test
  public void whenServerPodStuck_deleteIt() {
    markAsDelete(getSelectedPod(SERVER_POD_1));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS + 1);

    processing.checkStuckPods(NS);

    assertThat(getSelectedPod(SERVER_POD_1), nullValue());
  }

  @Test
  public void whenStuckServerPodDeleted_logMessage() {
    final List<LogRecord> logMessages = new ArrayList<>();
    consoleMemento.collectLogMessages(logMessages, POD_FORCE_DELETED).withLogLevel(Level.INFO);
    markAsDelete(getSelectedPod(SERVER_POD_1));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS + 1);

    processing.checkStuckPods(NS);

    assertThat(logMessages, containsInfo(POD_FORCE_DELETED, SERVER_POD_1, NS));
  }

  @Test
  public void whenServerPodDeleted_specifyZeroGracePeriod() {
    markAsDelete(getSelectedPod(SERVER_POD_1));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS + 1);
    testSupport.doOnDelete(POD, this::recordGracePeriodSeconds);

    processing.checkStuckPods(NS);

    assertThat(gracePeriodSeconds, equalTo(0));
  }

  private void recordGracePeriodSeconds(Integer gracePeriodSeconds) {
    this.gracePeriodSeconds = gracePeriodSeconds;
  }

  @Test
  public void whenServerPodStuck_initiateMakeRightProcessing() {
    markAsDelete(getSelectedPod(SERVER_POD_2));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS + 1);

    processing.checkStuckPods(NS);

    assertThat(mainDelegate.makeRightInvoked(domain), is(true));
  }

  @Test
  public void whenForeignPodStuck_ignoreIt() {
    markAsDelete(getSelectedPod(FOREIGN_POD));
    SystemClockTestSupport.increment(DELETION_GRACE_PERIOD_SECONDS + 1);

    processing.checkStuckPods(NS);

    assertThat(getSelectedPod(FOREIGN_POD), notNullValue());
  }

  private V1Pod getSelectedPod(String name) {
    return testSupport.getResourceWithName(POD, name);
  }

  private V1Pod defineManagedPod(String name) {
    return new V1Pod().metadata(createManagedPodMetadata(name));
  }

  private V1ObjectMeta createManagedPodMetadata(String name) {
    return createPodMetadata(name)
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL,"true")
          .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, UID)
          .putLabelsItem(LabelConstants.SERVERNAME_LABEL, name);
  }

  @SuppressWarnings("SameParameterValue")
  private V1Pod defineForeignPod(String name) {
    return new V1Pod().metadata(createPodMetadata(name));
  }

  private V1ObjectMeta createPodMetadata(String name) {
    return new V1ObjectMeta()
          .name(name)
          .namespace(NS);
  }

  private void markAsDelete(V1Pod pod) {
    Objects.requireNonNull(pod.getMetadata())
          .deletionGracePeriodSeconds(DELETION_GRACE_PERIOD_SECONDS)
          .deletionTimestamp(SystemClock.now());
  }

  abstract static class MainDelegateStub implements MainDelegate {
    private final List<Domain> invocations = new ArrayList<>();
    private final DomainProcessorStub domainProcessor = createStrictStub(DomainProcessorStub.class, this);
    private final DomainNamespaces domainNamespaces = new DomainNamespaces();
    private final KubernetesTestSupport testSupport;

    MainDelegateStub(KubernetesTestSupport testSupport) {
      this.testSupport = testSupport;
    }

    boolean makeRightInvoked(Domain domain) {
      return invocations.contains(domain);
    }

    @Override
    public void runSteps(Step firstStep) {
      testSupport.runSteps(firstStep);
    }

    @Override
    public DomainProcessor getDomainProcessor() {
      return domainProcessor;
    }

    @Override
    public DomainNamespaces getDomainNamespaces() {
      return domainNamespaces;
    }

    abstract static class DomainProcessorStub implements DomainProcessor {
      private final MainDelegateStub delegateStub;

      DomainProcessorStub(MainDelegateStub delegateStub) {
        this.delegateStub = delegateStub;
      }

      @Override
      public MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo info) {
        Optional.ofNullable(info).map(DomainPresenceInfo::getDomain).ifPresent(delegateStub.invocations::add);
        return createStrictStub(MakeRightDomainOperationStub.class);
      }
    }

    abstract static class MakeRightDomainOperationStub implements MakeRightDomainOperation {

      @Override
      public MakeRightDomainOperation withExplicitRecheck() {
        return this;
      }

      @Override
      public void execute() {
        
      }
    }
  }
}
