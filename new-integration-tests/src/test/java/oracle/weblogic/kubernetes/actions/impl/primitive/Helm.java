// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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

    public static boolean upgradeRelease(
        String chart,
        String name,
        HashMap<String, String> values
    ) {
        // do something !!!
        return true;
    }

    public static boolean deleteRelease(String name) {
        return true;
    }

}
