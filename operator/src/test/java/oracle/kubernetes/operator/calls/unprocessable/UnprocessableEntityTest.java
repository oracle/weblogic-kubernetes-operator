// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Collections;

import io.kubernetes.client.openapi.ApiException;
import org.junit.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.calls.unprocessable.UnprocessableEntityBuilder.HTTP_UNPROCESSABLE_ENTITY;
import static oracle.kubernetes.operator.calls.unprocessable.UnprocessableEntityBuilder.isUnprocessableEntity;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class UnprocessableEntityTest {

  private static final String KIND = "Pod";
  private static final String NAME = "sample-domain1-admin-server";
  private static final String VALUE = "weblogic-domain-storage-volume";
  private static final String REASON = "FieldValueNotFound";
  private static final String CAUSE_MESSAGE = String.format("Not found: %s", quoted(VALUE));
  private static final String FIELD = "spec.containers[0].volumeMounts[3].name";
  private static final String CAUSE = "\"reason\":" + quoted(REASON) + ",\"message\":" + quoted(CAUSE_MESSAGE)
                              + ",\"field\":" + quoted(FIELD);
  private static final String MESSAGE_1 = KIND + " " + quoted(NAME) + " is invalid: " + FIELD + ": " + CAUSE_MESSAGE;
  private static final String SAMPLE_MESSAGE_BODY
      = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\","
         + "\"message\":" + quoted(MESSAGE_1) + ","
         + "\"reason\":\"Invalid\",\"details\":{\"name\":" + quoted(NAME) + ",\"kind\":" + quoted(KIND) + ","
         + "\"causes\":[{" + CAUSE + "}]},"
         + "\"code\":422}\n";

  private static String escape(String s) {
    return s.replaceAll("\"", "\\\\\"");
  }

  private static String quoted(String s) {
    return '"' + escape(s) + '"';
  }

  @Test
  public void whenErrorIsNotUnprocessableEntity_returnFalse() {
    assertThat(isUnprocessableEntity(new ApiException(HTTP_FORBIDDEN, "")), is(false));
  }

  @Test
  public void whenErrorIsUnprocessableEntity_returnTrue() {
    assertThat(isUnprocessableEntity(new ApiException(HTTP_UNPROCESSABLE_ENTITY, "")), is(true));
  }

  @Test
  public void extractReasonFromException() {
    ApiException exception = new ApiException(HTTP_UNPROCESSABLE_ENTITY, Collections.emptyMap(), SAMPLE_MESSAGE_BODY);

    UnprocessableEntityBuilder builder = UnprocessableEntityBuilder.fromException(exception);

    assertThat(builder.getReason(), equalTo("FieldValueNotFound"));
  }

  @Test
  public void extractMessageFromException() {
    ApiException exception = new ApiException(HTTP_UNPROCESSABLE_ENTITY, Collections.emptyMap(), SAMPLE_MESSAGE_BODY);

    UnprocessableEntityBuilder builder = UnprocessableEntityBuilder.fromException(exception);

    assertThat(builder.getMessage(), equalTo(MESSAGE_1));
  }

  @Test
  public void constructTestException() {
    ApiException exception = new UnprocessableEntityBuilder()
        .withReason("SomethingWrong")
        .withMessage("This explanation")
        .build();

    UnprocessableEntityBuilder builder = UnprocessableEntityBuilder.fromException(exception);

    assertThat(builder.getReason(), equalTo("SomethingWrong"));
    assertThat(builder.getMessage(), equalTo("This explanation"));
  }
}
