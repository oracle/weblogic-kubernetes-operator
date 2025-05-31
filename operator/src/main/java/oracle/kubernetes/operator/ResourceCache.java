// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ConcurrentMap;

public interface ResourceCache extends NamespacedResourceCache {
  ConcurrentMap<String, NamespacedResourceCache> getNamespaces();

  NamespacedResourceCache findNamespace(String namespace);
}
