// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

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
    public static boolean install(OperatorParams params) {

        // check for required params and assert here?
        String namespace = params.getNamespace();

        // Question - Do these here or in test using TestActions?
        //delete the namespace if it exists ?

        // create namespace
        new Namespace().name(namespace).create();

        // create service account
        if(!params.getServiceAccount().equals("default")) {
            // Kubernetes.createServiceAccount(params.getServiceAccount(), namespace);
        }

        // create domain namespaces here?

        // use kubernetes/samples/scripts/rest/generate-external-rest-identity.sh to create the
        // secret with the self-signed certificate and private key in the same namespace as op ns?

        // Question - end
        boolean success = false;
        if(new Helm.HelmBuilder(OPERATOR_HELM_REPO_URL).build().addRepo()) {
            success = new Helm.HelmBuilder(OPERATOR_CHART_NAME, params.getReleaseName())
                .namespace(namespace)
                .values(params.values())
                .build().install();
        }
        return success;
    }

    public static boolean upgrade(OperatorParams params) {
        return true;
    }

    public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
        return true;
    }

    public static boolean delete(String releaseName, String namespace) {
        return true;
    }
}
