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
    CollectionModel<DomainModel> collection = new CollectionModel<>();
    for (String domainUid : getBackend().getDomainUids()) {
      DomainModel item = new DomainModel(domainUid);
      item.addSelfLinks(href(item.getDomainUid()));
      collection.addItem(item);
    }
    addSelfAndParentLinks(collection);
    LOGGER.exiting(collection);
    return collection;
  }

  /**
   * Construct and return a 'domain' jaxrs child resource.
   *
   * @param domainUid - the unique identifier assigned to the WebLogic domain when it was registered
   *     with the WebLogic operator.
   * @return the domain sub resource, throws a WebApplicationException if domainUID is not
   *     registered.
   */
  @Path("{domainUID}")
  public DomainResource getDomainResource(@PathParam("domainUID") String domainUid) {
    if (!getBackend().isDomainUid(domainUid)) {
      WebApplicationException e = notFound(domainUid);
      LOGGER.throwing(e);
      throw e;
    }
    DomainResource result = new DomainResource(this, domainUid);
    return result;
  }
}
