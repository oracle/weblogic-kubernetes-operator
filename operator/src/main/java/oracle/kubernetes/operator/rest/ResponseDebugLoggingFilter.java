// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** ResponseDebugLoggingFilter debug logs all the REST responses. */
@Provider
@Priority(FilterPriorities.RESPONSE_DEBUG_LOGGING_FILTER_PRIORITY)
public class ResponseDebugLoggingFilter extends BaseDebugLoggingFilter
    implements ContainerResponseFilter {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** Construct a ResponseDebugLoggingFilter. */
  public ResponseDebugLoggingFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
    if (!LOGGER.isFineEnabled()) {
      return; // don't waste time doing all the calculations
    }
    LOGGER.entering();
    try {
      String reqHeaders = getLoggableHeaders(req);
      LOGGER.fine("uri=" + req.getUriInfo().getRequestUri().toString());
      LOGGER.fine("method=" + req.getMethod());
      Object prop = req.getProperty(FILTER_REQUEST_START_TIME);
      if (prop != null) {
        long start = (Long) prop;
        long end = System.currentTimeMillis();
        long duration = end - start;
        LOGGER.fine("start=" + formatTime(start));
        LOGGER.fine("duration=" + duration + " ms");
      }
      LOGGER.fine("request headers=" + reqHeaders);
      LOGGER.fine("request body=" + req.getProperty(FILTER_REQUEST_ENTITY));
      LOGGER.fine("response status=" + res.getStatus());
      LOGGER.fine("response headers=" + res.getHeaders());
      LOGGER.fine(
          "response body=" + formatEntity(res.getMediaType(), entityToString(res.getEntity())));
    } catch (Throwable t) {
      LOGGER.fine("Unexpected throwable ", t);
    }
    LOGGER.exiting();
  }

  private String entityToString(Object entity) {
    if (entity == null) {
      return null;
    }
    if (entity instanceof InputStream) {
      // Note: Leave the input stream open - JAXRS will read and close it.
      return "streaming response";
    }
    return entity.toString();
  }
}
