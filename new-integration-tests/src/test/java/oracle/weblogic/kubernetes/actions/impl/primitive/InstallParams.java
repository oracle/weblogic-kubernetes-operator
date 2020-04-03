// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DEFAULT_WIT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.TYPE_WIT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_FILE_NAME;

/**
 * Contains the parameters for installing the WebLogic Image Tool or WebLogic Deploy Tool.
 */

public class InstallParams {

  // WIT or WDT
  private String type = TYPE_WIT;
  
  // The version of the tool
  private String version;
  
  // The download site location
  private String location;
  
  // Whether verify before download
  private boolean verify = true;
  
  // Whether the download zip file needs to be unziped
  private boolean unzip = false;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect = true;

  public InstallParams type(String type) {
    this.type = type;
    return this;
  }

  public String type() {
    return type;
  }

  public InstallParams version(String version) {
    this.version = version;
    return this;
  }

  public String version() {
    if (version == null) {
      return TYPE_WIT.equals(type) ? DEFAULT_WIT_VERSION : DEFAULT_WDT_VERSION;
    }
    return version;
  }

  public InstallParams location(String location) {
    this.location = location;
    return this;
  }

  public String location() {
    if (location == null) {
      return TYPE_WIT.equals(type) ? DEFAULT_WIT_DOWNLOAD_URL : DEFAULT_WDT_DOWNLOAD_URL;
    }
    return location;
  }

  public InstallParams verify(boolean verify) {
    this.verify = verify;
    return this;
  }

  public boolean verify() {
    return verify;
  }
  
  public InstallParams redirect(boolean redirect) {
    this.redirect = redirect;
    return this;
  }

  public boolean direct() {
    return redirect;
  }

  public InstallParams unzip(boolean unzip) {
    this.unzip = unzip;
    return this;
  }

  public boolean unzip() {
    return unzip;
  }

  public String fileName() {
    if (TYPE_WIT.equals(type)) {
      return WIT_FILE_NAME;
    } else {
      return WDT_FILE_NAME;
    }
  }
}
