// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.DomainAction;
import oracle.kubernetes.operator.rest.model.DomainModel;

/**
 * DomainResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains/{domainUID} path. It can be used to describe a WebLogic domain that
 * the WebLogic operator manages and to traverse to its child resources.
 */
public class DomainResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a DomainResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public DomainResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Get a description of this WebLogic domain.
   *
   * @return a DomainModel describing this domain.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public DomainModel get() {
    LOGGER.entering(href());
    DomainModel item = new DomainModel(getDomainUid());
    addSelfAndParentLinks(item);
    addLink(item, "clusters");
    LOGGER.exiting(item);
    return item;
  }

  /**
   * Apply changes to this domain. The changes depend on the details of the specified instructions
   *
   * @param params - an update command, including a command type and optional parameters
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public void post(final DomainAction params) {
    getBackend().performDomainAction(getDomainUid(), params);
  }

  /**
   * Construct and return the 'clusters' jaxrs child resource.
   *
   * @return the clusters sub resource.
   */
  @Path("clusters")
  public ClustersResource getClustersResource() {
    LOGGER.entering(href());
    ClustersResource result = new ClustersResource(this, "clusters");
    LOGGER.exiting(result);
    return result;
  }

  private String getDomainUid() {
    return getPathSegment();
  }
}
