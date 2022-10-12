// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ScaleSpec;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ScaleClusterParamsModelTest {

  @Test
  void parseScaleJsonRequestWithSpec() throws JsonProcessingException {
    final String jsonRequest = "{\"spec\": {\"replicas\": 3 }}";

    ObjectMapper objectMapper = new ObjectMapper();
    ScaleClusterParamsModel paramsModel = objectMapper.readValue(jsonRequest, ScaleClusterParamsModel.class);

    assertThat(paramsModel.getReplicas(), equalTo(3));
    assertThat(paramsModel.getSpec().getReplicas(), equalTo(3));
  }

  @Test
  void parseScaleJsonRequestWithManagedServerCount() throws JsonProcessingException {
    final String jsonRequest = "{\"managedServerCount\": 3 }";

    ObjectMapper objectMapper = new ObjectMapper();
    ScaleClusterParamsModel paramsModel = objectMapper.readValue(jsonRequest, ScaleClusterParamsModel.class);

    assertThat(paramsModel.getReplicas(), equalTo(3));
    assertThat(paramsModel.getManagedServerCount(),equalTo(3));
  }

  @Test
  void propertiesToString_containsReplicaIfSpecified() {
    ScaleClusterParamsModel scaleClusterParamsModel = new ScaleClusterParamsModel();
    scaleClusterParamsModel.setSpec(new V1ScaleSpec().replicas(5));

    assertThat(scaleClusterParamsModel.propertiesToString(),
        allOf(containsString("replicas"),
            containsString("5")));
  }

  @Test
  void propertiesToString_containsManagedServerCountIfSpecified() {
    ScaleClusterParamsModel scaleClusterParamsModel = new ScaleClusterParamsModel();
    scaleClusterParamsModel.setManagedServerCount(5);

    assertThat(scaleClusterParamsModel.propertiesToString(),
        allOf(containsString("managedServerCount"),
            containsString("5")));
  }
}
