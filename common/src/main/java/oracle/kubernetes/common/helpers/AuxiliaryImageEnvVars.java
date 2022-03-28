// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.helpers;

public class AuxiliaryImageEnvVars {

  private AuxiliaryImageEnvVars() {
    //not called
  }

  /** The auxiliary image path. */
  public static final String AUXILIARY_IMAGE_PATH = "AUXILIARY_IMAGE_PATH";

  /** The auxiliary image paths. */
  public static final String AUXILIARY_IMAGE_PATHS = "AUXILIARY_IMAGE_PATHS";

  /** The auxiliary image container command. */
  public static final String AUXILIARY_IMAGE_COMMAND = "AUXILIARY_IMAGE_COMMAND";

  /** The auxiliary image path. */
  public static final String AUXILIARY_IMAGE_MOUNT_PATH = "AUXILIARY_IMAGE_MOUNT_PATH";

  /** The auxiliary image target path. */
  public static final String AUXILIARY_IMAGE_TARGET_PATH = "AUXILIARY_IMAGE_TARGET_PATH";

  /** The auxiliary image container source WDT install home. */
  public static final String AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME = "AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME";

  /** The auxiliary image container source model home. */
  public static final String AUXILIARY_IMAGE_SOURCE_MODEL_HOME = "AUXILIARY_IMAGE_SOURCE_MODEL_HOME";

  /** The auxiliary image container image. */
  public static final String AUXILIARY_IMAGE_CONTAINER_IMAGE = "AUXILIARY_IMAGE_CONTAINER_IMAGE";

  /** The auxiliary image container name. */
  public static final String AUXILIARY_IMAGE_CONTAINER_NAME = "AUXILIARY_IMAGE_CONTAINER_NAME";

}
