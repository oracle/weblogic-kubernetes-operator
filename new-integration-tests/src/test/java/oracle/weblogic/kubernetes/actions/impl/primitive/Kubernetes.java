// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// TODO ryan - in here we want to implement all of the kubernetes
// primitives that we need, using the API, not spawning a process
// to run kubectl.

public class Kubernetes {

    public static Random random = new Random(System.currentTimeMillis());

    // ------------------------  deployments -----------------------------------

    public static boolean createDeployment(String deploymentYaml) {
        // do something with the command!!!
        return true;
    }

    public static List listDeployments() {
        return new ArrayList();
    }

    // --------------------------- pods -----------------------------------------


    // --------------------------- namespaces -----------------------------------

    public static boolean createNamespace(String name) {
        return true;
    }

    /**
     * Create a new namespace with a "unique" name.
     * This method will create a "unique" name by choosing a random name from
     * 26^4 possible combinations, and create a namespace using that random name.
     * @return the name of the new namespace.
     */
    public static String createUniqueNamespace() {
        char[] name = new char[4];
        for (int i = 0; i < name.length; i++) {
            name[i] = (char)(random.nextInt(25) + (int)'a');
        }
        String namespace = "ns-" + new String( name);
        if (createNamespace(namespace)) {
            return namespace;
        } else {
            return "";
        }
    }

    // --------------------------- create, delete resource using yaml --------------------------

    public static boolean create(String yaml) {
        return true;
    }

    public static boolean delete(String yaml) {
        return true;
    }

    // --------------------------- config map ---------------------------

    public static boolean createConfigMap(String cmName, String namespace, String fromFile) {
        return true;
    }

    public static boolean deleteConfigMap(String cmName, String namespace) {
        return true;
    }

    // --------------------------- secret ---------------------------

    public static boolean createSecret(String secretName,
                                       String username, String password, String namespace) {
        return true;
    }

    public static boolean deleteSecret(String cmName, String namespace) {
        return true;
    }

    // --------------------------- pv/pvc ---------------------------

    public static boolean deletePv(String pvName) {
        return true;
    }

    public static boolean deletePvc(String pvcName, String namespace) {
        return true;
    }
    // --------------------------
}
