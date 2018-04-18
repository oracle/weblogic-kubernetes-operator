// Copyright 2017, 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1ResourceRule;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Helper Class for checking the health of the WebLogic Operator
 */
public class HealthCheckHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
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

  private static final AuthorizationProxy.Operation[] cOperations = {
      AuthorizationProxy.Operation.create};

  private static final AuthorizationProxy.Operation[] glOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list};

  private static final AuthorizationProxy.Operation[] glwOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch};

  private static final AuthorizationProxy.Operation[] glwupOperations = {
      AuthorizationProxy.Operation.get,
      AuthorizationProxy.Operation.list,
      AuthorizationProxy.Operation.watch,
      AuthorizationProxy.Operation.update,
      AuthorizationProxy.Operation.patch};


  // default namespace or svc account name
  private static final String DEFAULT_NAMESPACE = "default";

  private static final String DOMAIN_UID_LABEL = "weblogic.domainUID";
  private static final String MINIMUM_K8S_VERSION = "v1.7.5";
  private static final String DOMAIN_IMAGE = "store/oracle/weblogic:12.2.1.3";
  private static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";

  /**
   * Constructor.
   * @param operatorNamespace Scope for object names and authorization
   * @param targetNamespaces If 'true', any output is pretty printed
   */
  public HealthCheckHelper(String operatorNamespace, Collection<String> targetNamespaces) {

    this.operatorNamespace = operatorNamespace;
    this.targetNamespaces = targetNamespaces;

    // Initialize access checks to be performed

    // CRUD resources
    namespaceAccessChecks.put(AuthorizationProxy.Resource.PODS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.PODPRESETS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.PODTEMPLATES, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.SERVICES, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.CONFIGMAPS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.EVENTS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.JOBS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.CRONJOBS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.PERSISTENTVOLUMECLAIMS, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.NETWORKPOLICIES, crudOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.PODSECURITYPOLICIES, crudOperations);
    
    namespaceAccessChecks.put(AuthorizationProxy.Resource.LOGS, glOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.EXEC, cOperations);

    clusterAccessChecks.put(AuthorizationProxy.Resource.DOMAINS, glwupOperations);

    clusterAccessChecks.put(AuthorizationProxy.Resource.CRDS, crudOperations);
    clusterAccessChecks.put(AuthorizationProxy.Resource.INGRESSES, crudOperations);
    clusterAccessChecks.put(AuthorizationProxy.Resource.PERSISTENTVOLUMES, crudOperations);

    // Readonly resources
    clusterAccessChecks.put(AuthorizationProxy.Resource.NAMESPACES, glwOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.SECRETS, glwOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.STORAGECLASSES, glwOperations);

    // tokenreview
    namespaceAccessChecks.put(AuthorizationProxy.Resource.TOKENREVIEWS, cOperations);
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
   * @param version Kubernetes version
   * @throws ApiException exception for k8s API
   **/
  public void performSecurityChecks(KubernetesVersion version) throws ApiException {

    // Validate namespace
    if (DEFAULT_NAMESPACE.equals(operatorNamespace)) {
      LOGGER.info(MessageKeys.NAMESPACE_IS_DEFAULT);
    }

    // Validate RBAC or ABAC policies allow service account to perform required operations
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.info(MessageKeys.VERIFY_ACCESS_START);
    
    if (version.major > 1 || version.minor >= 8) {
      boolean rulesReviewSuccessful = true;
      for (String ns : targetNamespaces) {
        V1SelfSubjectRulesReview review = ap.review(ns);
        if (review == null) {
          rulesReviewSuccessful = false;
          break;
        }
        
        V1SubjectRulesReviewStatus status = review.getStatus();
        List<V1ResourceRule> rules = status.getResourceRules();
        
        for (AuthorizationProxy.Resource r : namespaceAccessChecks.keySet()) {
          for (AuthorizationProxy.Operation op : namespaceAccessChecks.get(r)) {
            check(rules, r, op);
          }
        }
        for (AuthorizationProxy.Resource r : clusterAccessChecks.keySet()) {
          for (AuthorizationProxy.Operation op : clusterAccessChecks.get(r)) {
            check(rules, r, op);
          }
        }
      }
      if (!targetNamespaces.contains("default")) {
        V1SelfSubjectRulesReview review = ap.review("default");
        if (review == null) {
          rulesReviewSuccessful = false;
        } else {
          V1SubjectRulesReviewStatus status = review.getStatus();
          List<V1ResourceRule> rules = status.getResourceRules();

          for (AuthorizationProxy.Resource r : clusterAccessChecks.keySet()) {
            for (AuthorizationProxy.Operation op : clusterAccessChecks.get(r)) {
              check(rules, r, op);
            }
          }
        }
      }
      
      if (rulesReviewSuccessful) {
        return;
      }
    }
    
    for (AuthorizationProxy.Resource r : namespaceAccessChecks.keySet()) {
      for (AuthorizationProxy.Operation op : namespaceAccessChecks.get(r)) {

        for (String ns : targetNamespaces) {
          if (!ap.check(op, r, null, AuthorizationProxy.Scope.namespace, ns)) {
            LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
          }
        }
      }
    }

    for (AuthorizationProxy.Resource r : clusterAccessChecks.keySet()) {
      for (AuthorizationProxy.Operation op : clusterAccessChecks.get(r)) {

        if (!ap.check(op, r, null, AuthorizationProxy.Scope.cluster, null)) {
          LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
        }
      }
    }
  }
  
  private void check(List<V1ResourceRule> rules, AuthorizationProxy.Resource r, AuthorizationProxy.Operation op) {
    String verb = op.name();
    String apiGroup = r.getAPIGroup();
    String resource = r.getResource();
    String sub = r.getSubResource();
    if (sub != null && !sub.isEmpty()) {
      resource = resource + "/" + sub;
    }
    for (V1ResourceRule rule : rules) {
      List<String> ruleApiGroups = rule.getApiGroups();
      if (apiGroupMatch(ruleApiGroups, apiGroup)) {
        List<String> ruleResources = rule.getResources();
        if (ruleResources != null && ruleResources.contains(resource)) {
          List<String> ruleVerbs = rule.getVerbs();
          if (ruleVerbs != null && ruleVerbs.contains(verb)) {
            return;
          }
        }
      }
    }
    
    LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
  }

  private boolean apiGroupMatch(List<String> ruleApiGroups, String apiGroup) {
    if (apiGroup == null || apiGroup.isEmpty()) {
      return ruleApiGroups == null || ruleApiGroups.isEmpty() || ruleApiGroups.contains("");
    }
    return ruleApiGroups != null && ruleApiGroups.contains(apiGroup);
  }
  
  /**
   * Major and minor version of Kubernetes API Server
   * 
   */
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
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      info = factory.create().readVersionCode();

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
        LOGGER.warning(MessageKeys.K8S_MIN_VERSION_CHECK_FAILED, MINIMUM_K8S_VERSION, gitVersion);
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
    CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);

    HashMap<String, Domain> domainUIDMap = new HashMap<>();
    for (String namespace : targetNamespaces) {
      DomainList domainList = factory.create().listDomain(namespace);

      LOGGER.info(MessageKeys.NUMBER_OF_DOMAINS_IN_NAMESPACE, domainList.getItems().size(), namespace);

      // Verify that the domain UID is unique within the k8s cluster.
      for (Domain domain : domainList.getItems()) {
        Domain domain2 = domainUIDMap.put(domain.getSpec().getDomainUID(), domain);
        // Domain UID already exist if not null
        if (domain2 != null) {
          LOGGER.warning(MessageKeys.DOMAIN_UID_UNIQUENESS_FAILED, domain.getSpec().getDomainUID(),
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
    CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);

    V1PersistentVolumeList pvList = factory.create().listPersistentVolume();

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
            LOGGER.warning(MessageKeys.PV_ACCESS_MODE_FAILED, pv.getMetadata().getName(),
                domain.getMetadata().getName(), domainUID, READ_WRITE_MANY_ACCESS);
          }
          //TODO: Should we verify the claim, also?
        }
      }

      // Persistent volume for domain UID not found
      if (!foundLabel) {
        LOGGER.warning(MessageKeys.PV_NOT_FOUND_FOR_DOMAIN_UID, domain.getMetadata().getName(), domainUID);
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
        LOGGER.warning(MessageKeys.DOMAIN_IMAGE_FAILED, DOMAIN_IMAGE, domain.getSpec().getImage());
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
}
