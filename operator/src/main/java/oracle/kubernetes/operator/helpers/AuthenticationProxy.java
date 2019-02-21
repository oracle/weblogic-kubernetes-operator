// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1TokenReviewSpec;
import io.kubernetes.client.models.V1TokenReviewStatus;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/** Delegate authentication decisions to Kubernetes. */
public class AuthenticationProxy {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final AuthorizationProxy authorizationProxy = new AuthorizationProxy();

  /**
   * Check if the specified access token can be authenticated.
   *
   * @param principal The user, group or service account.
   * @param token The access token that identifies the user.
   * @return V1TokenReviewStatus containing either info about the authenticated user or an error
   *     explaining why the user couldn't be authenticated
   */
  public V1TokenReviewStatus check(String principal, String token) {

    LOGGER.entering(principal); // Don't expose the token since it's a credential

    V1TokenReview result = null;
    try {
      boolean allowed =
          authorizationProxy.check(
              principal,
              AuthorizationProxy.Operation.create,
              AuthorizationProxy.Resource.TOKENREVIEWS,
              null,
              AuthorizationProxy.Scope.cluster,
              null);
      if (allowed) {
        result = new CallBuilder().createTokenReview(prepareTokenReview(token));
      } else {
        LOGGER.info(MessageKeys.CANNOT_CREATE_TOKEN_REVIEW);
      }
    } catch (ApiException e) {
      LOGGER.severe(MessageKeys.APIEXCEPTION_FROM_TOKEN_REVIEW, e);
      LOGGER.exiting(null);
      return null;
    }
    LOGGER.info("Returned TokenReview", result);
    V1TokenReviewStatus status = result != null ? result.getStatus() : null;
    LOGGER.exiting(status);
    return status;
  }

  private V1TokenReview prepareTokenReview(String token) {
    LOGGER.entering();
    V1TokenReviewSpec spec = new V1TokenReviewSpec();
    spec.setToken(token);
    V1TokenReview tokenReview = new V1TokenReview();
    tokenReview.setSpec(spec);
    // Can't just log token review since it prints out the token, which is sensitive data.
    // It doesn't contain any other useful data, so don't log any return info.
    LOGGER.exiting();
    return tokenReview;
  }
}
