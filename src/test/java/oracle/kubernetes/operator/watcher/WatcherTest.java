// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.watcher;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class WatcherTest implements StubWatchFactory.AllWatchesClosedListener {
    private static final int NEXT_RESOURCE_VERSION = 123456;
    private static final int INITIAL_RESOURCE_VERSION = 123;
    private static final String API_VERSION = "weblogic.oracle/v1";
    private static final String NAMESPACE = "testspace";
    private final static ClientHelper clientHelper = ClientHelper.getInstance();

    private static ClientHolder clientHolder;
    private List<Memento> mementos = new ArrayList<>();
    private List<Watch.Response<V1Pod>> callBacks = new ArrayList<>();

    private int resourceVersion = INITIAL_RESOURCE_VERSION;
    private final V1Pod pod = new V1Pod().apiVersion(API_VERSION).kind("Pod").metadata(createMetaData("pod4", NAMESPACE));
    private AtomicBoolean stopping;

    @Override
    public void allWatchesClosed() {
        stopping.set(true);
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

    @Test
    public void initialRequest_specifiesStartingResourceVersion() throws Exception {
        StubWatchFactory.addCallResponses(createAddResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(0), hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)));
    }

    @Test
    public void afterInitialRequest_watchIsClosed() throws Exception {
        StubWatchFactory.addCallResponses(createAddResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

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
        StubWatchFactory.addCallResponses(createAddResponse(pod), createModifyResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

        assertThat(callBacks, contains(addEvent(pod), modifyEvent(pod)));
    }

    @Test
    public void afterFirstSetOfEvents_nextRequestSendsLastResourceVersion() throws Exception {
        Watch.Response[] firstSet = {createAddResponse(pod), createModifyResponse(pod)};
        int resourceAfterFirstSet = resourceVersion-1;
        StubWatchFactory.addCallResponses(firstSet);
        StubWatchFactory.addCallResponses(createDeleteResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(1), hasEntry("resourceVersion", Integer.toString(resourceAfterFirstSet)));
    }

    @Test
    public void afterHttpGoneError_nextRequestSendsIncludedResourceVersion() throws Exception {
        StubWatchFactory.addCallResponses(createHttpGoneErrorResponse(NEXT_RESOURCE_VERSION));
        StubWatchFactory.addCallResponses(createDeleteResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getRecordedParameters().get(1), hasEntry("resourceVersion", Integer.toString(NEXT_RESOURCE_VERSION)));
    }

    @Test
    public void afterExceptionDuringNext_closeWatchAndTryAgain() throws Exception {
        StubWatchFactory.throwExceptionOnNext(new RuntimeException(Watcher.HAS_NEXT_EXCEPTION_MESSAGE));
        StubWatchFactory.addCallResponses(createAddResponse(pod));

        createAndRunPodWatcher(INITIAL_RESOURCE_VERSION);

        assertThat(StubWatchFactory.getNumCloseCalls(), equalTo(2));
    }

    @SuppressWarnings("SameParameterValue")
    private V1ObjectMeta createMetaData(String name, String namespace) {
        return new V1ObjectMeta().name(name).namespace(namespace).resourceVersion(getNextResourceVersion());
    }

    private String getNextResourceVersion() {
        return Integer.toString(resourceVersion++);
    }

    private void createAndRunPodWatcher(int initialResourceVersion) {
        stopping = new AtomicBoolean(false);
        Watching<V1Pod> w = new Watching<V1Pod>() {
            @Override
            public WatchI<V1Pod> initiateWatch(String resourceVersion) throws ApiException {
                return new WatchBuilder(clientHolder).withResourceVersion(resourceVersion).createPodWatch(NAMESPACE);
            }

            @Override
            public boolean isStopping() {
                return stopping.get();
            }

            @Override
            public void eventCallback(Watch.Response<V1Pod> response) {
                callBacks.add(response);
            }

        };

        try {
            new Watcher<>(w, Integer.toString(initialResourceVersion)).start().join(1000);
        } catch (InterruptedException ignore) {
        }
    }



}