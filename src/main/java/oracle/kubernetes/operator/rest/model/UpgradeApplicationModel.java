// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

/**
 * UpgradeApplicationModel describes the input data for each application to the WebLogic application upgrade operation.
 */
public class UpgradeApplicationModel extends BaseModel {

  private String applicationName;
  private String patchedLocation;
  private String backupLocation;

  /**
   * Get the name of the application to be upgraded..
   * @return the name of the application..
   */
  public String getApplicationName() {
    return applicationName;
  }

  /**
   * Get the location of the new version of application.
   * @return
   */
  public String getPatchedLocation() {
    return patchedLocation;
  }

  /**
   * Get the location the current version of application to be backed up.
   * @return
   */
  public String getBackupLocation() {
    return backupLocation;
  }

  /**
   * Set the name of the application to be upgraded..
   * @param applicationName - the name of the application to be upgraded.
   */
  public void setApplicationName(String applicationName) {
    this.applicationName = applicationName;
  }

  /**
   * Set the path where the new version of application to be upgraded is located.
   * @param patchedLocation  The location of the new version of application to be upgraded.
   */
  public void setPatchedLocation(String patchedLocation) {
    this.patchedLocation = patchedLocation;
  }

  /**
   * Set the backup path so the current version of application can be backed up.
   * @param backupLocation  The backup location for the current version of application deployed.
   */
  public void setBackupLocation(String backupLocation) {
    this.backupLocation = backupLocation;
  }

  @Override
  protected String propertiesToString() {
    return "applicationName=" + getApplicationName() + ",patchedLocation=" + getPatchedLocation() + ", backupLocation=" + getBackupLocation(); // super has no properties
  }
}
