// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Simple validation of basic domain functions")
@IntegrationTest
class Mark implements LoggedTest {

  @Test
  @DisplayName("Test something")
  public void testSomeStuff() {
    int x = 1 + 5;
    logger.info("log message");

    assertThat(true).as("Test that true is true")
        .withFailMessage("OMG! True was not true")
        .isEqualTo(true);
  }

  @Test
  @DisplayName("Test something else")
  public void testThereWasNoException() {
    // this test will fail because it does throw an exception!

    assertThatCode(() ->
        createSecret("bob", // name
            "weblogic", // username
            "welcome1", // password
            "default")) //namespace
        .as("Test that createSecret does not throw an exception")
        .withFailMessage("OMG! createSecret() threw an unexpected exception")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Test catching an exception")
  public void testCatchingAnException() {
    // this test will pass because it does throw an exception of
    // the expected type, and with the expected message

    // when i call createSecret()
    Throwable thrown = catchThrowable(() ->
        createSecret("bob", // name
            "weblogic", // username
            "welcome1", // password
            "default"));

    // then
    assertThat(thrown)
        .as("Test that createSecret does not throw an APIException")
        .withFailMessage("OMG! createSecret() threw an APIException")
        // you can check what kind of exception it is
        .isInstanceOf(ApiException.class)
        // and you can also check the message contains some string...
        .hasMessageContaining("Failed to connect");
  }

}