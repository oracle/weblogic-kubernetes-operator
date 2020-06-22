// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

/**
 * Contains the parameters for installing the WebLogic Image Tool or WebLogic Deploy Tool.
 */

public class InstallParams {

  // WIT or WDT
  private String type;
  
  // The version of the tool
  private String version;
  
  // The download site location
  private String location;
  
  // The installer file name
  private String fileName;

  // Whether verify before download
  private boolean verify = true;
  
  // Whether the download zip file needs to be unziped
  private boolean unzip = false;
  
  // Whether the output of the command is redirected to system out
  private boolean redirect = true;

  public InstallParams defaults() {
    return this;
  }

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
    return version;
  }

  public InstallParams location(String location) {
    this.location = location;
    return this;
  }

  public String location() {
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

  public boolean redirect() {
    return redirect;
  }

  public InstallParams unzip(boolean unzip) {
    this.unzip = unzip;
    return this;
  }

  public boolean unzip() {
    return unzip;
  }

  public InstallParams fileName(String fileName) {
    this.fileName = fileName;
    return this;
  }
  
  public String fileName() {
    return fileName;
  }
}
