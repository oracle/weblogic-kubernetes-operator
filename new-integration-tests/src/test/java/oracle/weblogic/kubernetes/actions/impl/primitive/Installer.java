// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

// Implementation of all of the installation primitives that the IT test needs.

public class Installer {
    private InstallParams params;

    public Installer with(InstallParams params) {
        this.params = params;
        return this;
    }

    public static boolean download() {
        // do something with the command!!!
        return true;
    }
 
}
