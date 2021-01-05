// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.applications.clusterview;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import weblogic.j2ee.descriptor.wl.JDBCConnectionPoolParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCDataSourceParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCDriverParamsBean;
import weblogic.j2ee.descriptor.wl.JDBCPropertyBean;
import weblogic.management.configuration.JDBCSystemResourceMBean;
import weblogic.management.configuration.ServerMBean;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.JDBCDataSourceRuntimeMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * Servlet to print Server configuration and JDBC datasource parameters.
 */
public class ConfigServlet extends HttpServlet {

  Context ctx = null;
  MBeanServer localMBeanServer;
  MBeanServer domainMBeanServer;
  ServerRuntimeMBean serverRuntime;
  RuntimeServiceMBean runtimeService;
  DomainRuntimeServiceMBean domainRuntimeServiceMbean;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      ctx = new InitialContext();
      localMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
      // get ServerRuntimeMBean
      ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
      runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(localMBeanServer, runtimeserviceObjectName);
      serverRuntime = runtimeService.getServerRuntime();

      if (serverRuntime.isAdminServer()) {
        domainMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/domainRuntime");
        ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
        domainRuntimeServiceMbean = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler
            .newProxyInstance(domainMBeanServer, domainServiceObjectName);
      }

    } catch (MalformedObjectNameException | NamingException ex) {
      Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  @Override
  public void destroy() {
    try {
      ctx.close();
    } catch (NamingException ex) {
      Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
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

      // check attributes of server configurations
      String attributeTest = request.getParameter("attributeTest");
      if (attributeTest != null) {
        printServerAttributes(request, out);
      }

      // test JDBC connection pool
      String dsTest = request.getParameter("dsTest");
      if (dsTest != null) {
        testJdbcConnection(request, out);
      }

      // print JDBC datasource parameters
      String resTest = request.getParameter("resTest");
      if (resTest != null) {
        printResourceAttributes(request, out);
      }

      String restart = request.getParameter("restartDS");
      if (restart != null) {
        restartJDBCResource(request, out);
      }

    }
  }

  private void printServerAttributes(HttpServletRequest request, PrintWriter out) {
    String serverType = request.getParameter("serverType");
    String serverName = request.getParameter("serverName");
    ServerMBean serverConfiguration = getServerMBean(serverType, serverName);
    out.println("MaxMessageSize=" + serverConfiguration.getMaxMessageSize());
  }

  private void printResourceAttributes(HttpServletRequest request, PrintWriter out) {
    String resName = request.getParameter("resName");
    if (resName != null) {
      JDBCSystemResourceMBean jdbcSystemResource = getJDBCSystemResource(resName);
      JDBCConnectionPoolParamsBean connPool = jdbcSystemResource.getJDBCResource().getJDBCConnectionPoolParams();
      JDBCDriverParamsBean driverParams = jdbcSystemResource.getJDBCResource().getJDBCDriverParams();
      JDBCDataSourceParamsBean dsParams = jdbcSystemResource.getJDBCResource().getJDBCDataSourceParams();

      //print connection pool parameters
      getConnectionPoolAttributes(connPool, out);

      //print driver parameteres
      getDriverParamAttributes(driverParams, out);

      try {
        //print data source parameters
        getAttributes(dsParams.getClass().getDeclaredMethods(), dsParams, out);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        Logger.getLogger(ConfigServlet.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }

  private void getAttributes(Method[] declaredMethods, Object obj, PrintWriter out)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

    for (Method declaredMethod : declaredMethods) {
      String name = declaredMethod.getName();
      if (name.startsWith("get")) {
        declaredMethod.setAccessible(true);
        out.println(name + ":" + declaredMethod.invoke(obj) + "<br>");
      }
    }
  }

  private void getConnectionPoolAttributes(JDBCConnectionPoolParamsBean pool, PrintWriter out) {
    try {
      getAttributes(pool.getClass().getDeclaredMethods(), pool, out);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      Logger.getLogger(ConfigServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private void getDriverParamAttributes(JDBCDriverParamsBean driverParams, PrintWriter out) {
    out.println("driverName:" + driverParams.getDriverName() + "<br>");
    out.println("Url:" + driverParams.getUrl() + "<br>");
    JDBCPropertyBean[] properties = driverParams.getProperties().getProperties();
    for (JDBCPropertyBean property : properties) {
      out.println("property:" + property.getName() + ":" + property.getValue() + "<br>");
    }
  }

  private void testJdbcConnection(HttpServletRequest request, PrintWriter out) {

    String dsName = request.getParameter("dsName");
    String serverName = request.getParameter("serverName");
    System.out.println("ITTESTS:>>>>Testing connection pool in datasource : " + dsName + " in server " + serverName);
    try {
      ServerRuntimeMBean serverRuntime = getServerRuntime(serverName);
      JDBCDataSourceRuntimeMBean[] jdbcDataSourceRuntimeMBeans = serverRuntime.getJDBCServiceRuntime().getJDBCDataSourceRuntimeMBeans();
      System.out.println("ITTESTS:>>>>Getting datasource runtime mbeans");
      for (JDBCDataSourceRuntimeMBean jdbcDataSourceRuntimeMBean : jdbcDataSourceRuntimeMBeans) {
        System.out.println("ITTESTS:>>>>Found JDBC datasource runtime mbean: " + jdbcDataSourceRuntimeMBean.getName());
        if (jdbcDataSourceRuntimeMBean.getName().equals(dsName)) {
          System.out.println("ITTESTS:>>>>Testing connection pool for JDBC datasource runtime mbean: " + jdbcDataSourceRuntimeMBean.getName());
          String testPool = jdbcDataSourceRuntimeMBean.testPool();
          if (testPool == null) {
            out.println("Connection successful");
          }
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void restartJDBCResource(HttpServletRequest request, PrintWriter out) {
    String dsName = request.getParameter("dsName");
    String serverName = request.getParameter("serverName");

    ServerRuntimeMBean serverRuntime = getServerRuntime(serverName);
    JDBCDataSourceRuntimeMBean[] jdbcDataSourceRuntimeMBeans = serverRuntime.getJDBCServiceRuntime().getJDBCDataSourceRuntimeMBeans();
    for (JDBCDataSourceRuntimeMBean jdbcDataSourceRuntimeMBean : jdbcDataSourceRuntimeMBeans) {
      if (jdbcDataSourceRuntimeMBean.getName().equals(dsName)) {
        try {
          String testPool = jdbcDataSourceRuntimeMBean.testPool();
          jdbcDataSourceRuntimeMBean.forceShutdown();
          TimeUnit.SECONDS.sleep(5);
          jdbcDataSourceRuntimeMBean.start();
        } catch (Exception ex) {
          Logger.getLogger(ConfigServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    }
  }

  private ServerMBean getServerMBean(String serverType, String serverName) {
    if (serverType.equalsIgnoreCase("adminserver")) {
      return runtimeService.getServerConfiguration();
    } else {
      return domainRuntimeServiceMbean.findServerConfiguration(serverName);
    }
  }

  private ServerRuntimeMBean getServerRuntime(String serverName) {
    ServerRuntimeMBean[] serverRuntimes = domainRuntimeServiceMbean.getServerRuntimes();
    System.out.println();
    for (ServerRuntimeMBean serverRuntime : serverRuntimes) {
      System.out.println("ITTESTS:>>>>Found server runtime: " + serverRuntime.getName());
      if (serverRuntime.getName().equals(serverName)) {
        return serverRuntime;
      }
    }
    System.out.println("ITTESTS:>>>>No server runtime found for name: " + serverName);
    return null;
  }

  private JDBCSystemResourceMBean getJDBCSystemResource(String jdbcResourceName) {
    return runtimeService.getDomainConfiguration().lookupJDBCSystemResource(jdbcResourceName);
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
