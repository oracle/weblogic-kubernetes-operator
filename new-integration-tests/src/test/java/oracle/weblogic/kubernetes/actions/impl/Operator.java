// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;

public class Operator {

    /**
     * The URL of the Operator's Helm Repository.
     */
    private static String OPERATOR_HELM_REPO_URL = "https://oracle.github.io/weblogic-kubernetes-operator/charts";
    /**
     * The name of the Operator's Helm Chart (in the repository).
     */
    private static String OPERATOR_CHART_NAME = "weblogic-operator/weblogic-operator";


    // it is intended that methods in these impl classes are only ever called by
    // TestActions.java, and never directly from a test
    public static boolean install(String releaseName, String namespace, OperatorParams params) {
        // i would use primitives to do what i need to do
        boolean success = false;
        if(new Helm.HelmBuilder(OPERATOR_HELM_REPO_URL).build().addRepo()) {
            success = new Helm.HelmBuilder(OPERATOR_CHART_NAME, releaseName)
                .namespace(namespace)
                .values(params.values())
                .build().install();
        }
        return success;
    }

    public static boolean upgrade(String releaseName, String namespace, OperatorParams params) {
        return true;
    }

    public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
        return true;
    }

    public static boolean delete(String releaseName, String namespace) {
        return true;
    }
}
