// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.builders;

import io.kubernetes.client.util.Watch;

import java.io.IOException;
import java.util.Iterator;

/**
 * A pass-through implementation of the Kubernetes Watch class which implements a facade
 * interface.
 */
public class WatchImpl<T> implements WatchI<T> {
    private Watch<T> impl;

    WatchImpl(Watch<T> impl) {
        this.impl = impl;
    }

    @Override
    public void close() throws IOException {
        impl.close();
    }

    @Override
    public Iterator<Watch.Response<T>> iterator() {
        return impl.iterator();
    }

    @Override
    public boolean hasNext() {
        return impl.hasNext();
    }

    @Override
    public Watch.Response<T> next() {
        return impl.next();
    }
}
