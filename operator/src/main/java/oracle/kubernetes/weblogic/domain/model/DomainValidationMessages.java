// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.utils.OperatorUtils;

class DomainValidationMessages {

  /**
   * Returns a validation message indicating that more than one managed server spec has the same effective name
   * after DNS-1123 conversion.
   * @param serverName the duplicate server name
   * @return the localized message
   */
  static String duplicateServerName(@Nonnull String serverName) {
    return getMessage(MessageKeys.DUPLICATE_SERVER_NAME_FOUND, serverName);
  }

  /**
   * Returns a validation message indicating that more than one cluster spec has the same effective name
   * after DNS-1123 conversion.
   * @param clusterName the duplicate cluster name
   * @return the localized message
   */
  static String duplicateClusterName(@Nonnull String clusterName) {
    return getMessage(MessageKeys.DUPLICATE_CLUSTER_NAME_FOUND, clusterName);
  }

  /**
   * Returns a validation message indicating that a specified volume mount's path is not absolute.
   * @param mount the problematic volume mount
   * @return the localized message
   */
  static String badVolumeMountPath(@Nonnull V1VolumeMount mount) {
    return getMessage(MessageKeys.BAD_VOLUME_MOUNT_PATH, mount.getMountPath(), mount.getName());
  }

  /**
   * Returns a validation message indicating that none of the additional volume mounts contains a path which
   * includes the log home.
   * @param logHome the log home to be used
   * @return the localized message
   */
  static String logHomeNotMounted(@Nonnull String logHome) {
    return getMessage(MessageKeys.LOG_HOME_NOT_MOUNTED, logHome);
  }

  private static String getMessage(String key, Object... parameters) {
    MessageFormat formatter = new MessageFormat("");
    formatter.applyPattern(getBundleString(key));
    return formatter.format(parameters);
  }

  private static String getBundleString(String key) {
    return ResourceBundle.getBundle("Operator").getString(key);
  }

  static String reservedVariableNames(String prefix, List<String> reservedNames) {
    MessageFormat formatter = new MessageFormat("");
    formatter.applyPattern(getBundleString(MessageKeys.RESERVED_ENVIRONMENT_VARIABLES));
    formatter.setFormats(new Format[]{getEnvNoun(), null, null, getToBe()});
    return formatter.format(new Object[] {
        reservedNames.size(),
        OperatorUtils.joinListGrammatically(reservedNames),
        prefix + ".serverPod.env",
        reservedNames.size()});
  }

  private static ChoiceFormat getEnvNoun() {
    return new ChoiceFormat(new double[] {1, 2},
                            new String[] {getBundleString("oneEnvVar"), getBundleString("multipleEnvVars")});
  }

  private static ChoiceFormat getToBe() {
    return new ChoiceFormat(new double[] {1, 2},
                            new String[] {getBundleString("singularToBe"), getBundleString("pluralToBe")});
  }

  static String noSuchSecret(String secretName, String namespace, SecretType type) {
    return getMessage(MessageKeys.SECRET_NOT_FOUND, secretName, namespace, type);
  }

  static String missingRequiredSecret(String secret) {
    return getMessage(MessageKeys.SECRET_NOT_SPECIFIED, secret);
  }

  static String missingRequiredOpssSecret(String secret) {
    return getMessage(MessageKeys.OPSS_SECRET_NOT_SPECIFIED, secret);
  }

  static String illegalSecretNamespace(String namespace) {
    return getMessage(MessageKeys.ILLEGAL_SECRET_NAMESPACE, namespace);
  }

  static String illegalSitConfigForMii(String configOverrides) {
    return getMessage(MessageKeys.ILLEGAL_SIT_CONFIG_MII, configOverrides);
  }

  static String noSuchModelConfigMap(String configMapName, String namespace) {
    return getMessage(MessageKeys.MODEL_CONFIGMAP_NOT_FOUND, configMapName, namespace);
  }

  static String cannotExposeDefaultChannelIstio(String channelName) {
    return getMessage(MessageKeys.CANNOT_EXPOSE_DEFAULT_CHANNEL_ISTIO, channelName);
  }

  public static String exceedMaxIntrospectorJobName(String domainUid, String result, int limit) {
    return getMessage(MessageKeys.ILLEGAL_INTROSPECTOR_JOB_NAME_LENGTH, domainUid, result, limit);
  }

  public static String exceedMaxClusterServiceName(String domainUid, String clusterName, String result, int limit) {
    return getMessage(MessageKeys.ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH, domainUid, clusterName, result, limit);
  }

  public static String exceedMaxServerServiceName(String domainUid, String serverName, String result, int limit) {
    return getMessage(MessageKeys.ILLEGAL_SERVER_SERVICE_NAME_LENGTH, domainUid, serverName, result, limit);
  }

  public static String exceedMaxExternalServiceName(
      String domainUid, String adminServerName, String result, int limit) {
    return getMessage(MessageKeys.ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH, domainUid, adminServerName, result, limit);
  }
}
