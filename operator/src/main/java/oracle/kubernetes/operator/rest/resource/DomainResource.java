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
   * Get a description of this Weblogic domain.
   *
   * @return a DomainModel describing this domain.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public DomainModel get() {
    LOGGER.entering(href());
    DomainModel item = new DomainModel(getDomainUID());
    addSelfAndParentLinks(item);
    addLink(item, "clusters");
    LOGGER.exiting(item);
    return item;
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

  private String getDomainUID() {
    return getPathSegment();
  }
}
