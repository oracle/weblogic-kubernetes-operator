// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

/**
 * Message keys used to look up log messages from the resource bundle. The use of message keys makes
 * the code more readable.
 */
public class MessageKeys {
  public static final String OPERATOR_STARTED = "WLSKO-0000";
  public static final String CREATING_API_CLIENT = "WLSKO-0001";
  public static final String K8S_MASTER_URL = "WLSKO-0002";
  public static final String OPERATOR_SHUTTING_DOWN = "WLSKO-0005";
  public static final String EXCEPTION = "WLSKO-0006";
  public static final String CREATING_CRD = "WLSKO-0012";
  public static final String LISTING_DOMAINS = "WLSKO-0014";
  public static final String SECRET_NOT_FOUND = "WLSKO-0018";
  public static final String RETRIEVING_SECRET = "WLSKO-0019";
  public static final String SECRET_DATA_NOT_FOUND = "WLSKO-0020";
  public static final String WLS_CONFIGURATION_READ = "WLSKO-0021";
  public static final String JSON_PARSING_FAILED = "WLSKO-0026";
  public static final String SERVICE_URL = "WLSKO-0027";
  public static final String NO_WLS_SERVER_IN_CLUSTER = "WLSKO-0028";
  public static final String VERIFY_ACCESS_START = "WLSKO-0029";
  public static final String VERIFY_ACCESS_DENIED = "WLSKO-0030";
  public static final String NAMESPACE_IS_DEFAULT = "WLSKO-0031";
  public static final String STARTING_LIVENESS_THREAD = "WLSKO-0034";
  public static final String COULD_NOT_CREATE_LIVENESS_FILE = "WLSKO-0035";
  public static final String REST_AUTHENTICATION_MISSING_ACCESS_TOKEN = "WLSKO-0037";
  public static final String PROCESSING_DOMAIN = "WLSKO-0038";
  public static final String WATCH_DOMAIN = "WLSKO-0039";
  public static final String WATCH_DOMAIN_DELETED = "WLSKO-0040";
  public static final String ADMIN_POD_CREATED = "WLSKO-0041";
  public static final String ADMIN_POD_REPLACED = "WLSKO-0042";
  public static final String ADMIN_POD_EXISTS = "WLSKO-0043";
  public static final String ADMIN_SERVICE_CREATED = "WLSKO-0044";
  public static final String ADMIN_SERVICE_REPLACED = "WLSKO-0045";
  public static final String ADMIN_SERVICE_EXISTS = "WLSKO-0046";
  public static final String MANAGED_POD_CREATED = "WLSKO-0047";
  public static final String MANAGED_POD_REPLACED = "WLSKO-0048";
  public static final String MANAGED_POD_EXISTS = "WLSKO-0049";
  public static final String MANAGED_SERVICE_CREATED = "WLSKO-0050";
  public static final String MANAGED_SERVICE_REPLACED = "WLSKO-0051";
  public static final String MANAGED_SERVICE_EXISTS = "WLSKO-0052";
  public static final String CLUSTER_SERVICE_CREATED = "WLSKO-0053";
  public static final String CLUSTER_SERVICE_REPLACED = "WLSKO-0054";
  public static final String CLUSTER_SERVICE_EXISTS = "WLSKO-0055";
  public static final String CM_CREATED = "WLSKO-0056";
  public static final String CM_REPLACED = "WLSKO-0057";
  public static final String CM_EXISTS = "WLSKO-0058";
  public static final String CANNOT_CREATE_TOKEN_REVIEW = "WLSKO-0059";
  public static final String APIEXCEPTION_FROM_TOKEN_REVIEW = "WLSKO-0068";
  public static final String APIEXCEPTION_FROM_SUBJECT_ACCESS_REVIEW = "WLSKO-0069";
  public static final String REPLICA_MORE_THAN_WLS_SERVERS = "WLSKO-0071";
  public static final String K8S_VERSION_TOO_LOW = "WLSKO-0073";
  public static final String VERIFY_K8S_MIN_VERSION = "WLSKO-0074";
  public static final String DOMAIN_UID_UNIQUENESS_FAILED = "WLSKO-0076";
  public static final String PV_NOT_FOUND_FOR_DOMAIN_UID = "WLSKO-0077";
  public static final String PV_ACCESS_MODE_FAILED = "WLSKO-0078";
  public static final String K8S_VERSION_CHECK = "WLSKO-0079";
  public static final String K8S_VERSION_CHECK_FAILURE = "WLSKO-0080";
  public static final String HTTP_METHOD_FAILED = "WLSKO-0081";
  public static final String NOT_STARTING_DOMAINUID_THREAD = "WLSKO-0082";
  public static final String OP_CONFIG_NAMESPACE = "WLSKO-0083";
  public static final String OP_CONFIG_TARGET_NAMESPACES = "WLSKO-0084";
  public static final String OP_CONFIG_SERVICE_ACCOUNT = "WLSKO-0085";
  public static final String WAITING_FOR_POD_READY = "WLSKO-0087";
  public static final String POD_IS_READY = "WLSKO-0088";
  public static final String POD_IS_FAILED = "WLSKO-0089";
  public static final String ASYNC_REQUEST = "WLSKO-0094";
  public static final String ASYNC_FAILURE = "WLSKO-0095";
  public static final String ASYNC_SUCCESS = "WLSKO-0096";
  public static final String ASYNC_NO_RETRY = "WLSKO-0097";
  public static final String ASYNC_RETRY = "WLSKO-0098";
  public static final String ASYNC_TIMEOUT = "WLSKO-0099";
  public static final String WATCH_EVENT = "WLSKO-0101";
  public static final String DOMAIN_STATUS = "WLSKO-0102";
  public static final String INVALID_MANAGE_SERVER_COUNT = "WLSKO-0103";
  public static final String SCALE_COUNT_GREATER_THAN_CONFIGURED = "WLSKO-0104";
  public static final String MATCHING_DOMAIN_NOT_FOUND = "WLSKO-0106";
  public static final String INVALID_DOMAIN_UID = "WLSKO-0107";
  public static final String NULL_DOMAIN_UID = "WLSKO-0108";
  public static final String NULL_TOKEN_REVIEW_STATUS = "WLSKO-0109";
  public static final String NULL_USER_INFO = "WLSKO-0110";
  public static final String RESOURCE_BUNDLE_NOT_FOUND = "WLSKO-0111";
  public static final String CURRENT_STEPS = "WLSKO-0112";
  public static final String CYCLING_SERVERS = "WLSKO-0118";
  public static final String ROLLING_SERVERS = "WLSKO-0119";
  public static final String ADMIN_POD_PATCHED = "WLSKO-0120";
  public static final String MANAGED_POD_PATCHED = "WLSKO-0121";
  public static final String POD_DELETED = "WLSKO-0122";
  public static final String TUNING_PARAMETERS = "WLSKO-0126";
  public static final String WLS_HEALTH_READ_FAILED = "WLSKO-0127";
  public static final String WLS_UPDATE_CLUSTER_SIZE_STARTING = "WLSKO-0128";
  public static final String WLS_UPDATE_CLUSTER_SIZE_FAILED = "WLSKO-0129";
  public static final String WLS_UPDATE_CLUSTER_SIZE_INVALID_CLUSTER = "WLSKO-0131";
  public static final String WLS_CLUSTER_SIZE_UPDATED = "WLSKO-0132";
  public static final String WLS_SERVER_TEMPLATE_NOT_FOUND = "WLSKO-0133";
  public static final String SCRIPT_LOADED = "WLSKO-0134";
  public static final String JOB_IS_FAILED = "WLSKO-0136";
  public static final String JOB_DELETED = "WLSKO-0137";
  public static final String WAITING_FOR_JOB_READY = "WLSKO-0138";
  public static final String JOB_CREATED = "WLSK0-0139";
  public static final String JOB_IS_COMPLETE = "WLSKO-0140";
  public static final String CANNOT_PARSE_TOPOLOGY = "WLSKO-0141";
  public static final String CANNOT_PARSE_INTROSPECTOR_RESULT = "WLSKO-0142";
  public static final String CANNOT_PARSE_INTROSPECTOR_FILE = "WLSKO-0143";
  public static final String CANNOT_START_DOMAIN_AFTER_MAX_RETRIES = "WLSKO-0144";
  public static final String CYCLING_POD = "WLSKO-0145";
  public static final String REPLICAS_EXCEEDS_TOTAL_CLUSTER_SERVER_COUNT = "WLSKO-0146";
  public static final String POD_DUMP = "WLSKO-0148";
  public static final String EXTERNAL_CHANNEL_SERVICE_CREATED = "WLSKO-0150";
  public static final String EXTERNAL_CHANNEL_SERVICE_REPLACED = "WLSKO-0151";
  public static final String EXTERNAL_CHANNEL_SERVICE_EXISTS = "WLSKO-0152";
  public static final String WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT = "WLSKO-0153";
  public static final String JOB_DEADLINE_EXCEEDED_MESSAGE = "WLSKO-0154";
  public static final String VERIFY_ACCESS_DENIED_WITH_NS = "WLSKO-0156";
  public static final String DOMAIN_VALIDATION_FAILED = "WLSKO-0157";
  public static final String NO_INTERNAL_CERTIFICATE = "WLSKO-162";
  public static final String NO_EXTERNAL_CERTIFICATE = "WLSKO-163";
  public static final String REPLICAS_LESS_THAN_TOTAL_CLUSTER_SERVER_COUNT = "WLSKO-0164";
  public static final String REQUEST_PARAMS_IN_NS = "WLSKO-0165";
  public static final String REQUEST_PARAMS_FOR_NAME = "WLSKO-0166";
  public static final String REQUEST_PARAMS_WITH = "WLSKO-0167";
  public static final String CALL_FAILED = "WLSKO-0168";
  public static final String JOB_CREATION_TIMESTAMP_MESSAGE = "WLSKO-0169";
  public static final String HTTP_REQUEST_TIMED_OUT = "WLSKO-0170";
  public static final String NAMESPACE_IS_MISSING = "WLSKO-0171";
  public static final String CM_PATCHED = "WLSKO-0172";
  public static final String REPLACE_CRD_FAILED = "WLSKO-0173";
  public static final String CREATE_CRD_FAILED = "WLSKO-0174";
  public static final String INTROSPECTOR_JOB_FAILED = "WLSKO-0175";
  public static final String INTROSPECTOR_JOB_FAILED_DETAIL = "WLSKO-0176";
  public static final String INTROSPECTOR_POD_FAILED = "WLSKO-0177";

  // domain status messages
  public static final String DUPLICATE_SERVER_NAME_FOUND = "WLSDO-0001";
  public static final String DUPLICATE_CLUSTER_NAME_FOUND = "WLSDO-0002";
  public static final String LOG_HOME_NOT_MOUNTED = "WLSDO-0003";
  public static final String BAD_VOLUME_MOUNT_PATH = "WLSDO-0004";
  public static final String RESERVED_ENVIRONMENT_VARIABLES = "WLSDO-0005";
  public static final String ILLEGAL_SECRET_NAMESPACE = "WLSDO-0006";
  public static final String ILLEGAL_SIT_CONFIG_MII = "WLSDO-0007";
  public static final String MODEL_CONFIGMAP_NOT_FOUND = "WLSDO-0008";
  public static final String SECRET_NOT_SPECIFIED = "WLSDO-0009";
  public static final String OPSS_SECRET_NOT_SPECIFIED = "WLSDO-0010";
  public static final String NO_CLUSTER_IN_DOMAIN = "WLSDO-0011";
  public static final String NO_MANAGED_SERVER_IN_DOMAIN = "WLSDO-0012";
  public static final String CANNOT_EXPOSE_DEFAULT_CHANNEL_ISTIO = "WLSDO-0013";

  private MessageKeys() {
  }
}
