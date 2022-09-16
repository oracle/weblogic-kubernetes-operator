// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.annotation.Priority;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.http.rest.backend.RestBackend;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * AuthenticationFilter authenticates the request by extracting the access token from tha
 * authorization header and using it to construct a RestBackend impl for this request. It stores the
 * RestBackend in as a request property so that the jaxrs resource impls can call the backend to get
 * their work done.
 *
 * <p>The backend impl is responsible for authenticating the token (if it can't then it throws a
 * WebApplicationException) and storing info about the authenticated user so that it can do access
 * checks for this request later.
 */
@Provider
@PreMatching // so that it's called before the subresource locators are called since they need to
// access the backend
@Priority(FilterPriorities.AUTHENTICATION_FILTER_PRIORITY)
public class AuthenticationFilter extends BaseDebugLoggingFilter implements ContainerRequestFilter {

  public static final String REST_BACKEND_PROPERTY = "RestBackend";
  public static final String ACCESS_TOKEN_PREFIX = "Bearer ";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @Context private Application application;

  /** Construct an AuthenticationFilter. */
  public AuthenticationFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req) {
    LOGGER.entering();
    try {
      ResourceConfig rc = (ResourceConfig) application;
      RestConfig r = (RestConfig) rc.getProperty(RestConfig.REST_CONFIG_PROPERTY);
      String t = getAccessToken(req);
      RestBackend be = r.getBackend(t);
      req.setProperty(REST_BACKEND_PROPERTY, be);
    } catch (RuntimeException | Error re) {
      authenticationFailure(re);
      throw re; // stop the filter chain if we can't authenticate
    }
    LOGGER.exiting();
  }

  private String getAccessToken(ContainerRequestContext req) {
    LOGGER.entering();
    String atz = req.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (atz != null && atz.startsWith(ACCESS_TOKEN_PREFIX)) {
      String t = atz.substring(ACCESS_TOKEN_PREFIX.length());
      if (t.length() > 0) {
        LOGGER.exiting();
        return t;
      }
    }
    String msg = formatMessage(req, MessageKeys.REST_AUTHENTICATION_MISSING_ACCESS_TOKEN);
    WebApplicationException e =
        new WebApplicationException(Response.status(Status.UNAUTHORIZED).entity(msg).build());
    LOGGER.throwing(e);
    throw e;
  }

  private String formatMessage(ContainerRequestContext req, String msgId) {
    return getResourceBundle(req.getLanguage()).getString(msgId);
  }

  private ResourceBundle getResourceBundle(Locale locale) {
    return ResourceBundle.getBundle("Operator", Optional.ofNullable(locale).orElse(Locale.getDefault()));
  }

  private void authenticationFailure(Throwable cause) {
    LOGGER.fine("Unexpected throwable ", cause);
  }
}
