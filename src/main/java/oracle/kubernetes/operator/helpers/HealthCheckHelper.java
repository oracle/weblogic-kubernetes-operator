// Copyright 2017, 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Helper Class for checking the health of the WebLogic Operator
 */
public class HealthCheckHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private ClientHolder client;
  private final String operatorNamespace;
  private final Collection<String> targetNamespaces;

  private HashMap<AuthorizationProxy.Resource, AuthorizationProxy.Operation[]> namespaceAccessChecks = new HashMap<>();
  private HashMap<AuthorizationProxy.Resource, AuthorizationProxy.Operation[]> clusterAccessChecks = new HashMap<>();

  // Note: this list should match the RBAC or ABAC policies contained in the YAML script
  // generated for use by the Kubernetes administrator
  //
  private static final AuthorizationProxy.Operation[] crudOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch,
      AuthorizationProxy.Operation.create,
      AuthorizationProxy.Operation.update,
      AuthorizationProxy.Operation.patch,
      AuthorizationProxy.Operation.delete,
      AuthorizationProxy.Operation.deletecollection};

  private static final AuthorizationProxy.Operation[] domainOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch,
      AuthorizationProxy.Operation.update,
      AuthorizationProxy.Operation.patch};

  private static final AuthorizationProxy.Operation[] tokenReviewOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] ingressOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch,
      AuthorizationProxy.Operation.create,
      AuthorizationProxy.Operation.update,
      AuthorizationProxy.Operation.patch,
      AuthorizationProxy.Operation.delete,
      AuthorizationProxy.Operation.deletecollection};

  private static final AuthorizationProxy.Operation[] namespaceOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] persistentVolumeOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] persistentVolumeClaimOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] secretsOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] serviceOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch,
          AuthorizationProxy.Operation.create,
          AuthorizationProxy.Operation.update,
          AuthorizationProxy.Operation.patch,
          AuthorizationProxy.Operation.delete,
          AuthorizationProxy.Operation.deletecollection};

  private static final AuthorizationProxy.Operation[] podOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch,
          AuthorizationProxy.Operation.create,
          AuthorizationProxy.Operation.update,
          AuthorizationProxy.Operation.patch,
          AuthorizationProxy.Operation.delete,
          AuthorizationProxy.Operation.deletecollection};

  private static final AuthorizationProxy.Operation[] networkPoliciesOperations = {
          AuthorizationProxy.Operation.get,
          AuthorizationProxy.Operation.list,
          AuthorizationProxy.Operation.watch,
          AuthorizationProxy.Operation.create,
          AuthorizationProxy.Operation.update,
          AuthorizationProxy.Operation.patch,
          AuthorizationProxy.Operation.delete,
          AuthorizationProxy.Operation.deletecollection};

  // default namespace or svc account name
  private static final String DEFAULT_NAMESPACE = "default";

  // API Server command constants
  private static final String SVC_ACCOUNT_PREFIX = "system:serviceaccount:";

  private static final String DOMAIN_UID_LABEL = "weblogic.domainUID";
  private static final String MINIMUM_K8S_VERSION = "v1.7.5";
  private static final String DOMAIN_IMAGE = "store/oracle/weblogic:12.2.1.3";
  private static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";

  /**
   * Constructor.
   * @param client Object to access APIs
   * @param operatorNamespace Scope for object names and authorization
   * @param targetNamespaces If 'true', any output is pretty printed
   */
  public HealthCheckHelper(ClientHolder client, String operatorNamespace, Collection<String> targetNamespaces) {

    this.client = client;
    this.operatorNamespace = operatorNamespace;
    this.targetNamespaces = targetNamespaces;

    // Initialize access checks to be performed

    // CRUD resources
    namespaceAccessChecks.put(AuthorizationProxy.Resource.pods, podOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.services, serviceOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.ingresses, ingressOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.networkpolicies, networkPoliciesOperations);
    clusterAccessChecks.put(AuthorizationProxy.Resource.customresourcedefinitions, crudOperations);

    clusterAccessChecks.put(AuthorizationProxy.Resource.domains, domainOperations);

    // Readonly resources
    clusterAccessChecks.put(AuthorizationProxy.Resource.namespaces, namespaceOperations);
    clusterAccessChecks.put(AuthorizationProxy.Resource.persistentvolumes, persistentVolumeOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.secrets, secretsOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.persistentvolumeclaims, persistentVolumeClaimOperations);

    // tokenreview
    namespaceAccessChecks.put(AuthorizationProxy.Resource.tokenreviews, tokenReviewOperations);
  }

  /**
   * Execute health checks that are not security related.
   *
   * @throws ApiException exception for k8s API
   */
  public void performNonSecurityChecks() throws ApiException {

    HashMap<String, Domain> domainUIDMap = verifyDomainUidUniqueness();
    verifyPersistentVolume(domainUIDMap);
    verifyDomainImage(domainUIDMap);
    verifyAdminServer(domainUIDMap);
  }

  /**
   * Verify Access.
   *
   * @param svcAccountName service account for checking access
   * @throws ApiException exception for k8s API
   **/
  public void performSecurityChecks(String svcAccountName) throws ApiException {

    // Validate namespace
    if (DEFAULT_NAMESPACE.equals(operatorNamespace)) {
      LOGGER.info(MessageKeys.NAMESPACE_IS_DEFAULT);
    }

    // Validate svc account
    String principalName = svcAccountName;
    if (svcAccountName == null || DEFAULT_NAMESPACE.equals(svcAccountName)) {
      LOGGER.info(MessageKeys.SVC_ACCOUNT_IS_DEFAULT);
      principalName = DEFAULT_NAMESPACE;
    }

    String fullName = SVC_ACCOUNT_PREFIX + operatorNamespace + ":" + principalName;

    // Validate RBAC or ABAC policies allow service account to perform required opertions
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.info(MessageKeys.VERIFY_ACCESS_START, svcAccountName);

    for (AuthorizationProxy.Resource r : namespaceAccessChecks.keySet()) {
      for (AuthorizationProxy.Operation op : namespaceAccessChecks.get(r)) {

        for (String ns : targetNamespaces) {
          if (!ap.check(client, fullName, op, r, null, AuthorizationProxy.Scope.namespace, ns)) {
            logHealthCheckEvent(MessageKeys.VERIFY_ACCESS_DENIED, fullName, op, r);
          }
        }
      }
    }

    for (AuthorizationProxy.Resource r : clusterAccessChecks.keySet()) {
      for (AuthorizationProxy.Operation op : clusterAccessChecks.get(r)) {

        if (!ap.check(client, fullName, op, r, null, AuthorizationProxy.Scope.cluster, null)) {
          logHealthCheckEvent(MessageKeys.VERIFY_ACCESS_DENIED, fullName, op, r);
        }
      }
    }
  }

  public static class KubernetesVersion {
    public final int major;
    public final int minor;
    
    public KubernetesVersion(int major, int minor) {
      this.major = major;
      this.minor = minor;
    }
  }

  /**
   * Verify the k8s version.
   *
   * @return Major and minor version information
   * @throws ApiException exception for k8s API
   */
  public KubernetesVersion performK8sVersionCheck() throws ApiException {

    // k8s version must be 1.7.5 or greater
    LOGGER.info(MessageKeys.VERIFY_K8S_MIN_VERSION);
    boolean k8sMinVersion = true;
    VersionInfo info = null;
    
    int major = 0;
    int minor = 0;
    try {
      info = client.getVersionApiClient().getCode();

      String gitVersion = info.getGitVersion();
      major = Integer.parseInt(info.getMajor());
      if (major < 1) {
        k8sMinVersion = false;
      } else if (major == 1) {
	      // The Minor version can be also 8+
	      String minor_string = info.getMinor();
	      // It will check if it is a number.
	      // If not it will remove the last part of the string in order to have just a number
	      while( ! minor_string.chars().allMatch( Character::isDigit )) {
          minor_string = minor_string.substring(0, minor_string.length() -1);
	      }
        minor = Integer.parseInt(minor_string);
        if (minor < 7) {
          k8sMinVersion = false;
        } else if (minor == 7) {
          // git version is of the form v1.7.5
          // Check the 3rd part of the version.
          String[] splitVersion = gitVersion.split("\\.");
          // issue-36: gitVersion can be not just "v1.7.9" but also values like "v1.7.9+coreos.0"
          splitVersion = splitVersion[2].split("\\+");
          if (Integer.parseInt(splitVersion[0]) < 5) {
            k8sMinVersion = false;
          }
        }
      }

      // Minimum k8s version not satisfied.
      if (!k8sMinVersion) {
        logHealthCheckEvent(MessageKeys.K8S_MIN_VERSION_CHECK_FAILED, MINIMUM_K8S_VERSION, gitVersion);
      } else {
        LOGGER.info(MessageKeys.K8S_VERSION_CHECK, gitVersion);
      }
    } catch (ApiException ae) {
      LOGGER.warning(MessageKeys.K8S_VERSION_CHECK_FAILURE, ae);
    }
    
    return new KubernetesVersion(major, minor);
  }

  /**
   * Verify that domain UIDs are unique.
   *
   * @throws ApiException exception for k8s API
   */
  private HashMap<String, Domain> verifyDomainUidUniqueness() throws ApiException {

    HashMap<String, Domain> domainUIDMap = new HashMap<>();
    for (String namespace : targetNamespaces) {
      DomainList domainList = client.callBuilder().listDomain(namespace);

      LOGGER.info(MessageKeys.NUMBER_OF_DOMAINS_IN_NAMESPACE, domainList.getItems().size(), namespace);

      // Verify that the domain UID is unique within the k8s cluster.
      for (Domain domain : domainList.getItems()) {
        Domain domain2 = domainUIDMap.put(domain.getSpec().getDomainUID(), domain);
        // Domain UID already exist if not null
        if (domain2 != null) {
          logHealthCheckEvent(MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED, domain.getSpec().getDomainUID(),
              domain.getMetadata().getName(), domain2.getMetadata().getName());
        }
      }
    }

    return domainUIDMap;
  }

  /**
   * Verify a persistent volume exists for a domain and the permissions are correct.
   *
   * @throws ApiException exception for k8s API
   */
  private void verifyPersistentVolume(HashMap<String, Domain> domainUIDMap) throws ApiException {

    V1PersistentVolumeList pvList = client.callBuilder().listPersistentVolume();

    for (Domain domain : domainUIDMap.values()) {
      LOGGER.finest(MessageKeys.WEBLOGIC_DOMAIN, domain.toString());
      String domainUID = domain.getSpec().getDomainUID();
      boolean foundLabel = false;
      for (V1PersistentVolume pv : pvList.getItems()) {
        Map<String, String> labels = pv.getMetadata().getLabels();
        if (labels != null && labels.get(DOMAIN_UID_LABEL) != null && labels.get(DOMAIN_UID_LABEL).equals(domainUID)) {
          foundLabel = true;
          List<String> accessModes = pv.getSpec().getAccessModes();
          boolean foundAccessMode = false;
          for (String accessMode : accessModes) {
            if (accessMode.equals(READ_WRITE_MANY_ACCESS)) {
              foundAccessMode = true;
              break;
            }
          }

          // Persistent volume does not have ReadWriteMany access mode,
          if (!foundAccessMode) {
            logHealthCheckEvent(MessageKeys.PV_ACCESS_MODE_FAILED, pv.getMetadata().getName(),
                domain.getMetadata().getName(), domainUID, READ_WRITE_MANY_ACCESS);
          }
          //TODO: Should we verify the claim, also?
        }
      }

      // Persistent volume for domain UID not found
      if (!foundLabel) {
        logHealthCheckEvent(MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID, domain.getMetadata().getName(), domainUID);
      }
    }

  }

  /**
   * Verify the domain image is what we expect.
   *
   * @throws ApiException exception for k8s API
   */
  private void verifyDomainImage(HashMap<String, Domain> domainUIDMap) throws ApiException {

    // Verify the domain image is the expected WebLogic image.
    for (Domain domain : domainUIDMap.values()) {
      LOGGER.finest(MessageKeys.WEBLOGIC_DOMAIN, domain.toString());
      if (!domain.getSpec().getImage().equals(DOMAIN_IMAGE)) {
        logHealthCheckEvent(MessageKeys.DOMAIN_IMAGE_FAILED, DOMAIN_IMAGE, domain.getSpec().getImage());
      }
    }
  }

  /**
   * Perform health checks against a running Admin server.
   *
   * @throws ApiException exception for k8s API
   */
  private void verifyAdminServer(HashMap<String, Domain> domainUIDMap) throws ApiException {

    for (Domain domain : domainUIDMap.values()) {
      if (isAdminServerRunning(domain)) {

        // TODO: Check that domain has not dynamic clusters defined in it

        // TODO: Check if the ReadyApp is deployed to the Admin server

        // TODO: Check that clusters are set to unicast not multicast

        // TODO: Check if there is a channel defined for T3, and if so check it has the external port
        //       and host set, and check the internal and external ports are the same
      }
    }
  }

  /**
   * Check if an Admin server is running for a given domain.
   *
   * @param domain k8s domain resource identifying the Admin server
   * @return true is running, false if not running
   */
  private boolean isAdminServerRunning(Domain domain) {
    // TODO: Need to call API to find if an Admin server is running for a given domain
    return true;
  }

  /**
   * Log health check failures.
   *
   * @param msg    the message to log
   * @param params varargs list of objects to include in the log message
   */
  private void logHealthCheckEvent(String msg, Object... params) {
    LOGGER.warning(msg, params);
  }
}
