// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import weblogic.application.ApplicationException;
import weblogic.application.ApplicationLifecycleEvent;
import weblogic.application.ApplicationLifecycleListener;

public class LifecycleListenerImpl extends ApplicationLifecycleListener {

  public void preStart(ApplicationLifecycleEvent evt)
      throws ApplicationException {
    super.preStart(evt);
    try {
      RemoteSyncImpl.main(null);
    } catch (Exception e) {
      throw new ApplicationException(e);
    }
  }
}