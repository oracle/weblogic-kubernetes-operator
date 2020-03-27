// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.addRepo;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.installRelease;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.deleteRelease;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.upgradeRelease;

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
    public static boolean install() {
        // i would use primitives to do what i need to do
        boolean success = false;
        if (addRepo(OPERATOR_HELM_REPO_URL)) {
            success = installRelease(
                    OPERATOR_CHART_NAME,
                    "weblogic-operator",
                    new HashMap<>());
        }
        return success;
    }

    public static boolean upgrade(HashMap<String, String> values) {
        return upgradeRelease(OPERATOR_CHART_NAME, "weblogic-operator", values);
    }
    
    public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
        return true;
    }

    public static boolean delete() {
        return deleteRelease(OPERATOR_CHART_NAME);
    }
}
