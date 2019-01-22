// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiClient;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.Pool.Entry;
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
    assertThat(ClientPool.getInstance().take().value(), instanceOf(ApiClient.class));
  }

  @Test
  public void afterRecycle_takeReturnsSameClient() {
    Entry<ApiClient> apiClient = ClientPool.getInstance().take();
    ClientPool.getInstance().recycle(apiClient);

    assertThat(ClientPool.getInstance().take(), sameInstance(apiClient));
  }

  @Test
  public void onTake_noConnectionTimeout_clientIsValid() {
    ClientPool pool = new ClientPool();
    Entry<ApiClient> apiClient = pool.take();

    assertTrue(apiClient.isValid());
  }

  @Test
  public void onTake_connectionTimeout_clientIsValid() {
    ClientPool pool =
        new ClientPool() {
          @Override
          protected int connectionLifetimeSeconds() {
            return 30;
          }
        };
    Entry<ApiClient> apiClient = pool.take();

    assertTrue(apiClient.isValid());
  }

  @Test
  public void onTake_connectionTimeout_wait_clientNotValid() throws InterruptedException {
    ClientPool pool =
        new ClientPool() {
          @Override
          protected int connectionLifetimeSeconds() {
            return 1;
          }
        };
    Entry<ApiClient> apiClient = pool.take();

    // sleep longer than connection lifetime
    Thread.sleep(3000);

    assertFalse(apiClient.isValid());
  }
}
