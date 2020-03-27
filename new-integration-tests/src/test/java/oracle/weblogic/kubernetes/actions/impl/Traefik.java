// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.installRelease;

public class Traefik {

    public static boolean install(String valuesYaml) {
        return installRelease(
            valuesYaml,
            "traefik-operator",
            new HashMap<>());
    }

    public static boolean createIngress(String valuesYaml) {
        return true;
    }
}
