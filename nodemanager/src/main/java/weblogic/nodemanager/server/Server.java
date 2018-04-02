// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package weblogic.nodemanager.server;

import java.io.IOException;

public interface Server {
  
  public void init(NMServer nmServer) throws IOException;
  
  public void start(NMServer nmServer) throws IOException;

  public String supportedMode();
}
