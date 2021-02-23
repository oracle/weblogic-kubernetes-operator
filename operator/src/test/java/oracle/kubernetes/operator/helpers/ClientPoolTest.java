// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiClient;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;

public class ClientPoolTest {

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(ClientFactoryStub.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
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
