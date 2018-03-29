// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.Pool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A test-time replacement for the factory that creates Watch objects, allowing
 * tests to specify directly the events they want returned from the Watch.
 */
public class StubWatchFactory implements WatchBuilder.WatchFactory {

    private static StubWatchFactory factory;
    private static List<Map<String,String>> recordedParameters;
    private static RuntimeException exceptionOnNext;
    private static AllWatchesClosedListener listener;

    private List<List<Watch.Response>> calls = new ArrayList<>();
    private int numCloseCalls;

    public static Memento install() throws NoSuchFieldException {
        factory = new StubWatchFactory();
        recordedParameters = new ArrayList<>();
        exceptionOnNext = null;
        return StaticStubSupport.install(WatchBuilder.class, "FACTORY", factory);
    }

    /**
     * Adds the events to be returned from a single call to Watch.next()
     * @param events the events; will be converted to Watch.Response objects
     */
    public static void addCallResponses(Watch.Response... events) {
        factory.calls.add(Arrays.asList(events));
    }

    public static int getNumCloseCalls() {
        return factory.numCloseCalls;
    }

    public static void setListener(AllWatchesClosedListener listener) {
        StubWatchFactory.listener = listener;
    }

    public static List<Map<String, String>> getRecordedParameters() {
        return recordedParameters;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> WatchI<T> createWatch(Pool<ApiClient> pool, CallParams callParams, Class<?> responseBodyType, BiFunction<ApiClient, CallParams, Call> function) throws ApiException {
        getRecordedParameters().add(recordedParams(callParams));

        if (exceptionOnNext == null)
            return (WatchI<T>) new WatchStub(calls.remove(0));
        else try {
            return new ExceptionThrowingWatchStub(exceptionOnNext);
        } finally {
            exceptionOnNext = null;
        }
    }

    private Map<String,String> recordedParams(CallParams callParams) {
        Map<String,String> result = new HashMap<>();
        if (callParams.getResourceVersion() != null)
            result.put("resourceVersion", callParams.getResourceVersion());
        if (callParams.getLabelSelector() != null)
            result.put("labelSelector", callParams.getLabelSelector());

        return result;
    }

    private StubWatchFactory() {
    }

    public static void throwExceptionOnNext(RuntimeException e) {
        exceptionOnNext = e;
    }

    public interface AllWatchesClosedListener {
        void allWatchesClosed();
    }

    class WatchStub implements WatchI {
        private List<Watch.Response> responses;
        private Iterator<Watch.Response> iterator;

        private WatchStub(List<Watch.Response> responses) {
            this.responses = responses;
            iterator = responses.iterator();
        }

        @Override
        public void close() throws IOException {
            numCloseCalls++;
            if (calls.size() == 0 && listener != null)
                listener.allWatchesClosed();
        }

        @Override
        public Iterator<Watch.Response> iterator() {
            return responses.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Object next() {
            return iterator.next();
        }
    }

    class ExceptionThrowingWatchStub extends WatchStub {
        private RuntimeException exception;

        private ExceptionThrowingWatchStub(RuntimeException exception) {
            super(new ArrayList<>());
            this.exception = exception;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Object next() {
            throw exception;
        }
    }
}
