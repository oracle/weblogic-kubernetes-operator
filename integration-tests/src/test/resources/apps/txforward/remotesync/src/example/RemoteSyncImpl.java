// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.rmi.RemoteException;
import java.util.Arrays;

import javax.naming.Context;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;

import weblogic.jndi.Environment;
import weblogic.transaction.Transaction;
import weblogic.transaction.TransactionHelper;

public class RemoteSyncImpl implements RemoteSync {
  
  public String register() throws RemoteException {
    Transaction tx = (Transaction) 
        TransactionHelper.getTransactionHelper().getTransaction();
    if (tx == null) return Utils.getLocalServerID() + 
        " no transaction, Synchronization not registered";
    try {
      Synchronization sync = new SynchronizationImpl(tx);
      tx.registerSynchronization(sync);
      return Utils.getLocalServerID() + " " + tx.getXid().toString() + 
          " registered " + sync;
    } catch (IllegalStateException | RollbackException | 
        SystemException e) {
      throw new RemoteException(
          "error registering Synchronization callback with " + 
      tx.getXid().toString(), e);
    }
  }
  
  public String registerAndForward(String[] urls) throws RemoteException {
    StringBuilder trace = new StringBuilder();
    
    Transaction tx = (Transaction) TransactionHelper.getTransactionHelper().getTransaction();
    String xid = (tx != null) ? tx.getXid().toString() : "notx";
    
    if (tx != null) {
      try {
        tx.registerSynchronization(new SynchronizationImpl(tx));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    String serverID = Utils.getLocalServerID();
    
    String rmsg = serverID + " " + xid + " registerAndForward() urls=" + Arrays.toString(urls);
    trace.append(rmsg);
//    System.out.println(rmsg);
    
    if (urls != null && urls.length > 0) {
      trace.append("\n");
      try {
        Context ctx = Utils.getContext(urls[0]);
        RemoteSync remoteSync = (RemoteSync) ctx.lookup(JNDINAME);
        if (remoteSync != null) {
          String smsg = serverID + " " + xid + " invoking registerAndForward() on " + urls[0];
          trace.append(smsg).append("\n");
//          System.out.println(smsg);
          String[] remainingUrls = Arrays.copyOfRange(urls, 1, urls.length);
          trace.append(remoteSync.registerAndForward(remainingUrls));
        }
      } catch (RemoteException re) {
        throw re;
      } catch (Exception e) {
        throw new RemoteException("failed to propagate from " + serverID + " to " + urls[0] + ": " + e.toString());
      }
    }
    
    return trace.toString();
  }

  class SynchronizationImpl implements Synchronization {
    Transaction tx;
    
    SynchronizationImpl(Transaction tx) {
      this.tx = tx;
    }
    
    public void afterCompletion(int arg0) {
      System.out.println(Utils.getLocalServerID() + " " + 
          tx.getXid().toString() + " afterCompletion()");
    }

    public void beforeCompletion() {
      System.out.println(Utils.getLocalServerID() + " " + 
          tx.getXid().toString() + " beforeCompletion()");
    }
  }
  
  // create and bind remote object in local JNDI
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

