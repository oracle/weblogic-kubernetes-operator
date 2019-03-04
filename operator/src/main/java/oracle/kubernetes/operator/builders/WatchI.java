// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.util.Watch;
import java.util.Iterator;

/**
 * An iterator over watch responses from the server. These objects maintain resources, which will be
 * release when #close() is called.
 *
 * @param <T> the generic object type
 */
public interface WatchI<T>
    extends Iterable<Watch.Response<T>>, Iterator<Watch.Response<T>>, java.io.Closeable {}
