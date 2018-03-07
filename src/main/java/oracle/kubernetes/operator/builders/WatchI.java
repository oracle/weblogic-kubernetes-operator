// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.builders;

import io.kubernetes.client.util.Watch;

import java.util.Iterator;

/**
 * An interface that allows test-stubbing of the Kubernetes Watch class.
 * @param <T> the generic object type
 */
public interface WatchI<T>
        extends Iterable<Watch.Response<T>>, Iterator<Watch.Response<T>>,
				 java.io.Closeable {
}
