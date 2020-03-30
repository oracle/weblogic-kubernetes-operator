// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;

public class Traefik {
    /**
     * The URL of the Traefik's Helm Repository.
     */
    private static String TRAEFIK_HELM_REPO_URL = "";
    /**
     * The name of the Traefik Helm Chart (in the repository).
     */
    private static String TRAEFIK_CHART_NAME = "traefik";

    public static boolean install(String valuesYaml) {
        boolean success = false;
        if(new Helm.HelmBuilder(TRAEFIK_HELM_REPO_URL).build().addRepo()) {
            success = new Helm.HelmBuilder(TRAEFIK_CHART_NAME, "traefik-operator")
                .namespace("traefik")
                .values(new HashMap<>())
                .build().install();
        }
        return success;
    }

    public static boolean createIngress(String valuesYaml) {
        return true;
    }
}
