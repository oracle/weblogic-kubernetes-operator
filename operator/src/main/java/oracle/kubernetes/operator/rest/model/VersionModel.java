// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/** VersionModel describes a version of the WebLogic operator REST api. */
public class VersionModel extends ItemModel {

  private String version;
  private boolean latest;
  private String lifecycle;

  /** Construct an empty VersionModel. */
  public VersionModel() {
  }

  /**
   * Construct a populated VersionModel.
   *
   * @param version - the name of this version of WebLogic operator REST api.
   * @param latest - whether this is the latest version of the WebLogic operator REST api.
   * @param lifecycle - the lifecycle of this version of the WebLogic operator REST api, either
   *     'activate' or 'deprecated'.
   */
  public VersionModel(String version, boolean latest, String lifecycle) {
    setVersion(version);
    setLatest(latest);
    setLifecycle(lifecycle);
  }

  /**
   * Get the name of this version of the WebLogic operator REST api.
   *
   * @return the version name.
   */
  public String getVersion() {
    return version;
  }

  /**
   * Set the name of this version of the WebLogic operator REST api.
   *
   * @param version - the version name.
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Get whether or not this is the latest version of the WebLogic operator REST api.
   *
   * @return whether this is the latest version.
   */
  public boolean isLatest() {
    return latest;
  }

  /**
   * Set whether or not this is the latest version of the WebLogic operator REST api.
   *
   * @param latest - whether this is the latest version.
   */
  public void setLatest(boolean latest) {
    this.latest = latest;
  }

  /**
   * Get the lifecycle of this version of the WebLogic operator REST api.
   *
   * @return the lifecycle, either 'current' or 'deprecated'.
   */
  public String getLifecycle() {
    return lifecycle;
  }

  /**
   * Set the lifecycle of this version of the WebLogic operator REST api.
   *
   * @param lifecycle - either 'current' or 'deprecated'.
   */
  public void setLifecycle(String lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Override
  protected String propertiesToString() {
    return "version="
        + getVersion()
        + ", latest="
        + isLatest()
        + ", lifecycle="
        + getLifecycle()
        + ", "
        + super.propertiesToString();
  }
}
