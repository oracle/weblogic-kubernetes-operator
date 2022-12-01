// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common;

public final class AuxiliaryImageConstants {

  private AuxiliaryImageConstants() {
    //not called
  }

  public static final String AUXILIARY_IMAGE_TARGET_PATH = "/tmpAuxiliaryImage";
  public static final String AUXILIARY_IMAGE_VOLUME_NAME_PREFIX = "ai-vol-";
  public static final String AUXILIARY_IMAGE_VOLUME_NAME_OLD_PREFIX = "aux-image-volume-";
  public static final String AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT = "/weblogic-operator/scripts/auxImage.sh";
  public static final String AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX = "operator-aux-container";
  public static final String AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND
          = "cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH";
}
