// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiClient;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class ClientPoolTest {

  private List<Memento> mementos = new ArrayList<>();

  /**
   * Setup test.
   */
  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void onTake_returnApiClient() {
    assertThat(ClientPool.getInstance().take(), instanceOf(ApiClient.class));
  }

  @Test
  public void afterRecycle_takeReturnsSameClient() {
    ApiClient apiClient = ClientPool.getInstance().take();
    ClientPool.getInstance().recycle(apiClient);

    assertThat(ClientPool.getInstance().take(), sameInstance(apiClient));
  }
}
