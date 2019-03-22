package com.oracle.kubernetes.examples;

import java.io.IOException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public final class ListenAddressAndPort {
  public static String getListenAddress() throws IOException {
    InitialContext ctx = null;
    MBeanServer server = null;
    try {
      ctx = new InitialContext();
      server = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
    } catch (NamingException ne) {
      throw new IOException("Could not get MBeanServer: " + ne.getMessage(), ne);
    }

    String serverName = System.getProperty("weblogic.Name");
    ObjectName name;
    String listenAddress = null;
    String listenPort = null;
    try {
      name = new ObjectName("com.bea:Name=" + serverName + ",Type=ServerRuntime");
      listenAddress = (String) server.getAttribute(name, "ListenAddress");
      listenPort = ((Integer) server.getAttribute(name, "ListenPort")).toString();
    } catch (Exception e) {
      String msg = "Malformed Object Name: " + e.getMessage();
      System.err.println(msg);
      e.printStackTrace(System.err);
      throw new IOException(msg, e);
    }
    // Server Config MBean returns setting for ListenAddress,
    // which may be empty if it is not set.
    //
    // ServerRuntime MBean returns the listen address as hostname/ip
    //
    listenAddress = listenAddress.substring(0, listenAddress.lastIndexOf('/'));
    return listenAddress + ":" + listenPort;
  }
}
