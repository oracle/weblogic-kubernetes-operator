// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolume {

    public static boolean create(String pvYaml) {
        return Kubernetes.create(pvYaml);
    }

    public static boolean delete(String pvName) throws ApiException { return Kubernetes.deletePv(pvName); }

}
