// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;

/**
 * Contains the parameters for creating an application archive.
 */

public class AppParams {

  // Locations of the source code. 
  // This are the directory names under resources/apps for an application.
  // Note: the order of the directory names are significant. Files are copied into
  // the staging directory in the order that corresponds to the order that the
  // directories are located in the list. 
  private List<String> srcDirList;
  
  // The name of the final ear file.
  // When there is only one srcDir in the srcDirList, this is
  // the name of that directory by default.
  private String appName;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect = true;
  
  public AppParams defaults() {
    return this;
  }

  public AppParams srcDirList(List<String> srcDirList) {
    this.srcDirList = srcDirList;
    return this;
  }

  public List<String> srcDirList() {
    return srcDirList;
  }

  public AppParams appName(String appName) {
    this.appName = appName;
    return this;
  }

  public String appName() {
    return appName;
  }

  public AppParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean redirect() {
    return redirect;
  }
}
