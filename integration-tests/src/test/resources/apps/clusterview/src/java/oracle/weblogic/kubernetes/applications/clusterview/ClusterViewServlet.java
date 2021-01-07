// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.applications.clusterview;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import weblogic.health.HealthState;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.ClusterRuntimeMBean;
import weblogic.management.runtime.DomainRuntimeMBean;
import weblogic.management.runtime.ServerLifeCycleRuntimeMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * Servlet to print all MBeans names and attributes in the server runtime.
 */
public class ClusterViewServlet extends HttpServlet {

  Context ctx = null;
  MBeanServer localMBeanServer;
  ServerRuntimeMBean serverRuntime;
  RuntimeServiceMBean runtimeService;
  MBeanServer domainMBeanServer;
  DomainRuntimeServiceMBean domainRuntimeServiceMbean;
  DomainRuntimeMBean domainRuntime;
  String adminUser;
  String adminPassword;
  List<JMXConnector> jmxConnectors;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      ctx = new InitialContext();
      System.out.println("ClusterViewServlet:>>>>Looking up server runtime mbean server");
      localMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
      // get ServerRuntimeMBean
      ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
      runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(localMBeanServer, runtimeserviceObjectName);
      serverRuntime = runtimeService.getServerRuntime();
      System.out.println("ClusterViewServlet:>>>>Found server runtime mbean server for server: " + serverRuntime.getName());
    } catch (MalformedObjectNameException | NamingException ex) {
      System.out.println("ClusterViewServlet:>>>>ClusterViewServlet.init() threw exception");
      System.out.println("ClusterViewServlet:>>>>" + ex.getMessage());
    }

    // get domain runtime when running in admin server
    if (serverRuntime.isAdminServer()) {
      try {
        System.out.println("ClusterViewServlet:>>>>Looking up domain runtime mbean in server : " + serverRuntime.getName());
        domainMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/domainRuntime");
        ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
        domainRuntimeServiceMbean = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler
            .newProxyInstance(domainMBeanServer, domainServiceObjectName);
        domainRuntime = domainRuntimeServiceMbean.getDomainRuntime();
        System.out.println("ClusterViewServlet:>>>>Found domain runtime mbean in server : " + serverRuntime.getName());
      } catch (MalformedObjectNameException | NamingException ex) {
        System.out.println("ClusterViewServlet:>>>>Looking up domain runtime mbean in server : " + serverRuntime.getName() + " threw exception");
        System.out.println("ClusterViewServlet:>>>>" + ex.getMessage());
      }
    }

  }

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html;charset=UTF-8");
    try (PrintWriter out = response.getWriter()) {
      out.println("<!DOCTYPE html>");
      out.println("<html>");
      out.println("<head>");
      out.println("<title>ClusterViewServlet</title>");
      out.println("</head>");
      out.println("<body>");
      out.println("<pre>");

      jmxConnectors = new ArrayList<>();
      adminUser = request.getParameter("user");
      adminPassword = request.getParameter("password");

      // print the domain name in which this server resides in
      printDomainName(out);
      // print servers list and their health from admin server
      printServersHealth(out);
      // print the name of the server in which this Servlet object is located
      out.println("ServerName:" + runtimeService.getServerName());

      // print cluster health when running in managed servers
      printClusterHealth(out);

      // check connection to other servers in the domain is successful
      connectToOtherServers(out);

      out.println("</pre>");
      out.println("</body>");
      out.println("</html>");
    } finally {
      jmxConnectors.forEach((jmxConnector) -> {
        try {
          System.out.println("Closing MBeanServer connection to : " + jmxConnector.getConnectionId());
          jmxConnector.close();
        } catch (Exception ex) {
          //
        }
      });
    }
  }

  /**
   * Print the domain name. If this is a managed server, make connection to admin server mbean server and find the
   * domain name.
   *
   * @param out PrintWriter to write the output to
   */
  private void printDomainName(PrintWriter out) {
    System.out.println("printDomainName()");
    if (serverRuntime.isAdminServer()) {
      out.println("DomainName:" + domainRuntimeServiceMbean.getDomainConfiguration().getName());
    } else {
      try {
        String host = runtimeService.getServerRuntime().getAdminServerHost();
        int port = runtimeService.getServerRuntime().getAdminServerListenPort();
        MBeanServerConnection mbs = createMBeanServerConnection(host, Integer.toString(port),
            adminUser, adminPassword, RuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
        ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
        RuntimeServiceMBean adminRuntimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(mbs, runtimeserviceObjectName);
        out.println("DomainName:" + adminRuntimeService.getDomainConfiguration().getName());
      } catch (Exception ex) {
        out.println("printDomainName>>>" + ex.getMessage());
      }
    }
  }

  /**
   * When running in admin server, print the health of all servers in the domain.
   *
   * @param out PrintWriter to write the output to
   */
  private void printServersHealth(PrintWriter out) {
    System.out.println("printServersHealth()");
    if (serverRuntime.isAdminServer()) {
      ServerRuntimeMBean[] serverRuntimes = domainRuntimeServiceMbean.getServerRuntimes();
      for (ServerRuntimeMBean serverRuntime : serverRuntimes) {
        out.println(serverRuntime.getName() + ":STATUS<BR>");
        int state = serverRuntime.getHealthState().getState();
        switch (state) {
          case HealthState.HEALTH_OK:
            out.print(serverRuntime.getName() + ":HEALTH_OK");
            break;
          case HealthState.HEALTH_CRITICAL:
            out.print(serverRuntime.getName() + ":HEALTH_CRITICAL");
            break;
          case HealthState.HEALTH_FAILED:
            out.print(serverRuntime.getName() + ":HEALTH_FAILED");
            break;
          case HealthState.HEALTH_OVERLOADED:
            out.print(serverRuntime.getName() + ":HEALTH_OVERLOADED");
            break;
          case HealthState.HEALTH_WARN:
            out.print(serverRuntime.getName() + ":HEALTH_WARN");
            break;
          default:
            out.print(serverRuntime.getName() + ":HEALTH_WARN");
        }
        out.println("<BR>");
      }
    }
  }

  /**
   * Print the cluster details and its members health if this server is part of a cluster.
   *
   * @param out PrintWriter to write the output to
   */
  private void printClusterHealth(PrintWriter out) {
    System.out.println("printClusterHealth()");
    ClusterRuntimeMBean clusterRuntime = serverRuntime.getClusterRuntime();
    //if the server is part of a cluster get its cluster details
    if (clusterRuntime != null) {
      out.println("ClusterName:" + clusterRuntime.getName());
    }
    if (clusterRuntime != null) {
      String[] serverNames = clusterRuntime.getServerNames();
      out.println("Alive:" + clusterRuntime.getAliveServerCount());
      out.println("Health:" + clusterRuntime.getHealthState().getState());
      out.println("Members:" + String.join(",", serverNames));
      out.println("ServerName:" + serverRuntime.getName());
    } else {
      out.println(serverRuntime.getName() + ":Cluster:NULL <BR>");
      System.out.println("ITESTS:>>>>>>Cluster runtime is null in server:" + serverRuntime.getName());
    }
  }

  /**
   * Make connection to other servers in this domain from the current server.
   *
   * @param out PrintWriter to write the output to
   */
  private void connectToOtherServers(PrintWriter out) {
    System.out.println("connectToOtherServers()");
    List<String> serverUrls = getServerUrls();
    for (String serverUrl : serverUrls) {
      if (serverUrl.equals("null")) {
        continue;
      }
      JMXServiceURL url = null;
      try {
        url = new JMXServiceURL("service:jmx:" + serverUrl + "/jndi/" + RuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
      } catch (MalformedURLException ex) {
        Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
      }
      MBeanServerConnection mbeanServerConnection = createMBeanServerConnection(url);
      ObjectName runtimeserviceObjectName = null;
      try {
        runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
      } catch (MalformedObjectNameException ex) {
        Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
      }
      RuntimeServiceMBean runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(mbeanServerConnection, runtimeserviceObjectName);
      ServerRuntimeMBean serverRuntime = runtimeService.getServerRuntime();
      out.println("Success:" + serverRuntime.getName());
    }
  }

  /**
   * Get the administration URLs of all servers in the domain.
   *
   * @return List containing server administration urls.
   */
  private List<String> getServerUrls() {
    System.out.println("getServerUrls()");

    List<String> serverUrls = new ArrayList<>();
    String host = serverRuntime.getAdminServerHost();
    int port = serverRuntime.getAdminServerListenPort();
    MBeanServerConnection mbs = createMBeanServerConnection(host, Integer.toString(port), adminUser, adminPassword, DomainRuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
    ObjectName runtimeserviceObjectName = null;
    try {
      runtimeserviceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
    } catch (MalformedObjectNameException ex) {
      Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
    DomainRuntimeServiceMBean domainRuntimeService = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(mbs, runtimeserviceObjectName);
    DomainRuntimeMBean domainRuntime = domainRuntimeService.getDomainRuntime();
    ServerLifeCycleRuntimeMBean[] serverLifeCycleRuntimes = domainRuntime.getServerLifeCycleRuntimes();
    for (ServerLifeCycleRuntimeMBean serverLifeCycleRuntime : serverLifeCycleRuntimes) {
      //check state and get the url only if its running, also make sure the url is not null
      if (null != serverLifeCycleRuntime.getIPv4URL("t3") && serverLifeCycleRuntime.getState().equals("RUNNING")) {
        serverUrls.add(serverLifeCycleRuntime.getIPv4URL("t3"));
        System.out.println("getIPv4URL(t3):" + serverLifeCycleRuntime.getIPv4URL("t3"));
      }
    }

    return serverUrls;
  }

  protected MBeanServerConnection createMBeanServerConnection(String host, String portString, String user, String password, String jndiName) {
    System.out.println("createMBeanServerConnection()");
    JMXServiceURL serviceURL = null;
    MBeanServerConnection mBeanServerConnection = null;
    try {
      System.out.println("Host: " + host + " Port: " + portString + " username: " + user + " password :" + password);
      String protocol = "t3";
      Integer portInteger = Integer.valueOf(portString);
      int port = portInteger;
      Hashtable h = new Hashtable();
      h.put(Context.SECURITY_PRINCIPAL, user);
      h.put(Context.SECURITY_CREDENTIALS, password);
      h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
      h.put("jmx.remote.x.request.waiting.timeout", Long.valueOf(10000));
      serviceURL = new JMXServiceURL(protocol, host, port, "/jndi/" + jndiName);
      System.out.println("Making mbean server connection with url: " + serviceURL.toString());
      JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, h);
      mBeanServerConnection = jmxConnector.getMBeanServerConnection();
      jmxConnectors.add(jmxConnector);
    } catch (NumberFormatException | IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
    return mBeanServerConnection;
  }

  protected MBeanServerConnection createMBeanServerConnection(JMXServiceURL serviceURL) {
    System.out.println("createMBeanServerConnection()");
    MBeanServerConnection mBeanServerConnection = null;
    try {
      Hashtable h = new Hashtable();
      h.put(Context.SECURITY_PRINCIPAL, adminUser);
      h.put(Context.SECURITY_CREDENTIALS, adminPassword);
      h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
      h.put("jmx.remote.x.request.waiting.timeout", Long.valueOf(10000));
      System.out.println("Making mbean server connection with url: " + serviceURL.toString());
      JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, h);
      mBeanServerConnection = jmxConnector.getMBeanServerConnection();
      jmxConnectors.add(jmxConnector);
    } catch (NumberFormatException | IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
    return mBeanServerConnection;
  }

  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Cluster View Servlet";
  }

}
