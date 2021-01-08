// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1TokenReviewSpec;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** Delegate authentication decisions to Kubernetes. */
public class AuthenticationProxy {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static AuthorizationProxy authorizationProxy = new AuthorizationProxy();

  /**
   * Check if the specified access token can be authenticated.
   *
   * @param principal The user, group or service account.
   * @param token The access token that identifies the user.
   * @param namespace Namespace
   * @return V1TokenReviewStatus containing either info about the authenticated user or an error
   *     explaining why the user couldn't be authenticated
   */
  public V1TokenReviewStatus check(String principal, String token, String namespace) {

    LOGGER.entering(principal); // Don't expose the token since it's a credential

    V1TokenReview result = null;
    try {
      boolean allowed =
          authorizationProxy.check(
              principal,
              AuthorizationProxy.Operation.create,
              AuthorizationProxy.Resource.TOKENREVIEWS,
              null,
              namespace == null ? AuthorizationProxy.Scope.cluster : AuthorizationProxy.Scope.namespace,
              namespace);
      if (allowed) {
        result = new CallBuilder().createTokenReview(prepareTokenReview(token));
      } else {
        LOGGER.warning(MessageKeys.CANNOT_CREATE_TOKEN_REVIEW);
      }
    } catch (ApiException e) {
      LOGGER.severe(MessageKeys.APIEXCEPTION_FROM_TOKEN_REVIEW, e);
      LOGGER.exiting(null);
      return null;
    }
    LOGGER.fine("Returned TokenReview", result);
    V1TokenReviewStatus status = result != null ? result.getStatus() : null;
    LOGGER.exiting(status);
    return status;
  }

  private V1TokenReview prepareTokenReview(String token) {
    return new V1TokenReview().spec(new V1TokenReviewSpec().token(token));
  }
}
