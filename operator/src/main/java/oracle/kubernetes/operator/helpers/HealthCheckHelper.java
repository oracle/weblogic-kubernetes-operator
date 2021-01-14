// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ResourceRule;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.Main;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Operation;
import oracle.kubernetes.operator.helpers.AuthorizationProxy.Resource;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** A Helper Class for checking the health of the WebLogic Operator. */
public final class HealthCheckHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<Resource, Operation[]>
      domainNamespaceAccessChecks = new HashMap<>();
  private static final Map<Resource, Operation[]>
      operatorNamespaceAccessChecks = new HashMap<>();
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

    domainNamespaceAccessChecks.put(Resource.DOMAINS, glwupOperations);
    domainNamespaceAccessChecks.put(Resource.DOMAINSTATUSES, glwupOperations);

    domainNamespaceAccessChecks.put(Resource.SELFSUBJECTRULESREVIEWS, cOperations);

    domainNamespaceAccessChecks.put(Resource.SERVICES, crudOperations);
    domainNamespaceAccessChecks.put(Resource.CONFIGMAPS, crudOperations);
    domainNamespaceAccessChecks.put(Resource.PODS, crudOperations);
    domainNamespaceAccessChecks.put(Resource.EVENTS, crudOperations);
    domainNamespaceAccessChecks.put(Resource.JOBS, crudOperations);
    domainNamespaceAccessChecks.put(Resource.SECRETS, glwOperations);

    domainNamespaceAccessChecks.put(Resource.LOGS, glOperations);
    domainNamespaceAccessChecks.put(Resource.EXEC, cOperations);

    operatorNamespaceAccessChecks.put(Resource.EVENTS, crudOperations);
    operatorNamespaceAccessChecks.put(Resource.CONFIGMAPS, glwOperations);
    operatorNamespaceAccessChecks.put(Resource.SECRETS, glwOperations);
  }

  private HealthCheckHelper() {
  }

  /**
   * Access the self-subject rules review for the namespace. The namespace may be the operator's
   * namespace, a domain namespace, or both.
   *
   * @param namespace namespace
   * @return self-subject rules review for the namespace
   */
  public static V1SubjectRulesReviewStatus getSelfSubjectRulesReviewStatus(@Nonnull String namespace) {
    AuthorizationProxy ap = new AuthorizationProxy();
    return Optional.ofNullable(ap.review(namespace)).map(V1SelfSubjectRulesReview::getStatus).orElse(null);
  }

  /**
   * Verify Access.
   *
   * @param status Self-subject rules review status
   * @param namespace Namespace
   * @param isDomainNamespace if true, verify domain namespace access; otherwise, verify operator-only namespaces access
   */
  public static void verifyAccess(@Nonnull V1SubjectRulesReviewStatus status, @Nonnull String namespace,
                                  boolean isDomainNamespace) {
    // Validate policies allow service account to perform required operations
    AuthorizationProxy ap = new AuthorizationProxy();
    LOGGER.fine(MessageKeys.VERIFY_ACCESS_START, namespace);

    if (status != null) {
      List<V1ResourceRule> rules = status.getResourceRules();

      if (isDomainNamespace) {
        for (Resource r : domainNamespaceAccessChecks.keySet()) {
          for (Operation op : domainNamespaceAccessChecks.get(r)) {
            check(rules, r, op, namespace);
          }
        }
      } else {
        for (Resource r : operatorNamespaceAccessChecks.keySet()) {
          for (Operation op : operatorNamespaceAccessChecks.get(r)) {
            check(rules, r, op, namespace);
          }
        }

        if (!Main.isDedicated()) {
          for (Resource r : clusterAccessChecks.keySet()) {
            for (Operation op : clusterAccessChecks.get(r)) {
              check(rules, r, op, namespace);
            }
          }
        }
      }
    }
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
    LOGGER.fine(MessageKeys.VERIFY_K8S_MIN_VERSION);

    try {
      CallBuilder cb = new CallBuilder();
      return createAndValidateKubernetesVersion(
          cb.executeSynchronousCallWithRetry(cb::readVersionCode,
          TuningParameters.getInstance().getMainTuning().initializationRetryDelaySeconds));
    } catch (Throwable t) {
      LOGGER.warning(MessageKeys.K8S_VERSION_CHECK_FAILURE, t);
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