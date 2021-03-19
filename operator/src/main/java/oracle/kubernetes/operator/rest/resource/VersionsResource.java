// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.VersionUtils;
import oracle.kubernetes.operator.rest.model.CollectionModel;
import oracle.kubernetes.operator.rest.model.VersionModel;

/**
 * VersionsResource is a jaxrs resource that implements the REST api for the /operator path. It is
 * the root resource of the WebLogic operator REST api and can be used to list the available
 * versions of the WebLogic operator REST api.
 */
@Path("operator")
public class VersionsResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** Construct a VersionsResource. */
  public VersionsResource() {
    super(null, "operator");
  }

  /**
   * List the supported versions of the WebLogic operator REST api.
   *
   * @return a collection of VersionModels describing the supported versions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CollectionModel<VersionModel> get() {
    LOGGER.entering(href());
    CollectionModel<VersionModel> collection = new CollectionModel<>();
    for (String version : VersionUtils.getVersions()) {
      VersionModel item =
          new VersionModel(
              version, VersionUtils.isLatest(version), VersionUtils.getLifecycle(version));
      item.addSelfLinks(href(item.getVersion()));
      collection.addItem(item);
    }
    addSelfAndParentLinks(collection);
    LOGGER.exiting(collection);
    return collection;
  }

  /**
   * Construct and return a 'version' jaxrs child resource.
   *
   * @param version - the name of the WebLogic operator REST api version.
   * @return the version resource, throws a WebApplicationException if the version does not exist.
   */
  @Path("{version}")
  public VersionResource getVersionResource(@PathParam("version") String version) {
    if (!VersionUtils.isVersion(version)) {
      WebApplicationException e = notFound(version);
      LOGGER.throwing(e);
      throw e;
    }
    VersionResource result = new VersionResource(this, version);
    return result;
  }
}
