<%-- Copyright (c) 2020, Oracle Corporation and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>

<%@page import="java.io.PrintStream"%>
<%@page import="javax.naming.Context"%>
<%@page import="javax.naming.InitialContext"%>
<%@page import="javax.naming.NameNotFoundException"%>
<%@page import="javax.management.MBeanServer"%>
<%@page import="javax.management.ObjectName"%>

<%@page import="weblogic.management.jmx.MBeanServerInvocationHandler"%>
<%@page import="weblogic.management.mbeanservers.runtime.RuntimeServiceMBean"%>
<%@page import="weblogic.management.runtime.ClusterRuntimeMBean"%>
<%@page import="weblogic.management.runtime.ServerRuntimeMBean"%>

<%
  Context ctx = null;
  try {
    out.println("<html><body><pre>");

    ctx = new InitialContext();
    MBeanServer localMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");

    ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
    RuntimeServiceMBean runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
        .newProxyInstance(localMBeanServer, runtimeserviceObjectName);

    ServerRuntimeMBean serverRuntime = runtimeService.getServerRuntime();
    ClusterRuntimeMBean clusterRuntime = serverRuntime.getClusterRuntime();
    
    // get all servers running in cluster
    String[] serverNames = clusterRuntime.getServerNames();
    out.println("Members:" + String.join(",", serverNames));
    // get alive count
    out.println("AliveCount:" + clusterRuntime.getAliveServerCount());
    out.println("Health:" + clusterRuntime.getHealthState().getState());

    // bind the server name in the local JNDI tree
    try {
      ctx.lookup(serverRuntime.getName());
    } catch (NameNotFoundException nnfex) {
      out.println("Binding server: " + serverRuntime.getName() + " : in JNDI tree");
      ctx.bind(serverRuntime.getName(), serverRuntime.getName());
    }
    // lookup JNDI for other clustered servers bound in tree
    try {
      for (String serverName : serverNames) {
        if (ctx.lookup(serverName) != null) {
          out.println("Bound:" + serverName);
        }
      }
    } catch (NameNotFoundException nnfex) {
      out.println(nnfex.getMessage());
    }
  } catch (Throwable t) {
    t.printStackTrace(new PrintStream(response.getOutputStream()));
  } finally {
    out.println("</pre></body></html>");
    if (ctx != null) {
      ctx.close();
    }
  }
%>
