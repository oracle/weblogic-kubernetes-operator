package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Namespace {

    public static String createUniqueNamespace() {
        return Kubernetes.createUniqueNamespace();
    }

    public static boolean createNamespace(String name) {
        return Kubernetes.createNamespace(name);
    }

}
