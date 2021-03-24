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
import oracle.kubernetes.operator.rest.model.ClusterModel;
import oracle.kubernetes.operator.rest.model.CollectionModel;

/**
 * ClustersResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains/{domainUID}/clusters path. It can be used to list a WebLogic domain's
 * clusters.
 */
public class ClustersResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a ClustersResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public ClustersResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * List a WebLogic domain's clusters.
   *
   * @return a collection of ClusterModels describing the clusters.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CollectionModel<ClusterModel> get() {
    LOGGER.entering(href());
    String domainUid = getDomainUid();
    CollectionModel<ClusterModel> collection = new CollectionModel<>();
    for (String cluster : getBackend().getClusters(domainUid)) {
      ClusterModel item = new ClusterModel(cluster);
      item.addSelfLinks(href(item.getCluster()));
      collection.addItem(item);
    }
    addSelfAndParentLinks(collection);
    LOGGER.exiting(collection);
    return collection;
  }

  /**
   * Construct and return a 'cluster' jaxrs child resource.
   *
   * @param cluster - the name of the WebLogic cluster.
   * @return the cluster sub resource, throws a WebApplicationException if cluster does not exist.
   */
  @Path("{clusters}")
  public ClusterResource getClusterResource(@PathParam("clusters") String cluster) {
    if (!getBackend().isCluster(getDomainUid(), cluster)) {
      WebApplicationException e = notFound(cluster);
      LOGGER.throwing(e);
      throw e;
    }
    ClusterResource result = new ClusterResource(this, cluster);
    return result;
  }

  private String getDomainUid() {
    return getParent().getPathSegment();
  }
}
