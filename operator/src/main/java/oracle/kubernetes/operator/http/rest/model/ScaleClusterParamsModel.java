// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kubernetes.client.openapi.models.V1ScaleSpec;

/**
 * ScaleClusterParamsModel describes the input parameters to the WebLogic cluster scaling operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScaleClusterParamsModel extends BaseModel {

  /**
   * The desired number of managed servers in the WebLogic cluster.
   *
   * @deprecated Deprecated since release 4.0. Please use {#spec} instead.
   */
  @Deprecated(since = "4.0")
  private int managedServerCount;

  private V1ScaleSpec spec;

  /**
   * Get the desired number of managed servers in the WebLogic cluster.
   *
   * @return the desired number of managed servers.
   */
  public int getManagedServerCount() {
    return managedServerCount;
  }

  /**
   * Get the ScaleSpec containing the desired replicas.
   *
   * @return the V1ScaleSpec object containing the desired replicas.
   */
  public V1ScaleSpec getSpec() {
    return spec;
  }

  /**
   * Get the desired replicas for the WebLogic cluster.
   *
   * @return the desired replicas for the WebLogic cluster.
   */
  public int getReplicas() {
    return Optional.ofNullable(spec)
        .map(V1ScaleSpec::getReplicas)
        .orElse(getManagedServerCount());
  }

  /**
   * Set the desired number of managed servers in the WebLogic cluster.
   *
   * @param managedServerCount - the desired number of managed servers.
   */
  public void setManagedServerCount(int managedServerCount) {
    this.managedServerCount = managedServerCount;
  }

  /**
   * Set the ScaleSpec containing the desired replicas.
   *
   * @param scaleSpec V1ScaleSpec object containing the desired replicas
   */
  public void setSpec(V1ScaleSpec scaleSpec) {
    this.spec = scaleSpec;
  }

  @Override
  protected String propertiesToString() {
    return "spec= "
        + Optional.ofNullable(spec).map(V1ScaleSpec::toString).orElse("<null>")
        + ", managedServerCount=" + getManagedServerCount(); // super has no properties
  }
}
