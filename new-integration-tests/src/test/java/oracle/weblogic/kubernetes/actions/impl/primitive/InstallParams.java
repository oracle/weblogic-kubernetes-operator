// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

// Presents all parameters that the test InstallParams needs.

public class InstallParams {

  private String type;
  private String version;
  private String location;

  public InstallParams type(String type) {
    this.type = type;
    return this;
  }

  public InstallParams version(String version) {
    this.version = version;
    return this;
  }

  public InstallParams location(String location) {
    this.location = location;
    return this;
  }

}
