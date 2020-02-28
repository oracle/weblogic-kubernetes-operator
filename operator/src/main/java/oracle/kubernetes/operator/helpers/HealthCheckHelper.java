// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.Main;
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

  private static final Operation[] crdOperations = {
      Operation.get,
      Operation.list,
      Operation.watch,
      Operation.create,
      Operation.update,
      Operation.patch
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
    clusterAccessChecks.put(Resource.NAMESPACES, glwOperations);
    clusterAccessChecks.put(Resource.CRDS, crdOperations);

    namespaceAccessChecks.put(Resource.DOMAINS, glwupOperations);
    namespaceAccessChecks.put(Resource.DOMAINSTATUSES, glwupOperations);

    namespaceAccessChecks.put(Resource.TOKENREVIEWS, cOperations);
    namespaceAccessChecks.put(Resource.SELFSUBJECTRULESREVIEWS, cOperations);

    namespaceAccessChecks.put(Resource.SERVICES, crudOperations);
    namespaceAccessChecks.put(Resource.CONFIGMAPS, crudOperations);
    namespaceAccessChecks.put(Resource.PODS, crudOperations);
    namespaceAccessChecks.put(Resource.EVENTS, crudOperations);

    namespaceAccessChecks.put(Resource.SECRETS, glwOperations);

    namespaceAccessChecks.put(Resource.LOGS, glOperations);
    namespaceAccessChecks.put(Resource.EXEC, cOperations);

    namespaceAccessChecks.put(Resource.JOBS, crudOperations);
  }

  private HealthCheckHelper() {
  }

  /**
   * Verify Access.
   *
   * @param version Kubernetes version
   * @param operatorNamespace operator namespace
   * @param namespace target namespace
   * @return self subject rules review for the target namespace
   */
  public static V1SubjectRulesReviewStatus performSecurityChecks(
      KubernetesVersion version, String operatorNamespace, String namespace) {
    String ns = namespace != null ? namespace : operatorNamespace;

    // Validate namespace
    if (DEFAULT_NAMESPACE.equals(operatorNamespace)) {
      LOGGER.info(MessageKeys.NAMESPACE_IS_DEFAULT);
    }

    // Validate policies allow service account to perform required operations
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.info(MessageKeys.VERIFY_ACCESS_START, ns);

    V1SelfSubjectRulesReview review = ap.review(ns);
    if (review != null) {
      V1SubjectRulesReviewStatus status = review.getStatus();
      List<V1ResourceRule> rules = status.getResourceRules();

      if (namespace != null) {
        for (Resource r : namespaceAccessChecks.keySet()) {
          for (Operation op : namespaceAccessChecks.get(r)) {
            check(rules, r, op, namespace);
          }
        }
      }
      if (!Main.isDedicated() && operatorNamespace.equals(ns)) {
        for (Resource r : clusterAccessChecks.keySet()) {
          for (Operation op : clusterAccessChecks.get(r)) {
            check(rules, r, op, ns);
          }
        }
      }

      return status;
    }

    return null;
  }

  /**
   * Check if operator has privilege to perform the operation on the resource.
   * @param rules Self subject check rules
   * @param res Resource
   * @param op Operation
   * @return true, if the operator has privilege.
   */
  public static boolean check(
      List<V1ResourceRule> rules, Resource res, Operation op) {
    String verb = op.name();
    String apiGroup = res.getApiGroup();
    String resource = res.getResource();
    String sub = res.getSubResource();
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
    return false;
  }

  private static void check(
      List<V1ResourceRule> rules, Resource r, Operation op, String ns) {

    if (!check(rules, r, op)) {
      LOGGER.warning(MessageKeys.VERIFY_ACCESS_DENIED_WITH_NS, op, r.getResource(), ns);
    }
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