// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;

/**
 * Definition of an interface that returns values that the Main class requires.
 */
public interface MainDelegate extends CoreDelegate {

  String getPrincipal();

  DomainProcessor getDomainProcessor();

  DomainNamespaces getDomainNamespaces();

  AtomicReference<V1CustomResourceDefinition> getCrdReference();
}
