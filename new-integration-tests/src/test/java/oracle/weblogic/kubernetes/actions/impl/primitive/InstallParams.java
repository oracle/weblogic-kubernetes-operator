// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;

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
  
  private final static String TMP_FILE_NAME = "temp-download-file";

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
    return getActualVersion(version);
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
  
  private String getActualVersion(String version) {
    if (version == null || version.equalsIgnoreCase("Latest")) {
      String command = String.format(
          "curl -fL %s/releases/release/latest -o %s/%s", 
          location(), 
          version(),
          DOWNLOAD_DIR,
          TMP_FILE_NAME);
      
      boolean getLatest = Command.withParams(defaultCommandParams().command(command)).execute();
      command = String.format("cat $tempfile | grep 'releases/download'");
          // "cat $tempfile | grep 'releases/download' | awk '{ split($0,a,/href="/); print a[2]}' | cut -d\/ -f 6"); 
      
      boolean getVersion = Command.withParams(
          defaultCommandParams()
              .command(command)
              .saveStdOut(true))
          .execute();
      if (getLatest && getVersion) {
        String ver = param.stdOut();
    	int index = ver.lastIndexOf("/");
    	ver = ver.substring(index+1);
    	ver.substring(ver.indexOf("/"));
        return ver;
      } else {
        return null;
      }
    }
    return version;
  }

}
