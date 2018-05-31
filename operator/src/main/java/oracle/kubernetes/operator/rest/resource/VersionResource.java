// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.VersionUtils;
import oracle.kubernetes.operator.rest.model.VersionModel;

/**
 * VersionResource is a jaxrs resource that implements the REST api for the /operator/{version}
 * path. It can be used to describe a version of the WebLogic operator REST api and to traverse to
 * its child resources.
 */
public class VersionResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a VersionResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public VersionResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Get a description of this version of the WebLogic Operator REST api.
   *
   * @return a VersionModel describing this version.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public VersionModel get() {
    LOGGER.entering(href());
    String version = getVersion();
    VersionModel item =
        new VersionModel(
            VersionUtils.getVersion(version),
            VersionUtils.isLatest(version),
            VersionUtils.getLifecycle(version));
    addSelfAndParentLinks(item);
    addLink(item, "domains");
    addLink(item, "swagger");
    LOGGER.exiting(item);
    return item;
  }

  /**
   * Construct and return the 'domains' jaxrs child resource.
   *
   * @return the domains sub resource.
   */
  @Path("domains")
  public DomainsResource getDomainsResource() {
    LOGGER.entering(href());
    DomainsResource result = new DomainsResource(this, "domains");
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Construct and return the 'swagger' jaxrs child resource.
   *
   * @return the swagger sub resource.
   */
  @Path("swagger")
  public SwaggerResource getSwaggerResource() {
    LOGGER.entering(href());
    SwaggerResource result = new SwaggerResource(this, "swagger");
    LOGGER.exiting(result);
    return result;
  }

  private String getVersion() {
    return getPathSegment();
  }
}
