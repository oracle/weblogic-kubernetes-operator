// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ClusterModel;

/**
 * ClusterResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains/{domainUID}/clusters/{cluster} path. It can be used to describe a
 * WebLogic cluster and to traverse to its child resources.
 */
public class ClusterResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a ClusterResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public ClusterResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Get a description of this Weblogic cluster.
   *
   * @return a ClusterModel describing this cluster.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public ClusterModel get() {
    LOGGER.entering(href());
    ClusterModel item = new ClusterModel(getCluster());
    addSelfAndParentLinks(item);
    addActionLink(item, "scale");
    LOGGER.exiting(item);
    return item;
  }

  /**
   * Construct and return the 'scale' jaxrs child resource.
   *
   * @return the scale sub resource.
   */
  @Path("scale")
  public ScaleClusterResource getScaleClusterResource() {
    LOGGER.entering();
    ScaleClusterResource result = new ScaleClusterResource(this, "scale");
    LOGGER.exiting(result);
    return result;
  }

  private String getCluster() {
    return getPathSegment();
  }
}
