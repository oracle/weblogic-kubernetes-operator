// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * ExceptionMapper converts non-WebApplicationExceptions into internal server errors and logs
 * warnings for them. It debug logs WebApplicationExceptions then lets them flow through unchanged.
 */
@Provider
public class ExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Exception> {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** Construct an ExceptionMapper. */
  public ExceptionMapper() {
    // nothing to do
  }

  @Override
  public Response toResponse(Exception e) {
    LOGGER.entering(e);
    Response r = null;
    // Note: If the top level exception is a WebApplicationException,
    // then jaxrs catches it and returns its response, and never calls the
    // exception mapper.  However, if the top level exception isn't a
    // WebApplicationException, but has a nested WebApplicationException,
    // jaxrs calls the exception mapper instead.
    // So, see if there's a nested WebApplicationException, and if so,
    // return its response.
    for (Throwable t = e; r == null && t != null; t = t.getCause()) {
      if (t instanceof WebApplicationException) {
        r = ((WebApplicationException) t).getResponse();
      }
    }
    if (r == null) {
      LOGGER.finer(
          "Constructing an INTERNAL_SERVER_ERROR response from a non-WebApplicationException");
      // It isn't a WebApplicationException - return INTERNAL_SERVER_ERROR
      r = Response.status(Status.INTERNAL_SERVER_ERROR).entity(getExceptionMessage(e)).build();
    } else {
      LOGGER.finer("Using the response from a thrown WebApplicationException");
    }
    if (r.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
      // Always log internal server errors
      LOGGER.warning("InternalServerError", e);
    } else {
      // Otherwise debug log it (i.e. don't log it AND debug log it)
      LOGGER.fine("REST exception", e);
    }
    LOGGER.exiting(r);
    return r;
  }

  private String getExceptionMessage(Exception e) {
    LOGGER.entering();
    // TBD - if there are nested exceptions, which exception message(s) should
    // return? the top one? the most nested one? all of them?
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (!first) {
        sb.append("\n");
      }
      sb.append(t.getLocalizedMessage());
      first = false;
    }
    String result = sb.toString();
    LOGGER.exiting(result);
    return result;
  }
}
