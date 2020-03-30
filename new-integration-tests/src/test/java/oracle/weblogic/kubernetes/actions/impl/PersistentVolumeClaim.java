// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolumeClaim {

    public static boolean create(String pvcYaml) {
        return Kubernetes.create(pvcYaml);
    }

    public static boolean delete(String pvcName, String namespace) throws ApiException {
        return Kubernetes.deletePvc(pvcName, namespace);
    }
}
