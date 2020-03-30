// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import java.util.List;

public class Namespace {

    public static String createUniqueNamespace() {
        return Kubernetes.createUniqueNamespace();
    }

    public static boolean createNamespace(String name) {
        return Kubernetes.createNamespace(name);
    }

    public static List<String> listNamespaces() {
        return Kubernetes.listNamespaces();
    }

    public static boolean deleteNamespace(String name) {
        return Kubernetes.deleteNamespace(name);
    }
}
