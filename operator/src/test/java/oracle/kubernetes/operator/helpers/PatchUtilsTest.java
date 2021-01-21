// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;
import javax.json.Json;
import javax.json.JsonPatch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import oracle.kubernetes.operator.utils.PatchUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class PatchUtilsTest {

  @Test
  public void convertJsonPatch_toKubernetesPatch() {
    JsonPatch build =
        Json.createPatchBuilder()
            .add("/metadata/labels/age", 27)
            .replace("/metadata/labels/run", "456")
            .build();
    List<JsonObject> collect = PatchUtils.toKubernetesPatch(build);

    String expectedString = "[{\"op\":\"add\",\"path\":\"/metadata/labels/age\",\"value\":27},{\"op\":\"replace\","
        + "\"path\":\"/metadata/labels/run\",\"value\":\"456\"}]";
    assertThat(serialize(collect), equalTo(expectedString));
  }

  private String serialize(List<JsonObject> collect) {
    return new Gson().toJson(collect);
  }
}
