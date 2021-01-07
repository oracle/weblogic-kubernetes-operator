// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ErrorModel;

/**
 * ErrorFilter reformats string entities from non-success responses into arrays of message entities.
 */
@Provider
@Priority(FilterPriorities.ERROR_FILTER_PRIORITY)
public class ErrorFilter implements ContainerResponseFilter {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ErrorFilter() {
    // nothing to do
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext res) {
    LOGGER.entering();
    int status = res.getStatus();
    LOGGER.finer("status=" + status);
    if ((status >= 200) && (status <= 299)) {
      // don't wrap success messages
      return;
    }
    Object entity = res.getEntity();
    if (entity == null) {
      // don't wrap null entities
      LOGGER.finer("null entity");
    } else if (entity instanceof String) {
      // Wrap the error in an 'Error' object that converts the error to a
      // json object matching the Oracle REST style guide:
      String detail = (String) entity;
      LOGGER.finer("String entity=" + detail);
      ErrorModel error = new ErrorModel(status, detail);
      res.setEntity(error, res.getEntityAnnotations(), MediaType.APPLICATION_JSON_TYPE);
    } else {
      LOGGER.finer("Non-string entity", entity);
    }
    LOGGER.exiting();
  }
}
