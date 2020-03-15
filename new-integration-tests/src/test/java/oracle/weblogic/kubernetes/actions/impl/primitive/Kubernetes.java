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

}
