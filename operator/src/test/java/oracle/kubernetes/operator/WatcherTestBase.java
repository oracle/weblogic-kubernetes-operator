// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.tuning.FakeWatchTuning;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_GONE;
import static oracle.kubernetes.operator.builders.EventMatcher.addEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.modifyEvent;
import static oracle.kubernetes.operator.builders.StubWatchFactory.AllWatchesClosedListener;
import static oracle.kubernetes.operator.tuning.TuningParameters.WATCH_BACKSTOP_RECHECK_COUNT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

/** Tests behavior of the Watcher class. */
@SuppressWarnings("SameParameterValue")
public abstract class WatcherTestBase extends ThreadFactoryTestBase implements AllWatchesClosedListener {
  private static final BigInteger NEXT_RESOURCE_VERSION = new BigInteger("214748364705");
  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("214748364700");
  private static final String NAMESPACE = "testspace";

  private final RuntimeException hasNextException = new RuntimeException(Watcher.HAS_NEXT_EXCEPTION_MESSAGE);
  private final List<Memento> mementos = new ArrayList<>();
  private final List<Watch.Response<?>> callBacks = new ArrayList<>();
  private final AtomicBoolean stopping = new AtomicBoolean(false);
  final WatchTuning tuning = new FakeWatchTuning();
  private BigInteger resourceVersion = INITIAL_RESOURCE_VERSION;

  private V1ObjectMeta createMetaData() {
    return createMetaData(getNextResourceVersion());
  }

  private V1ObjectMeta createMetaData(String resourceVersion) {
    return createMetaData("test", NAMESPACE, resourceVersion);
  }

  @SuppressWarnings("SameParameterValue")
  private V1ObjectMeta createMetaData(String name, String namespace, String resourceVersion) {
    return new V1ObjectMeta()
        .name(name)
        .namespace(namespace)
        .resourceVersion(resourceVersion);
  }

  @Override
  public void allWatchesClosed() {
    stopping.set(true);
  }

  void recordCallBack(Watch.Response<?> response) {
    callBacks.add(response);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(configureOperatorLogger());
    mementos.add(StubWatchFactory.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(TestStepFactory.install());

    TuningParametersStub.setParameter(WATCH_BACKSTOP_RECHECK_COUNT, "1");
    StubWatchFactory.setListener(this);
  }

  protected BaseTestUtils.ConsoleHandlerMemento configureOperatorLogger() {
    return TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(hasNextException);
  }

  final void addMemento(Memento memento) {
    mementos.add(memento);
  }

  @AfterEach
  public void tearDown() throws Exception {
    shutDownThreads();
    mementos.forEach(Memento::revert);
  }

  void sendInitialRequest(BigInteger initialResourceVersion) {
    scheduleAddResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, initialResourceVersion);
  }

  Watcher<?> sendBookmarkRequest(BigInteger initialResourceVersion, String bookmarkResourceVersion) {
    scheduleBookmarkResponse(createObjectWithMetaData(bookmarkResourceVersion));

    return createAndRunWatcher(NAMESPACE, stopping, initialResourceVersion);
  }

  private Object createObjectWithMetaData() {
    return createObjectWithMetaData(createMetaData());
  }

  private Object createObjectWithMetaData(String resourceVersion) {
    return createObjectWithMetaData(createMetaData(resourceVersion));
  }

  protected abstract <T> T createObjectWithMetaData(V1ObjectMeta metaData);

  @Test
  void afterInitialRequest_watchIsClosed() {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(StubWatchFactory.getNumCloseCalls(), equalTo(1));
  }

  private <T> Watch.Response<T> createAddResponse(T object) {
    return WatchEvent.createAddedEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createBookmarkResponse(T object) {
    return WatchEvent.createBookmarkEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createModifyResponse(T object) {
    return WatchEvent.createModifiedEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createDeleteResponse(T object) {
    return WatchEvent.createDeletedEvent(object).toWatchResponse();
  }

  private Watch.Response<Object> createHttpGoneErrorResponse(BigInteger nextResourceVersion) {
    return WatchEvent.createErrorEvent(HTTP_GONE, nextResourceVersion.toString()).toWatchResponse();
  }

  private Watch.Response<Object> createHttpGoneErrorWithoutResourceVersionResponse() {
    return WatchEvent.createErrorEvent(HTTP_GONE).toWatchResponse();
  }

  private Watch.Response<Object> createErrorWithoutStatusResponse() {
    return WatchEvent.createErrorEventWithoutStatus().toWatchResponse();
  }

  @Test
  void receivedEvents_areSentToListeners() {
    Object object1 = createObjectWithMetaData();
    Object object2 = createObjectWithMetaData();
    StubWatchFactory.addCallResponses(createAddResponse(object1), createModifyResponse(object2));

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(callBacks, contains(List.of(addEvent(object1), modifyEvent(object2))));
  }

  @Test
  void receivedEvents_areNotSentToListenersWhenWatchersPaused() {
    Object object1 = createObjectWithMetaData();
    Object object2 = createObjectWithMetaData();
    StubWatchFactory.addCallResponses(createAddResponse(object1), createModifyResponse(object2));

    Watcher watcher = createWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);
    pauseWatcher(watcher);
    assertThat(callBacks, empty());

    resumeWatcher(watcher);
    assertThat(callBacks, contains(List.of(addEvent(object1), modifyEvent(object2))));
  }

  private void resumeWatcher(Watcher watcher1) {
    watcher1.start(this);
    watcher1.resume();
    watcher1.waitForExit();
  }

  private void pauseWatcher(Watcher watcher1) {
    watcher1.pause();
    watcher1.waitForExit();
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  void afterFirstSetOfEvents_nextRequestSendsLastResourceVersion() {
    Object object1 = createObjectWithMetaData();
    Object object2 = createObjectWithMetaData();
    Watch.Response[] firstSet = {createAddResponse(object1), createModifyResponse(object2)};
    StubWatchFactory.addCallResponses(firstSet);
    scheduleAddResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(1),
        hasEntry("resourceVersion", resourceVersion.subtract(BigInteger.TWO).toString()));
  }

  @Test
  void afterHttpGoneError_nextRequestSendsIncludedResourceVersion() {
    StubWatchFactory.addCallResponses(createHttpGoneErrorResponse(NEXT_RESOURCE_VERSION));
    scheduleDeleteResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(1),
        hasEntry("resourceVersion", NEXT_RESOURCE_VERSION.toString()));
  }

  @Test
  void afterHttpGoneErrorWithoutResourceVersion_nextRequestSendsResourceVersionZero() {
    StubWatchFactory.addCallResponses(createHttpGoneErrorWithoutResourceVersionResponse());
    scheduleDeleteResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(StubWatchFactory.getRequestParameters().get(1), hasEntry("resourceVersion", "0"));
  }

  @Test
  void afterErrorWithoutStatus_nextRequestSendsResourceVersionZero() {
    StubWatchFactory.addCallResponses(createErrorWithoutStatusResponse());
    scheduleDeleteResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(StubWatchFactory.getRequestParameters().get(1), hasEntry("resourceVersion", "0"));
  }

  @Test
  void afterExceptionDuringNext_closeWatchAndTryAgain() {
    StubWatchFactory.throwExceptionOnNext(hasNextException);
    scheduleAddResponse(createObjectWithMetaData());

    createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

    assertThat(StubWatchFactory.getNumCloseCalls(), equalTo(2));
  }

  void scheduleAddResponse(Object object) {
    StubWatchFactory.addCallResponses(createAddResponse(object));
  }

  void scheduleBookmarkResponse(Object object) {
    StubWatchFactory.addCallResponses(createBookmarkResponse(object));
  }

  private void scheduleDeleteResponse(Object object) {
    StubWatchFactory.addCallResponses(createDeleteResponse(object));
  }

  private String getNextResourceVersion() {
    String res = resourceVersion.toString();
    resourceVersion = resourceVersion.add(BigInteger.ONE);
    return res;
  }

  private Watcher<?> createAndRunWatcher(String nameSpace, AtomicBoolean stopping, BigInteger resourceVersion) {
    Watcher<?> watcher = createWatcher(nameSpace, stopping, resourceVersion);
    watcher.waitForExit();
    return watcher;
  }

  protected abstract Watcher<?> createWatcher(String ns, AtomicBoolean stopping, BigInteger rv);

  static class TestStepFactory implements WaitForReadyStep.NextStepFactory {

    private static final TestStepFactory factory = new TestStepFactory();

    private static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(WaitForReadyStep.class, "nextStepFactory", factory);
    }

    @Override
    public Step createMakeDomainRightStep(WaitForReadyStep<?>.Callback callback,
                                                  DomainPresenceInfo info, Step next) {
      return next;
    }
  }

}
