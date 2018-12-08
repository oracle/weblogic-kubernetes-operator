// Copyright  2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import oracle.kubernetes.operator.helpers.ClientFactory;
import oracle.kubernetes.operator.helpers.ClientPool;

public class ClientFactoryStub implements ClientFactory {

  public static Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(ClientPool.class, "FACTORY", new ClientFactoryStub());
  }

  @Override
  public ApiClient get() {
    return new ApiClient();
  }
}
