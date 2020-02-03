// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.StartupControl;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** A Helper Class for checking the health of the WebLogic Operator. */
public final class HealthCheckHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<Resource, Operation[]>
      namespaceAccessChecks = new HashMap<>();
  private static final Map<Resource, Operation[]>
      clusterAccessChecks = new HashMap<>();

  // Note: this list should match the policies contained in the YAML script
  // generated for use by the Kubernetes administrator
  //
  private static final Operation[] crudOperations = {
    Operation.get,
    Operation.list,
    Operation.watch,
    Operation.create,
    Operation.update,
    Operation.patch,
    Operation.delete,
    Operation.deletecollection
  };

  private static final Operation[] cOperations = {
    Operation.create
  };

  private static final Operation[] glOperations = {
    Operation.get, Operation.list
  };

  private static final Operation[] glwOperations = {
    Operation.get,
    Operation.list,
    Operation.watch
  };

  private static final Operation[] glwupOperations = {
    Operation.get,
    Operation.list,
    Operation.watch,
    Operation.update,
    Operation.patch
  };

  // default namespace or svc account name
  private static final String DEFAULT_NAMESPACE = "default";

  static {
    // CRUD resources
    namespaceAccessChecks.put(Resource.PODS, crudOperations);
    namespaceAccessChecks.put(Resource.PODPRESETS, crudOperations);
    namespaceAccessChecks.put(Resource.PODTEMPLATES, crudOperations);
    namespaceAccessChecks.put(Resource.SERVICES, crudOperations);
    namespaceAccessChecks.put(Resource.CONFIGMAPS, crudOperations);
    namespaceAccessChecks.put(Resource.EVENTS, crudOperations);
    namespaceAccessChecks.put(Resource.JOBS, crudOperations);
    namespaceAccessChecks.put(Resource.CRONJOBS, crudOperations);
    namespaceAccessChecks.put(Resource.PERSISTENTVOLUMECLAIMS, crudOperations);
    namespaceAccessChecks.put(Resource.NETWORKPOLICIES, crudOperations);
    namespaceAccessChecks.put(Resource.PODSECURITYPOLICIES, crudOperations);
    namespaceAccessChecks.put(Resource.INGRESSES, crudOperations);

    clusterAccessChecks.put(Resource.PERSISTENTVOLUMES, crudOperations);
    clusterAccessChecks.put(Resource.CRDS, crudOperations);

    namespaceAccessChecks.put(Resource.LOGS, glOperations);
    namespaceAccessChecks.put(Resource.EXEC, cOperations);

    namespaceAccessChecks.put(Resource.DOMAINS, glwupOperations);

    // Readonly resources
    clusterAccessChecks.put(Resource.NAMESPACES, glwOperations);
    namespaceAccessChecks.put(Resource.SECRETS, glwOperations);
    namespaceAccessChecks.put(Resource.STORAGECLASSES, glwOperations);

    // tokenreview
    namespaceAccessChecks.put(Resource.TOKENREVIEWS, cOperations);
  }

  private HealthCheckHelper() {
  }

  /**
   * Verify Access.
   *  @param version Kubernetes version
   * @param operatorNamespace operator namespace
   * @param ns target namespace
   */
  public static void performSecurityChecks(
        KubernetesVersion version, String operatorNamespace, String ns) {

    // Validate namespace
    if (DEFAULT_NAMESPACE.equals(operatorNamespace)) {
      LOGGER.info(MessageKeys.NAMESPACE_IS_DEFAULT);
    }
    boolean dedicated = new StartupControl(version).isDedicated();

    // Validate RBAC or ABAC policies allow service account to perform required operations
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.info(MessageKeys.VERIFY_ACCESS_START, ns);

    V1SelfSubjectRulesReview review = getRulesReview(ap, version);
    if (review != null) {
      List<V1ResourceRule> rules = Optional.ofNullable(review.getStatus())
            .map(V1SubjectRulesReviewStatus::getResourceRules)
            .orElse(Collections.emptyList());

      for (Resource r : namespaceAccessChecks.keySet()) {
        for (Operation op : namespaceAccessChecks.get(r)) {
          check(rules, r, op, ns, !dedicated);
        }
      }
      if (!dedicated) {
        for (Resource r : clusterAccessChecks.keySet()) {
          for (Operation op : clusterAccessChecks.get(r)) {
            check(rules, r, op, null, true);
          }
        }
      }
    } else {

      for (Resource r : namespaceAccessChecks.keySet()) {
        for (Operation op : namespaceAccessChecks.get(r)) {

          if (!ap.check(op, r, null, AuthorizationProxy.Scope.namespace, ns)) {
            LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED_WITH_NS, op, r.getResource(), ns);
          }
        }
      }

      if (!dedicated) {
        for (Resource r : clusterAccessChecks.keySet()) {
          for (Operation op : clusterAccessChecks.get(r)) {

            if (!authorizationPermitsClusterAccess(ap, r, op)) {
              LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
            }
          }
        }
      }
    }
  }

  /**
   * Verify Access To a Cluster resources. Try rule-based control, and fall-back to access-based
   * control if it fails.
   *
   * @param resource Kubernetes resource
   * @param operation Kubernetes operation
   */
  public static boolean isClusterResourceAccessAllowed(
        KubernetesVersion version,
        Resource resource,
        Operation operation) {

    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.fine(MessageKeys.VERIFY_CLUSTER_VIEW_ACCESS_START, resource, operation);

    V1SelfSubjectRulesReview review = getRulesReview(ap, version);
    if (review != null) {
      return rulesPermitClusterAccess(review, resource, operation);
    } else {
      return authorizationPermitsClusterAccess(ap, resource, operation);
    }
  }

  private static V1SelfSubjectRulesReview getRulesReview(AuthorizationProxy ap, KubernetesVersion version) {
    if (!version.isRulesReviewSupported()) {
      return null;
    }

    return ap.review(StartupControl.getOperatorNamespace());
  }

  static boolean authorizationPermitsClusterAccess(AuthorizationProxy ap, Resource resource, Operation operation) {
    return ap.check(operation, resource, null, AuthorizationProxy.Scope.cluster, null);
  }

  static boolean rulesPermitClusterAccess(V1SelfSubjectRulesReview review, Resource resource, Operation operation) {
    List<V1ResourceRule> rules = Optional.ofNullable(review.getStatus())
          .map(V1SubjectRulesReviewStatus::getResourceRules)
          .orElse(Collections.emptyList());

    return check(rules, resource, operation, null, false);
  }

  private static boolean check(
      List<V1ResourceRule> rules, Resource r, Operation op,
      String ns, boolean log) {
    String verb = op.name();
    String apiGroup = r.getApiGroup();
    String resource = r.getResource();
    String sub = r.getSubResource();
    if (sub != null && !sub.isEmpty()) {
      resource = resource + "/" + sub;
    }
    for (V1ResourceRule rule : rules) {
      List<String> ruleApiGroups = rule.getApiGroups();
      if (apiGroupMatch(ruleApiGroups, apiGroup)) {
        List<String> ruleResources = rule.getResources();
        if (ruleResources != null && (ruleResources.contains("*") || ruleResources.contains(resource))) {
          List<String> ruleVerbs = rule.getVerbs();
          if (ruleVerbs != null && (ruleVerbs.contains("*") || ruleVerbs.contains(verb))) {
            return true;
          }
        }
      }
    }
    if (log) {
      if (ns != null) {
        LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED_WITH_NS, op, r.getResource(), ns);
      } else {
        LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
      }
    }
    return false;
  }

  private static boolean apiGroupMatch(List<String> ruleApiGroups, String apiGroup) {
    if (apiGroup == null || apiGroup.isEmpty()) {
      return ruleApiGroups == null || ruleApiGroups.isEmpty() || ruleApiGroups.contains("");
    }
    return ruleApiGroups != null && (ruleApiGroups.contains("*") || ruleApiGroups.contains(apiGroup));
  }

  /**
   * Verify the k8s version.
   *
   * @return Major and minor version information
   */
  public static KubernetesVersion performK8sVersionCheck() {
    LOGGER.info(MessageKeys.VERIFY_K8S_MIN_VERSION);

    try {
      return createAndValidateKubernetesVersion(new CallBuilder().readVersionCode());
    } catch (ApiException ae) {
      LOGGER.warning(MessageKeys.K8S_VERSION_CHECK_FAILURE, ae);
      return KubernetesVersion.UNREADABLE;
    }
  }

  private static KubernetesVersion createAndValidateKubernetesVersion(VersionInfo info) {
    KubernetesVersion kubernetesVersion = new KubernetesVersion(info);

    if (!kubernetesVersion.isCompatible()) {
      LOGGER.warning(
          MessageKeys.K8S_VERSION_TOO_LOW,
          KubernetesVersion.getSupportedVersions(),
          kubernetesVersion.asDisplayString());
    } else {
      LOGGER.info(MessageKeys.K8S_VERSION_CHECK, kubernetesVersion.asDisplayString());
    }
    return kubernetesVersion;
  }
}
