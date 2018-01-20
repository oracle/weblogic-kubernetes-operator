// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ResourceAttributes;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1SubjectAccessReviewSpec;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * Delegate authorization decisions to Kubernetes ABAC and/or RBAC.
 */
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
    pods,
    services,
    namespaces,
    customresources,
    customresourcedefinitions,
    domains,
    tokenreviews,
    networkpolicies,
    secrets,
    persistentvolumes,
    persistentvolumeclaims,
    ingresses
  }

  public enum Scope {
    namespace,
    cluster
  }

  /**
   * Check if the specified principal is allowed to perform the specified operation on the
   * specified resource in the specified scope.  Call this version of the method when you
   * know that the principal is not a member of any groups.
   *
   * @param client        The Kubernetes client api.
   * @param principal     The user, group or service account.
   * @param operation     The operation to be authorized.
   * @param resource      The kind of resource on which the operation is to be authorized.
   * @param resourceName  The name of the resource instance on which the operation is to be authorized.
   * @param scope         The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(ClientHolder client, String principal, Operation operation, Resource resource, String resourceName, Scope scope, String namespaceName) {
    return check(client, principal, null, operation, resource, resourceName, scope, namespaceName);
  }

  /**
   * Check if the specified principal is allowed to perform the specified operation on the
   * specified resource in the specified scope.
   *
   * @param client        The Kubernetes client api.
   * @param principal     The user, group or service account.
   * @param groups        The groups that principal is a member of.
   * @param operation     The operation to be authorized.
   * @param resource      The kind of resource on which the operation is to be authorized.
   * @param resourceName  The name of the resource instance on which the operation is to be authorized.
   * @param scope         The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return true if the operation is allowed, or false if not.
   */
  public boolean check(ClientHolder client, String principal, final List<String> groups, Operation operation, Resource resource, String resourceName, Scope scope, String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReview subjectAccessReview = prepareSubjectAccessReview(principal, groups, operation, resource, resourceName, scope, namespaceName);
    try {
      subjectAccessReview = client.callBuilder().createSubjectAccessReview(subjectAccessReview);
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
   * Prepares an instance of SubjectAccessReview and returns same.
   *
   * @param principal     The user, group or service account.
   * @param groups        The groups that principal is a member of.
   * @param operation     The operation to be authorized.
   * @param resource      The kind of resource on which the operation is to be authorized.
   * @param resourceName  The name of the resource instance on which the operation is to be authorized.
   * @param scope         The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of SubjectAccessReview.
   */
  private V1SubjectAccessReview prepareSubjectAccessReview(String principal, final List<String> groups, Operation operation, Resource resource, String resourceName, Scope scope, String namespaceName) {
    LOGGER.entering();
    V1SubjectAccessReviewSpec subjectAccessReviewSpec = new V1SubjectAccessReviewSpec();

    subjectAccessReviewSpec.setUser(principal);
    subjectAccessReviewSpec.setGroups(groups);
    subjectAccessReviewSpec.setResourceAttributes(prepareResourceAttributes(operation, resource, resourceName, scope, namespaceName));

    V1SubjectAccessReview subjectAccessReview = new V1SubjectAccessReview();
    subjectAccessReview.setApiVersion("authorization.k8s.io/v1");
    subjectAccessReview.setKind("SubjectAccessReview");
    subjectAccessReview.setMetadata(new V1ObjectMeta());
    subjectAccessReview.setSpec(subjectAccessReviewSpec);
    LOGGER.exiting(subjectAccessReview);
    return subjectAccessReview;
  }

  /**
   * Prepares an instance of ResourceAttributes and returns same.
   *
   * @param operation     The operation to be authorized.
   * @param resource      The kind of resource on which the operation is to be authorized.
   * @param resourceName  The name of the resource instance on which the operation is to be authorized.
   * @param scope         The scope of the operation (cluster or namespace).
   * @param namespaceName name of the namespace if scope is namespace else null.
   * @return an instance of ResourceAttributes
   */
  private V1ResourceAttributes prepareResourceAttributes(Operation operation, Resource resource, String resourceName, Scope scope, String namespaceName) {
    LOGGER.entering();
    V1ResourceAttributes resourceAttributes = new V1ResourceAttributes();
    if (null != operation) {
      resourceAttributes.setVerb(operation.toString());
    }
    if (null != resource) {
      resourceAttributes.setResource(resource.toString());
    }

    String apiGroup = getApiGroup(resource);
    if (apiGroup != null) {
      resourceAttributes.setGroup(apiGroup);
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

  private String getApiGroup(Resource resource) {
    if (resource == Resource.domains) {
      return "weblogic.oracle";
    }

    if (resource == Resource.customresourcedefinitions) {
      return "apiextensions.k8s.io";
    }

    if (resource == Resource.tokenreviews) {
      return "authentication.k8s.io";
    }
    
    if (resource == Resource.ingresses) {
      return "extensions";
    }

    // TODO - do we need to specify the api group for any of the other Resource values?
    return null;
  }
}
