// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common;

public class CommonConstants {

  public static final String CRD = "CRD";

  private CommonConstants() {
    //not called
  }

  public static final String COMPATIBILITY_MODE = "compat-";
  public static final String WLS_SHARED = "wls-shared-";
  public static final String API_VERSION_V9 = "weblogic.oracle/v9";
  public static final String API_VERSION_V8 = "weblogic.oracle/v8";

  public static final String SCRIPTS_VOLUME = "weblogic-scripts-cm-volume";
  public static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";

  public static final String SECRETS_WEBHOOK_CERT = "/secrets/webhookCert";
  public static final String SECRETS_WEBHOOK_KEY = "/secrets/webhookKey";

  public static final String LATEST_IMAGE_SUFFIX = ":latest";

}
