// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Secret {

    public static boolean create(String secretName, String username, String password, String namespace) throws ApiException {
        return Kubernetes.createSecret(secretName, username, password, namespace);
    }
    public static boolean delete(String secretName, String namespace) throws ApiException {
        return Kubernetes.deleteSecret(secretName, namespace);
    }
}
