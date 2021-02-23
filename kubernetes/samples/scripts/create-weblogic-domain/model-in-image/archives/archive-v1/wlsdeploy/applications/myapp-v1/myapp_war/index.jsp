<%-- Copyright (c) 2019, 2021, Oracle and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>
<%@ page import="javax.naming.InitialContext" %>
<%@ page import="javax.management.*" %>
<%@ page import="java.io.*" %>
<%
  InitialContext ic = null;
  try {
    ic = new InitialContext();

    String srName=System.getProperty("weblogic.Name");
    String domainUID=System.getenv("DOMAIN_UID");
    String domainName=System.getenv("CUSTOM_DOMAIN_NAME");

    out.println("<html><body><pre>");
    out.println("*****************************************************************");
    out.println();
    out.println("Hello World! This is version 'v1' of the mii-sample JSP web-app.");
    out.println();
    out.println("Welcome to WebLogic Server '" + srName + "'!");
    out.println();
    out.println("  domain UID  = '" + domainUID +"'");
    out.println("  domain name = '" + domainName +"'");
    out.println();

    MBeanServer mbs = (MBeanServer)ic.lookup("java:comp/env/jmx/runtime");

    // display the current server's cluster name
    
    Set<ObjectInstance> clusterRuntimes = mbs.queryMBeans(new ObjectName("*:Type=ClusterRuntime,*"), null);
    out.println("Found " + clusterRuntimes.size() + " local cluster runtime" + (String)((clusterRuntimes.size()!=1)?"s":"") + ":");
    for (ObjectInstance clusterRuntime : clusterRuntimes) {
       String cName = (String)mbs.getAttribute(clusterRuntime.getObjectName(), "Name");
       out.println("  Cluster '" + cName + "'");
    }
    out.println();

    // display work manager configuration created by the sample
    
    Set<ObjectInstance> minTCRuntimes = mbs.queryMBeans(new ObjectName("*:Type=MinThreadsConstraintRuntime,Name=SampleMinThreads,*"), null);
    for (ObjectInstance minTCRuntime : minTCRuntimes) {
       String cName = (String)mbs.getAttribute(minTCRuntime.getObjectName(), "Name");
       int count = (int)mbs.getAttribute(minTCRuntime.getObjectName(), "ConfiguredCount");
       out.println("Found min threads constraint runtime named '" + cName + "' with configured count: " + count);
    }
    out.println();

    Set<ObjectInstance> maxTCRuntimes = mbs.queryMBeans(new ObjectName("*:Type=MaxThreadsConstraintRuntime,Name=SampleMaxThreads,*"), null);
    for (ObjectInstance maxTCRuntime : maxTCRuntimes) {
       String cName = (String)mbs.getAttribute(maxTCRuntime.getObjectName(), "Name");
       int count = (int)mbs.getAttribute(maxTCRuntime.getObjectName(), "ConfiguredCount");
       out.println("Found max threads constraint runtime named '" + cName + "' with configured count: " + count);
    }
    out.println();

    // display local data sources
    // - note that data source tests are expected to fail until the MII sample Update 4 use case updates the datasource's secret
    
    ObjectName jdbcRuntime = new ObjectName("com.bea:ServerRuntime=" + srName + ",Name=" + srName + ",Type=JDBCServiceRuntime");
    ObjectName[] dataSources = (ObjectName[])mbs.getAttribute(jdbcRuntime, "JDBCDataSourceRuntimeMBeans");
    out.println("Found " + dataSources.length + " local data source" + (String)((dataSources.length!=1)?"s":"") + ":");
    for (ObjectName dataSource : dataSources) {
       String dsName  = (String)mbs.getAttribute(dataSource, "Name");
       String dsState = (String)mbs.getAttribute(dataSource, "State");
       String dsTest  = (String)mbs.invoke(dataSource, "testPool", new Object[] {}, new String[] {});
       out.println(
           "  Datasource '" + dsName + "': "
           + " State='" + dsState + "',"
           + " testPool='" + (String)(dsTest==null ? "Passed" : "Failed") + "'"
       );
       if (dsTest != null) {
         out.println(
               "    ---TestPool Failure Reason---\n"
             + "    NOTE: Ignore 'mynewdatasource' failures until the MII sample's Update 4 use case.\n"
             + "    ---\n"
             + "    " + dsTest.replaceAll("\n","\n   ").replaceAll("\n *\n","\n") + "\n"
             + "    -----------------------------");
       }
    }
    out.println();

    out.println("*****************************************************************");

  } catch (Throwable t) {
    t.printStackTrace(new PrintStream(response.getOutputStream()));
  } finally {
    out.println("</pre></body></html>");
    if (ic != null) ic.close();
  }
%>
