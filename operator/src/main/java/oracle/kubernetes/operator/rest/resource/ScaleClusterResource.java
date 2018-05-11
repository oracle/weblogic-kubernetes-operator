// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.core.MediaType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ScaleClusterParamsModel;

/**
 * ScaleResource is a jaxrs resource that implements the REST api for the
 * /operator/{version}/domains/{domainUID}/clusters/{cluster}/scale path. It can be used to scale up
 * or down the number of managed servers in a WebLogic cluster.
 */
public class ScaleClusterResource extends BaseResource {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Construct a ScaleClusterResource.
   *
   * @param parent - the jaxrs resource that parents this resource.
   * @param pathSegment - the last path segment in the url to this resource.
   */
  public ScaleClusterResource(BaseResource parent, String pathSegment) {
    super(parent, pathSegment);
  }

  /**
   * Scale this WebLogic cluster up or down. This method configures the specified numer of managed
   * servers at both the Kubernetes and WebLogic levels, then returns. It does not wait for the new
   * managed servers to start or removed managed servers to stop.
   *
   * @param params - a ScaleClusterParamsModel that specifies the desired number of managed servers
   *     in the cluster
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public void post(final ScaleClusterParamsModel params) {
    LOGGER.entering(href(), params);
    getBackend().scaleCluster(getDomainUid(), getCluster(), params.getManagedServerCount());
    LOGGER.exiting();
  }

  private String getCluster() {
    return getParent().getPathSegment();
  }

  private String getDomainUid() {
    return getParent().getParent().getParent().getPathSegment();
  }
}
