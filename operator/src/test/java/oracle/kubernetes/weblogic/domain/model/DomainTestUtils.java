// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import oracle.kubernetes.operator.helpers.GsonOffsetDateTime;

public class DomainTestUtils {

  public DomainResource readDomain(String resourceName) throws IOException {
    return readDomain(resourceName, false);
  }

  public DomainResource readDomain(String resourceName, boolean isFile) throws IOException {
    String json = jsonFromYaml(resourceName, isFile);
    return getGsonBuilder().fromJson(json, DomainResource.class);
  }

  private String jsonFromYaml(String resourceName, boolean isFile) throws IOException {
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj;
    if (isFile) {
      obj = yamlReader.readValue(new File(resourceName), Object.class);
    } else {
      obj = yamlReader.readValue(resourceName, Object.class);
    }

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }

  private Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }
}
