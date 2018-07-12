// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
    CollectionModel<ClusterModel> collection = new CollectionModel<ClusterModel>();
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
    LOGGER.entering(href(), cluster);
    if (!getBackend().isCluster(getDomainUid(), cluster)) {
      WebApplicationException e = notFound(cluster);
      LOGGER.throwing(e);
      throw e;
    }
    ClusterResource result = new ClusterResource(this, cluster);
    LOGGER.exiting(result);
    return result;
  }

  private String getDomainUid() {
    return getParent().getPathSegment();
  }
}
