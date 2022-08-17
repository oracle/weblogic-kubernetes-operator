// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ScaleClusterParamsModelTest {

  @Test
  public void parseScaleJsonRequestWithSpec() throws JsonProcessingException {
    final String jsonRequest = "{\"spec\": {\"replicas\": 3 }}";

    ObjectMapper objectMapper = new ObjectMapper();
    ScaleClusterParamsModel paramsModel = objectMapper.readValue(jsonRequest, ScaleClusterParamsModel.class);

    assertThat(paramsModel.getReplicas(), equalTo(3));
  }

  @Test
  public void parseScaleJsonRequestWithManagedServerCount() throws JsonProcessingException {
    final String jsonRequest = "{\"managedServerCount\": 3 }";

    ObjectMapper objectMapper = new ObjectMapper();
    ScaleClusterParamsModel paramsModel = objectMapper.readValue(jsonRequest, ScaleClusterParamsModel.class);

    assertThat(paramsModel.getReplicas(), equalTo(3));
  }
}
