// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

/**
 * Contains the parameters for creating an application archive.
 */

public class AppParams {

  // Location of the source code. 
  // This is the name of the directory under resources/apps for an application
  private String srcDir;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect = true;

  public AppParams defaults() {
    return this;
  }

  public AppParams srcDir(String srcDir) {
    this.srcDir = srcDir;
    return this;
  }

  public String srcDir() {
    return srcDir;
  }

  public AppParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }
}
