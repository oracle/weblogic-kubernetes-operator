// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ResultTest {

  @Test
  public void verifyIsServerOverloadedReturnsTrue() {
    Result result500 = new Result("response", 500, false);
    assertThat(result500.isServerOverloaded(), is(true));

    Result result503 = new Result("response", 503, false);
    assertThat(result503.isServerOverloaded(), is(true));
  }

  @Test
  public void verifyIsOverloadedReturnsFalseForOtherResponseCode() {
    Result result404 = new Result("response", 404, false);
    assertThat(result404.isServerOverloaded(), is(false));

    Result result200 = new Result("response", 200, false);
    assertThat(result200.isServerOverloaded(), is(false));
  }
}
