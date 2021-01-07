// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteSync extends Remote {
  public static final String JNDINAME = "txforward.RemoteSync";
  String register() throws RemoteException;
  String registerAndForward(String[] urls) throws RemoteException;
}
