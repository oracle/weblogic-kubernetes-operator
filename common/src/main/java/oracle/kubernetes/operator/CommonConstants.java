// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public class CommonConstants {
  public static String COMPATIBILITY_MODE = "compatibility-mode-";
  public static final String API_VERSION_V9 = "weblogic.oracle/v9";
  public static final String API_VERSION_V8 = "weblogic.oracle/v8";

  public static String SCRIPTS_VOLUME = "weblogic-scripts-cm-volume";
  public static String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";

  public static String LATEST_IMAGE_SUFFIX = ":latest";
  public static String ALWAYS_IMAGEPULLPOLICY = ImagePullPolicy.Always.name();
  public static String IFNOTPRESENT_IMAGEPULLPOLICY = ImagePullPolicy.IfNotPresent.name();

}
