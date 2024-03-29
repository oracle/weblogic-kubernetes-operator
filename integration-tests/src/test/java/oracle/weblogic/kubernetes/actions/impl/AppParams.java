// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;

import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;

/**
 * Contains the parameters for creating an application archive.
 */

public class AppParams {

  // A list of directories under resources/apps that are part of the application.
  // Note: the order of the directory names is significant. Files are copied into
  // the staging directory in this order. 
  private List<String> srcDirList;
  
  // The name of the final archive file.
  // The name of the first dir in srcDirList will be used if the appName is absent.
  private String appName;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect = true;

  // The directory for the app archive
  private String appArchiveDir = ARCHIVE_DIR;

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

  public AppParams appArchiveDir(String appArchiveDir) {
    this.appArchiveDir = appArchiveDir;
    return this;
  }

  public String appArchiveDir() {
    return appArchiveDir;
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
