// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.applications.clusterview;

import java.io.IOException;
import java.io.PrintWriter;
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
import weblogic.management.configuration.ServerMBean;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * Servlet to print all MBeans names and attributes in the server runtime.
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

      domainMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/domainRuntime");
      ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
      domainRuntimeServiceMbean = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(domainMBeanServer, domainServiceObjectName);

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
      String serverType = request.getParameter("serverType");
      String serverName = request.getParameter("serverName");
      String attribute = request.getParameter("attribute");
      ServerMBean serverConfiguration = getServerMBean(serverType, serverName);

      switch (attribute) {
        case "maxmessagesize":
          int size = getMaxMessageSize(serverConfiguration);
          out.println("MaxMessageSize=" + size);
        default:
          out.println("supported attributes are<br>");
          out.println("MaxMessageSize");
      }
    }
  }

  private int getMaxMessageSize(ServerMBean server) {
    return server.getMaxMessageSize();
  }

  private ServerMBean getServerMBean(String serverType, String serverName) {
    if (serverType.equalsIgnoreCase("adminserver")) {
      return runtimeService.getServerConfiguration();
    } else {
      return domainRuntimeServiceMbean.findServerConfiguration(serverName);
    }
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
