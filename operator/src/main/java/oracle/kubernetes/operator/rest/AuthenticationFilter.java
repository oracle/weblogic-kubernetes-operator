// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.backend.RestBackend;
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

  @Context private Application application; // TBD - does this work?

  public static final String REST_BACKEND_PROPERTY = "RestBackend";

  private static final String ACCESS_TOKEN_PREFIX = "Bearer ";

  private static LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** Construct an AuthenticationFilter. */
  public AuthenticationFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    LOGGER.entering();
    try {
      ResourceConfig rc = (ResourceConfig) application;
      RestConfig r = (RestConfig) rc.getProperty(RestConfig.REST_CONFIG_PROPERTY);
      String t = getAccessToken(req);
      RestBackend be = r.getBackend(t);
      req.setProperty(REST_BACKEND_PROPERTY, be);
    } catch (RuntimeException re) {
      authenticationFailure(re);
      throw re; // stop the filter chain if we can't authenticate
    } catch (Error er) {
      authenticationFailure(er);
      throw er; // stop the filter chain if we can't authenticate
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
    // TODO - use the resource bundle for the client's locale, not the server's locale
    for (Logger l = LOGGER.getUnderlyingLogger(); l != null; l = l.getParent()) {
      ResourceBundle rb = l.getResourceBundle();
      if (rb != null) {
        return rb;
      }
    }
    throw new AssertionError("Could not find the resource bundle");
  }

  private void authenticationFailure(Throwable cause) {
    LOGGER.fine("Unexpected throwable ", cause);
  }
}
