// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import oracle.kubernetes.operator.http.rest.model.ScaleClusterParamsModel;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

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
    getBackend().scaleCluster(getDomainUid(), getCluster(), params.getReplicas());
    LOGGER.exiting();
  }

  private String getCluster() {
    return getParent().getPathSegment();
  }

  private String getDomainUid() {
    return getParent().getParent().getParent().getPathSegment();
  }
}
