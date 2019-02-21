// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ResourceAttributes;
import io.kubernetes.client.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.models.V1SelfSubjectAccessReviewSpec;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1SelfSubjectRulesReviewSpec;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1SubjectAccessReviewSpec;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import java.util.List;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** Delegate authorization decisions to Kubernetes ABAC and/or RBAC. */
public class AuthorizationProxy {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public enum Operation {
    get,
    list,
    create,
    update,
    patch,
    replace,
    watch,
    proxy,
    redirect,
    delete,
    deletecollection
  }

  public enum Resource {
    CONFIGMAPS("configmaps", ""),
    PODS("pods", ""),
    LOGS("pods", "log", ""),
    EXEC("pods", "exec", ""),
    PODTEMPLATES("podtemplates", ""),
    EVENTS("events", ""),
    SERVICES("services", ""),
    NAMESPACES("namespaces", ""),
    JOBS("jobs", "batch"),
    CRONJOBS("cronjobs", "batch"),
    CRDS("customresourcedefinitions", "apiextensions.k8s.io"),
    DOMAINS("domains", "weblogic.oracle"),
    DOMAINSTATUSS("domains", "status", "weblogic.oracle"),
    SUBJECTACCESSREVIEWS("subjectaccessreviews", "authorization.k8s.io"),
    SELFSUBJECTACCESSREVIEWS("selfsubjectaccessreviews", "authorization.k8s.io"),
    LOCALSUBJECTACCESSREVIEWS("localsubjectaccessreviews", "authorization.k8s.io"),
    SELFSUBJECTRULESREVIEWS("selfsubjectrulesreviews", "authorization.k8s.io"),
    TOKENREVIEWS("tokenreviews", "authentication.k8s.io"),
    SECRETS("secrets", ""),
    PERSISTENTVOLUMES("persistentvolumes", ""),
    PERSISTENTVOLUMECLAIMS("persistentvolumeclaims", ""),
    STORAGECLASSES("storageclasses", "storage.k8s.io"),
    PODPRESETS("podpresets", "settings.k8s.io"),
    INGRESSES("ingresses", "extensions"),
    NETWORKPOLICIES("networkpolicies", "extensions"),
    PODSECURITYPOLICIES("podsecuritypolicies", "extensions");

    private final String resource;
    private final String subResource;
    private final String apiGroup;

    Resource(String resource, String apiGroup) {
      this(resource, "", apiGroup);
    }

    Resource(String resource, String subResource, String apiGroup) {
      this.resource = resource;
      this.subResource = subResource;
      this.apiGroup = apiGroup;
    }

    public String getResource() {
      return resource;
    }

    public String getSubResource() {
      return subResource;
    }

    public String getAPIGroup() {
      return apiGroup;
    }
  }

  public enum Scope {
    namespace,
    cluster
  }

  /**
   * Check if the specified principal is allowed to perform the specified operation on the specified
   * resource in the specified scope. Call this version of the method when you know that the
   * principal is not a member of any groups.
   *
   * @param principal The user, group or service account.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(
      String principal,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    return check(principal, null, operation, resource, resourceName, scope, namespaceName);
  }

  /**
   * Check if the specified principal is allowed to perform the specified operation on the specified
   * resource in the specified scope.
   *
   * @param principal The user, group or service account.
   * @param groups The groups that principal is a member of.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(
      String principal,
      final List<String> groups,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReview subjectAccessReview =
        prepareSubjectAccessReview(
            principal, groups, operation, resource, resourceName, scope, namespaceName);
    try {
      subjectAccessReview = new CallBuilder().createSubjectAccessReview(subjectAccessReview);
    } catch (ApiException e) {
      LOGGER.severe(MessageKeys.APIEXCEPTION_FROM_SUBJECT_ACCESS_REVIEW, e);
      LOGGER.exiting(Boolean.FALSE);
      return Boolean.FALSE;
    }
    V1SubjectAccessReviewStatus subjectAccessReviewStatus = subjectAccessReview.getStatus();
    Boolean result = subjectAccessReviewStatus.isAllowed();
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Check access.
   *
   * @param operation Operator
   * @param resource Resource
   * @param resourceName Resource name
   * @param scope Scope
   * @param namespaceName Namespace
   * @return True, if authorization is allowed
   */
  public boolean check(
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();

    Boolean result =
        createSelfSubjectAccessReview(
            prepareSelfSubjectAccessReview(
                operation, resource, resourceName, scope, namespaceName));

    LOGGER.exiting(result);
    return result;
  }

  private Boolean createSelfSubjectAccessReview(V1SelfSubjectAccessReview subjectAccessReview) {
    try {
      subjectAccessReview = new CallBuilder().createSelfSubjectAccessReview(subjectAccessReview);
      V1SubjectAccessReviewStatus subjectAccessReviewStatus = subjectAccessReview.getStatus();
      return subjectAccessReviewStatus.isAllowed();
    } catch (ApiException e) {
      LOGGER.severe(MessageKeys.APIEXCEPTION_FROM_SUBJECT_ACCESS_REVIEW, e);
      return false;
    }
  }

  /**
   * Prepares an instance of SubjectAccessReview and returns same.
   *
   * @param principal The user, group or service account.
   * @param groups The groups that principal is a member of.
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of SubjectAccessReview.
   */
  private V1SubjectAccessReview prepareSubjectAccessReview(
      String principal,
      final List<String> groups,
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReviewSpec subjectAccessReviewSpec = new V1SubjectAccessReviewSpec();

    subjectAccessReviewSpec.setUser(principal);
    subjectAccessReviewSpec.setGroups(groups);
    subjectAccessReviewSpec.setResourceAttributes(
        prepareResourceAttributes(operation, resource, resourceName, scope, namespaceName));

    V1SubjectAccessReview subjectAccessReview = new V1SubjectAccessReview();
    subjectAccessReview.setApiVersion("authorization.k8s.io/v1");
    subjectAccessReview.setKind("SubjectAccessReview");
    subjectAccessReview.setMetadata(new V1ObjectMeta());
    subjectAccessReview.setSpec(subjectAccessReviewSpec);
    LOGGER.exiting(subjectAccessReview);
    return subjectAccessReview;
  }

  private V1SelfSubjectAccessReview prepareSelfSubjectAccessReview(
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1SelfSubjectAccessReviewSpec subjectAccessReviewSpec = new V1SelfSubjectAccessReviewSpec();

    subjectAccessReviewSpec.setResourceAttributes(
        prepareResourceAttributes(operation, resource, resourceName, scope, namespaceName));

    V1SelfSubjectAccessReview subjectAccessReview = new V1SelfSubjectAccessReview();
    subjectAccessReview.setApiVersion("authorization.k8s.io/v1");
    subjectAccessReview.setKind("SelfSubjectAccessReview");
    subjectAccessReview.setMetadata(new V1ObjectMeta());
    subjectAccessReview.setSpec(subjectAccessReviewSpec);
    LOGGER.exiting(subjectAccessReview);
    return subjectAccessReview;
  }

  /**
   * Prepares an instance of ResourceAttributes and returns same.
   *
   * @param operation The operation to be authorized.
   * @param resource The kind of resource on which the operation is to be authorized.
   * @param resourceName The name of the resource instance on which the operation is to be
   *     authorized.
   * @param scope The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of ResourceAttributes
   */
  private V1ResourceAttributes prepareResourceAttributes(
      Operation operation,
      Resource resource,
      String resourceName,
      Scope scope,
      String namespaceName) {
    LOGGER.entering();
    V1ResourceAttributes resourceAttributes = new V1ResourceAttributes();
    if (null != operation) {
      resourceAttributes.setVerb(operation.toString());
    }
    if (null != resource) {
      resourceAttributes.setResource(resource.resource);
      resourceAttributes.setSubresource(resource.subResource);
      resourceAttributes.setGroup(resource.apiGroup);
    }

    if (null != resourceName) {
      resourceAttributes.setName(resourceName);
    }

    if (Scope.namespace == scope) {
      resourceAttributes.setNamespace(namespaceName);
    }
    LOGGER.exiting(resourceAttributes);
    return resourceAttributes;
  }

  V1SelfSubjectRulesReview review(String namespace) {
    V1SelfSubjectRulesReview subjectRulesReview = new V1SelfSubjectRulesReview();
    V1SelfSubjectRulesReviewSpec spec = new V1SelfSubjectRulesReviewSpec();
    spec.setNamespace(namespace);
    subjectRulesReview.setSpec(spec);
    try {
      return new CallBuilder().createSelfSubjectRulesReview(subjectRulesReview);
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }
}
