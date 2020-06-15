package example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteSync extends Remote {
  public static final String JNDINAME = "propagate.RemoteSync";
  String register() throws RemoteException;
  String registerAndForward(String[] urls) throws RemoteException;
}
