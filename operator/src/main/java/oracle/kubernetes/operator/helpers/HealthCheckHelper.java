// Copyright 2017, 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ResourceRule;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.models.VersionInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** A Helper Class for checking the health of the WebLogic Operator. */
public final class HealthCheckHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<AuthorizationProxy.Resource, AuthorizationProxy.Operation[]>
      namespaceAccessChecks = new HashMap<>();
  private static final Map<AuthorizationProxy.Resource, AuthorizationProxy.Operation[]>
      clusterAccessChecks = new HashMap<>();

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
    AuthorizationProxy.Operation.deletecollection
  };

  private static final AuthorizationProxy.Operation[] cOperations = {
    AuthorizationProxy.Operation.create
  };

  private static final AuthorizationProxy.Operation[] glOperations = {
    AuthorizationProxy.Operation.get, AuthorizationProxy.Operation.list
  };

  private static final AuthorizationProxy.Operation[] glwOperations = {
    AuthorizationProxy.Operation.get,
    AuthorizationProxy.Operation.list,
    AuthorizationProxy.Operation.watch
  };

  private static final AuthorizationProxy.Operation[] glwupOperations = {
    AuthorizationProxy.Operation.get,
    AuthorizationProxy.Operation.list,
    AuthorizationProxy.Operation.watch,
    AuthorizationProxy.Operation.update,
    AuthorizationProxy.Operation.patch
  };

  // default namespace or svc account name
  private static final String DEFAULT_NAMESPACE = "default";

  static {
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
    namespaceAccessChecks.put(AuthorizationProxy.Resource.INGRESSES, crudOperations);

    clusterAccessChecks.put(AuthorizationProxy.Resource.PERSISTENTVOLUMES, crudOperations);
    clusterAccessChecks.put(AuthorizationProxy.Resource.CRDS, crudOperations);

    namespaceAccessChecks.put(AuthorizationProxy.Resource.LOGS, glOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.EXEC, cOperations);

    namespaceAccessChecks.put(AuthorizationProxy.Resource.DOMAINS, glwupOperations);

    // Readonly resources
    clusterAccessChecks.put(AuthorizationProxy.Resource.NAMESPACES, glwOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.SECRETS, glwOperations);
    namespaceAccessChecks.put(AuthorizationProxy.Resource.STORAGECLASSES, glwOperations);

    // tokenreview
    namespaceAccessChecks.put(AuthorizationProxy.Resource.TOKENREVIEWS, cOperations);
  }

  private HealthCheckHelper() {}

  /**
   * Verify Access.
   *
   * @param version Kubernetes version
   * @param operatorNamespace operator namespace
   * @param ns target namespace
   */
  public static void performSecurityChecks(
      KubernetesVersion version, String operatorNamespace, String ns) {

    // Validate namespace
    if (DEFAULT_NAMESPACE.equals(operatorNamespace)) {
      LOGGER.info(MessageKeys.NAMESPACE_IS_DEFAULT);
    }

    // Validate RBAC or ABAC policies allow service account to perform required operations
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.info(MessageKeys.VERIFY_ACCESS_START);

    if (version.isRulesReviewSupported()) {
      boolean rulesReviewSuccessful = true;
      V1SelfSubjectRulesReview review = ap.review(ns);
      if (review == null) {
        rulesReviewSuccessful = false;
      } else {
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

      if (rulesReviewSuccessful) {
        return;
      }
    }

    for (AuthorizationProxy.Resource r : namespaceAccessChecks.keySet()) {
      for (AuthorizationProxy.Operation op : namespaceAccessChecks.get(r)) {

        if (!ap.check(op, r, null, AuthorizationProxy.Scope.namespace, ns)) {
          LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED, op, r.getResource());
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

  private static void check(
      List<V1ResourceRule> rules, AuthorizationProxy.Resource r, AuthorizationProxy.Operation op) {
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

  private static boolean apiGroupMatch(List<String> ruleApiGroups, String apiGroup) {
    if (apiGroup == null || apiGroup.isEmpty()) {
      return ruleApiGroups == null || ruleApiGroups.isEmpty() || ruleApiGroups.contains("");
    }
    return ruleApiGroups != null && ruleApiGroups.contains(apiGroup);
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
