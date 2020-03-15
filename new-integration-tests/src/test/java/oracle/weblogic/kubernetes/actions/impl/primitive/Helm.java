package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

public class Helm {

    public static boolean addRepo(String repoUrl) {
        // do something!!
        return true;
    }

    public static boolean installRelease(
            String chart,
            String name,
            HashMap<String, String> values
    ) {
        // do something !!!
        return true;
    }

}
