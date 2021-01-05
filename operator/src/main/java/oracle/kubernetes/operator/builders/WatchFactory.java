// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.lang.reflect.Type;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Watchable;
import okhttp3.Call;

/**
 * An interface whose default implementation creates a Kubernetes Watch object, and which is used in unit tests
 * to create a test stub.
 * @param <T> the Kubernetes type to watch
 */
@FunctionalInterface
public interface WatchFactory<T> {
  Watchable<T> createWatch(ApiClient client, Call call, Type type);
}
