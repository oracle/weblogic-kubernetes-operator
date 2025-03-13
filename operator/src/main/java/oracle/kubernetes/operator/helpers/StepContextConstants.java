// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

public interface StepContextConstants {

  String OPSS_KEYPASSPHRASE_VOLUME = "opss-keypass-volume";
  String OPSS_WALLETFILE_VOLUME = "opss-walletfile-volume";
  String SECRETS_VOLUME = "weblogic-credentials-volume";
  String DEBUG_CM_VOLUME = "weblogic-domain-debug-cm-volume";
  String INTROSPECTOR_VOLUME = "weblogic-domain-introspect-cm-volume";
  String RUNTIME_ENCRYPTION_SECRET_VOLUME = "weblogic-domain-runtime-encryption-volume";
  String WDT_MODEL_ENCRYPTION_PASSPHRASE_VOLUME = "wdt-encryption-passphrase-volume";
  String FLUENTD_CONFIGMAP_VOLUME = "weblogic-fluentd-configmap-volume";
  String OLD_FLUENTD_CONFIGMAP_NAME = "weblogic-fluentd-configmap";
  String FLUENTD_CONFIGMAP_NAME_SUFFIX = "-" + OLD_FLUENTD_CONFIGMAP_NAME;
  String FLUENTD_CONTAINER_NAME = "fluentd";
  String FLUENTD_CONFIG_DATA_NAME = "fluentd.conf";
  String FLUENTBIT_CONTAINER_NAME = "fluent-bit";
  String FLUENTBIT_CONFIG_DATA_NAME = "fluent-bit.conf";
  String FLUENTBIT_PARSER_CONFIG_DATA_NAME = "parsers.conf";
  String FLUENTBIT_CONFIGMAP_VOLUME = "weblogic-fluentbit-configmap-volume";
  String FLUENTBIT_CONFIGMAP_NAME_SUFFIX = "-" + "weblogic-fluentbit-configmap";
  String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  String OPSS_KEY_MOUNT_PATH = "/weblogic-operator/opss-walletkey-secret";
  String WDT_MODEL_ENCRYPTION_PASSPHRASE_MOUNT_PATH = "/weblogic-operator/wdt-encryption-passphrase";
  String RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH = "/weblogic-operator/model-runtime-secret";
  String OVERRIDE_SECRETS_MOUNT_PATH = "/weblogic-operator/config-overrides-secrets";
  String OVERRIDES_CM_MOUNT_PATH = "/weblogic-operator/config-overrides";
  String WDTCONFIGMAP_MOUNT_PATH = "/weblogic-operator/wdt-config-map";
  String OPSS_WALLETFILE_MOUNT_PATH = "/weblogic-operator/opss-walletfile-secret";
  String DEBUG_CM_MOUNTS_PATH = "/weblogic-operator/debug";
  String NODEMGR_HOME = "/u01/nodemanager";

  String INIT_DOMAIN_ON_PV_CONTAINER = "create-dh-dir";
  String INIT_DOMAIN_ON_PV_SCRIPT = "/weblogic-operator/scripts/initializeDomainHomeOnPV.sh";
  int DEFAULT_SUCCESS_THRESHOLD = 1;

  @SuppressWarnings("OctalInteger")
  int ALL_READ_AND_EXECUTE = 0555;

  // Access modes for PV and PVC
  String READ_WRITE_MANY = "ReadWriteMany";

}
