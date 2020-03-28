// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class ConfigMap {

    public static boolean create(String cmName, String namespace,String fromFile) {
        return Kubernetes.createConfigMap(cmName, namespace, fromFile);
    }

    public static boolean delete(String cmName, String namespace) {
        return Kubernetes.deleteConfigMap(cmName, namespace);
    }
}
