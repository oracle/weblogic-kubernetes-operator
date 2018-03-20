// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;

import com.meterware.simplestub.Memento;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_GONE;
import static oracle.kubernetes.operator.builders.EventMatcher.addEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.modifyEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

/**
 * Tests behavior of the Watcher class.
 */
public abstract class WatcherTestBase implements StubWatchFactory.AllWatchesClosedListener {
    private static final int NEXT_RESOURCE_VERSION = 123456;
    private static final int INITIAL_RESOURCE_VERSION = 123;
    private static final String NAMESPACE = "testspace";
    private final static ClientHelper clientHelper = ClientHelper.getInstance();

    private static ClientHolder clientHolder;
    private List<Memento> mementos = new ArrayList<>();
    private List<Watch.Response<?>> callBacks = new ArrayList<>();

    private int resourceVersion = INITIAL_RESOURCE_VERSION;

    private V1ObjectMeta createMetaData() {
        return createMetaData("test", NAMESPACE);
    }

    private AtomicBoolean stopping = new AtomicBoolean(false);

    @Override
    public void allWatchesClosed() {
        stopping.set(true);
    }

    void recordCallBack(Watch.Response response) {
        callBacks.add(response);
    }

    @Before
    public void setUp() throws Exception {
        mementos.add(TestUtils.silenceOperatorLogger());
        mementos.add(StubWatchFactory.install());
        StubWatchFactory.setListener(this);
        clientHolder = clientHelper.take();
    }

    @After
    public void tearDown() throws Exception {
        clientHelper.recycle(clientHolder);
        for (Memento memento : mementos) memento.revert();
    }

    void sendInitialRequest(int initialResourceVersion) {
        StubWatchFactory.addCallResponses(createAddResponse(createObjectWithMetaData()));

        createAndRunWatcher(NAMESPACE, stopping, initialResourceVersion);
    }

    private Object createObjectWithMetaData() {
        return createObjectWithMetaData(createMetaData());
    }

    protected abstract <T> T createObjectWithMetaData(V1ObjectMeta metaData);

    @Test
    public void afterInitialRequest_watchIsClosed() throws Exception {
        sendInitialRequest(INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getNumCloseCalls(), equalTo(1));
    }

    private <T> Watch.Response createAddResponse(T object) {
        return WatchEvent.createAddedEvent(object).toWatchResponse();
    }

    private <T> Watch.Response createModifyResponse(T object) {
        return WatchEvent.createModifiedEvent(object).toWatchResponse();
    }

    private <T> Watch.Response createDeleteResponse(T object) {
        return WatchEvent.createDeleteEvent(object).toWatchResponse();
    }

    private Watch.Response createHttpGoneErrorResponse(int nextResourceVersion) {
        return WatchEvent.createErrorEvent(HTTP_GONE, nextResourceVersion).toWatchResponse();
    }

    @Test
    public void receivedEvents_areSentToListeners() throws Exception {
        Object object = createObjectWithMetaData();
        StubWatchFactory.addCallResponses(createAddResponse(object), createModifyResponse(object));

        createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

        assertThat(callBacks, contains(addEvent(object), modifyEvent(object)));
    }

    @Test
    public void afterFirstSetOfEvents_nextRequestSendsLastResourceVersion() throws Exception {
        Object object1 = createObjectWithMetaData();
        Object object2 = createObjectWithMetaData();
        Watch.Response[] firstSet = {createAddResponse(object1), createModifyResponse(object2)};
        int resourceAfterFirstSet = resourceVersion-1;
        StubWatchFactory.addCallResponses(firstSet);
        StubWatchFactory.addCallResponses(createAddResponse(createObjectWithMetaData()));

        createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(1), hasEntry("resourceVersion", Integer.toString(resourceAfterFirstSet)));
    }

    @Test
    public void afterHttpGoneError_nextRequestSendsIncludedResourceVersion() throws Exception {
        StubWatchFactory.addCallResponses(createHttpGoneErrorResponse(NEXT_RESOURCE_VERSION));
        StubWatchFactory.addCallResponses(createDeleteResponse(createObjectWithMetaData()));

        createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(1), hasEntry("resourceVersion", Integer.toString(NEXT_RESOURCE_VERSION)));
    }

    @Test
    public void afterDelete_nextRequestSendsIncrementedResourceVersion() throws Exception {
        StubWatchFactory.addCallResponses(createDeleteResponse(createObjectWithMetaData()));
        StubWatchFactory.addCallResponses(createAddResponse(createObjectWithMetaData()));

        createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(1), hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION+1)));
    }

    @Test
    public void afterExceptionDuringNext_closeWatchAndTryAgain() throws Exception {
        StubWatchFactory.throwExceptionOnNext(new RuntimeException(Watcher.HAS_NEXT_EXCEPTION_MESSAGE));
        StubWatchFactory.addCallResponses(createAddResponse(createObjectWithMetaData()));

        createAndRunWatcher(NAMESPACE, stopping, INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getNumCloseCalls(), equalTo(2));
    }

    @SuppressWarnings("SameParameterValue")
    private V1ObjectMeta createMetaData(String name, String namespace) {
        return new V1ObjectMeta().name(name).namespace(namespace).resourceVersion(getNextResourceVersion());
    }

    private String getNextResourceVersion() {
        return Integer.toString(resourceVersion++);
    }

    private void createAndRunWatcher(String nameSpace, AtomicBoolean stopping, int initialResourceVersion) {
        Watcher watcher = createWatcher(nameSpace, stopping, initialResourceVersion);
        watcher.waitForExit();
    }

    protected abstract Watcher createWatcher(String nameSpace, AtomicBoolean stopping, int initialResourceVersion);


}