<%-- Copyright (c) 2020, 2021, Oracle and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>

<%@page import="java.io.PrintStream"%>
<%@page import="javax.naming.Context"%>
<%@page import="javax.naming.InitialContext"%>
<%@page import="javax.management.MalformedObjectNameException"%>
<%@page import="javax.management.MBeanServer"%>
<%@page import="javax.management.ObjectName"%>
<%@page import="javax.naming.NamingException"%>
<%@page import="javax.naming.NameNotFoundException"%>

<%@page import="weblogic.management.jmx.MBeanServerInvocationHandler"%>
<%@page import="weblogic.management.mbeanservers.runtime.RuntimeServiceMBean"%>
<%@page import="weblogic.management.runtime.ClusterRuntimeMBean"%>
<%@page import="weblogic.management.runtime.ServerRuntimeMBean"%>

<%!
  Context ctx = null;
  MBeanServer localMBeanServer;
  ServerRuntimeMBean serverRuntime;

  public void jspInit() {

    try {
      ctx = new InitialContext();
      localMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
      // get ServerRuntimeMBean
      ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
      RuntimeServiceMBean runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(localMBeanServer, runtimeserviceObjectName);
      serverRuntime = runtimeService.getServerRuntime();
      try {
        ctx.lookup(serverRuntime.getName());
      } catch (NameNotFoundException nnfe) {
        ctx.bind(serverRuntime.getName(), serverRuntime.getName());
      }
    } catch (NamingException | MalformedObjectNameException ex) {
      ex.printStackTrace();
    }
  }

  public void jspDestroy() {
    try {
      ctx.unbind(serverRuntime.getName());
      ctx.close();
    } catch (NamingException ex) {
      ex.printStackTrace();
    }
  }

%>

<%
  out.println("<html><body><pre>");
  out.println("ServerName:" + serverRuntime.getName());

  ClusterRuntimeMBean clusterRuntime = serverRuntime.getClusterRuntime();
  //if the server is part of a cluster get its cluster details
  if (clusterRuntime != null) {
    String[] serverNames = clusterRuntime.getServerNames();
    out.println("Alive:" + clusterRuntime.getAliveServerCount());
    out.println("Health:" + clusterRuntime.getHealthState().getState());
    out.println("Members:" + String.join(",", serverNames));

    // lookup JNDI for other clustered servers bound in tree
    for (String serverName : serverNames) {
      try {
        if (ctx.lookup(serverName).equals(serverName)) {
          out.println("Bound:" + serverName);
        }
      } catch (NamingException nex) {
        out.println(nex.getMessage());
      }
    }
  }
  
  out.println("</pre></body></html>");  
%>
