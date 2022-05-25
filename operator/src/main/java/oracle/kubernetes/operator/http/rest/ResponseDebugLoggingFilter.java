// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import javax.annotation.Priority;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
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
  public void filter(ContainerRequestContext req, ContainerResponseContext res) {
    if (!LOGGER.isFineEnabled()) {
      return; // don't waste time doing all the calculations
    }
    LOGGER.entering();
    try {
      final String reqHeaders = getLoggableHeaders(req);
      LOGGER.fine("uri=" + req.getUriInfo().getRequestUri().toString());
      LOGGER.fine("method=" + req.getMethod());
      Object prop = req.getProperty(FILTER_REQUEST_START_TIME);
      if (prop != null) {
        OffsetDateTime start = (OffsetDateTime) prop;
        OffsetDateTime end = OffsetDateTime.now();
        long duration = start.until(end, ChronoUnit.MILLIS);
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
