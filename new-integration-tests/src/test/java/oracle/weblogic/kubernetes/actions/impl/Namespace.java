// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.Random;

import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Namespace {
    public static Random random = new Random(System.currentTimeMillis());
    private String name;

    /* public static String createUniqueNamespace() {
        return Kubernetes.createUniqueNamespace();
    }

    public static boolean createNamespace(String name) {
        return Kubernetes.createNamespace(name);
    } */

    public Namespace name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Generates a "unique" name by choosing a random name from
     * 26^4 possible combinations
     * @return Namespace
     */
    public static String uniqueName() {
        char[] nsName = new char[4];
        for (int i = 0; i < nsName.length; i++) {
            nsName[i] = (char)(random.nextInt(25) + (int)'a');
        }
        String uniqueName = "ns-" + new String(nsName);
        return uniqueName;
    }
    public boolean create() {
        return Kubernetes.createNamespace(name);
    }

}
