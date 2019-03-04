// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiClient;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientPoolTest {

  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
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
