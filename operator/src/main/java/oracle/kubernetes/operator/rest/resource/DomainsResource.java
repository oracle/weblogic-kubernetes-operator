// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.CollectionModel;
import oracle.kubernetes.operator.rest.model.DomainModel;

/**
 * DomainsResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains path. It can be used to list the WebLogic domains that are registered
 * with the WebLogic operator.
 */
public class DomainsResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a DomainsResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public DomainsResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * List the WebLogic domains that are registered with the WebLogic operator.
   *
   * @return a collection of DomainModels describing the domains.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CollectionModel<DomainModel> get() {
    LOGGER.entering(href());
    CollectionModel<DomainModel> collection = new CollectionModel<DomainModel>();
    for (String domainUID : getBackend().getDomainUIDs()) {
      DomainModel item = new DomainModel(domainUID);
      item.addSelfLinks(href(item.getDomainUID()));
      collection.addItem(item);
    }
    addSelfAndParentLinks(collection);
    LOGGER.exiting(collection);
    return collection;
  }

  /**
   * Construct and return a 'domain' jaxrs child resource.
   *
   * @param domainUID - the unique identifier assigned to the WebLogic domain when it was registered
   *     with the WebLogic operator.
   * @return the domain sub resource, throws a WebApplicationException if domainUID is not
   *     registered.
   */
  @Path("{domainUID}")
  public DomainResource getDomainResource(@PathParam("domainUID") String domainUID) {
    LOGGER.entering(href(), domainUID);
    if (!getBackend().isDomainUID(domainUID)) {
      WebApplicationException e = notFound(domainUID);
      LOGGER.throwing(e);
      throw e;
    }
    DomainResource result = new DomainResource(this, domainUID);
    LOGGER.exiting(result);
    return result;
  }
}
