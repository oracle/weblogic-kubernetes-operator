// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.rmi.RemoteException;
import javax.naming.Context;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;

import weblogic.jndi.Environment;
import weblogic.transaction.Transaction;
import weblogic.transaction.TransactionHelper;

public class RemoteSyncImpl implements RemoteSync {

  /**
   * This class gets the Xid and registers synchronization.
   * @return message that the Xid is registered.
   * @throws RemoteException - throws RemoteException.
   */
  public String register() throws RemoteException {
    Transaction tx = (Transaction) 
        TransactionHelper.getTransactionHelper().getTransaction();
    if (tx == null) {
      return Utils.getLocalServerID() + " no transaction, Synchronization not registered";
    }
    try {
      Synchronization sync = new SynchronizationImpl(tx);
      tx.registerSynchronization(sync);
      return Utils.getLocalServerID() + " " + tx.getXid().toString() + " registered " + sync;
    } catch (IllegalStateException | RollbackException
        | SystemException e) {
      throw new RemoteException(
          "error registering Synchronization callback with " + tx.getXid().toString(), e);
    }
  }

  class SynchronizationImpl implements Synchronization {
    Transaction tx;
    
    SynchronizationImpl(Transaction tx) {
      this.tx = tx;
    }
    
    public void afterCompletion(int arg0) {
      System.out.println(Utils.getLocalServerID() + " "
          + tx.getXid().toString() + " afterCompletion()");
    }

    public void beforeCompletion() {
      System.out.println(Utils.getLocalServerID() + " "
          + tx.getXid().toString() + " beforeCompletion()");
    }
  }
  
  //

  /**
   * create and bind remote object in local JNDI.
   * @param args - no args
   * @throws Exception - throws Exception
   */
  public static void main(String[] args) throws Exception {
    RemoteSyncImpl remoteSync = new RemoteSyncImpl();
    Environment env = new Environment();
    env.setCreateIntermediateContexts(true);
    env.setReplicateBindings(false);
    Context ctx = env.getInitialContext();
    ctx.rebind(JNDINAME, remoteSync);
    System.out.println("bound " + remoteSync);
  }
}

