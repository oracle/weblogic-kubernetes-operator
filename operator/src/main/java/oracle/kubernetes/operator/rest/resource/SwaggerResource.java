// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import java.io.InputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * SwaggerResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/swagger path. It can be used to get a json swagger definition of the WebLogic
 * operator REST api.
 */
public class SwaggerResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a SwaggerResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public SwaggerResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Get a swagger definition that describes this version of the WebLogic Operator REST api.
   *
   * @return a json swagger definition.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public InputStream get() {
    LOGGER.entering();
    InputStream is = this.getClass().getClassLoader().getResourceAsStream("operator-swagger.json");
    if (is == null) {
      throw new AssertionError("Could not find operator-swagger.json");
    }
    LOGGER.exiting();
    return is;
  }
}
