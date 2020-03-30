// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

// Implementation of all of the WIT primitives that the IT test needs.

public class WebLogicImageTool {

    private WITParams params;

    // use the default values
    public WebLogicImageTool with() {
      // fill in the default values!!
      return this;
    }

    public WebLogicImageTool with(WITParams params) {
      this.params = params;
      return this;
    }

    public static boolean updateImage() {
        // use WIT to update the base image with additional components!!!
        return true;
    }
 
}
