// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.applications.sitconfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
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
import weblogic.diagnostics.descriptor.WLDFHarvestedTypeBean;
import weblogic.diagnostics.descriptor.WLDFInstrumentationMonitorBean;
import weblogic.diagnostics.descriptor.WLDFResourceBean;
import weblogic.j2ee.descriptor.wl.JMSBean;
import weblogic.j2ee.descriptor.wl.UniformDistributedTopicBean;
import weblogic.management.configuration.DomainMBean;
import weblogic.management.configuration.JMSSystemResourceMBean;
import weblogic.management.configuration.WLDFSystemResourceMBean;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * Servlet to print Server configuration and JDBC datasource parameters.
 */
public class SitconfigServlet extends HttpServlet {

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
      Logger.getLogger(SitconfigServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  @Override
  public void destroy() {
    try {
      ctx.close();
    } catch (NamingException ex) {
      Logger.getLogger(SitconfigServlet.class.getName()).log(Level.SEVERE, null, ex);
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
      testSystemResourcesJmsAttributeChange(out);
      testSystemResourcesWldfAttributeChange(out);
    }
  }

  public void testSystemResourcesJmsAttributeChange(PrintWriter out) {
    String jmsModuleName = "ClusterJmsSystemResource";
    String topicName = "UniformReplicatedTestTopic";

    out.println("Verifying the configuration changes made by JMS sit config file");
    JMSSystemResourceMBean jmsModule = getJmsSystemModule(jmsModuleName, out);
    if (jmsModule == null) {
      out.println("TEST FAILED");
      return;
    }
    JMSBean jmsResource = jmsModule.getJMSResource();
    UniformDistributedTopicBean uniformDistributedTopic
        = jmsResource.lookupUniformDistributedTopic(topicName);

    out.println("ExpirationPolicy:" + uniformDistributedTopic.getDeliveryFailureParams().getExpirationPolicy());
    out.println("RedeliveryLimit:" + uniformDistributedTopic.getDeliveryFailureParams().getRedeliveryLimit());
  }

  /**
   * Returns the JMSSystemResourceMBean from domain configuration matching the JMS resource name.
   *
   * @param resourceName - name of the JMS system module to lookup
   * @return JMSSystemResourceMBean of the JMS module
   */
  protected JMSSystemResourceMBean getJmsSystemModule(String resourceName, PrintWriter out) {
    out.println("Looking up the jms system module..." + resourceName);
    DomainMBean domain = runtimeService.getDomainConfiguration();
    JMSSystemResourceMBean jmsSysRes = domain.lookupJMSSystemResource(resourceName);
    if (jmsSysRes == null) {
      out.println("JMS Resource is null");
      return null;
    }
    return jmsSysRes;
  }

  /**
   * The testSystemResourcesWldfAttributeAdd test verifies the WLDF-MODULE-0 WLDF system module overridden in
   * diagnostics-WLDF-MODULE-0.xml. The added elements wldf-instrumentation-monitor harvested-type are expected to show
   * up in the domain configuration.
   */
  public void testSystemResourcesWldfAttributeChange(PrintWriter out) {
    final String wldfModName0 = "WLDF-MODULE-0";

    // verify the changed properties
    out.println("Verifying the configuration changes made by WLDF sit config file");
    WLDFSystemResourceMBean wldfSystemModule = getWldfSystemModule(wldfModName0, out);
    if (wldfSystemModule == null) {
      out.println("WLDF TEST FAILED");
      return;
    }
    WLDFResourceBean wldfRes = wldfSystemModule.getWLDFResource();
    String[] monitorsExp = {
      "Connector_After_Inbound",
      "Connector_Around_Outbound",
      "Connector_Around_Tx",
      "Connector_Around_Work",
      "Connector_Before_Inbound"
    };

    WLDFInstrumentationMonitorBean[] wldfInstrumentationMonitors
        = wldfRes.getInstrumentation().getWLDFInstrumentationMonitors();
    String[] monitorsGot = new String[wldfInstrumentationMonitors.length];
    for (int i = 0; i < wldfInstrumentationMonitors.length; i++) {
      monitorsGot[i] = wldfInstrumentationMonitors[i].getName();
      out.println("Monitor got :" + wldfInstrumentationMonitors[i].getName());
    }
    Arrays.sort(monitorsExp);
    Arrays.sort(monitorsGot);
    if (Arrays.equals(monitorsExp, monitorsGot)) {
      out.println("MONITORS:PASSED");
    } else {
      out.println("Didn't get all the configured monitors, expected "
          + Arrays.toString(monitorsExp)
          + " but got "
          + Arrays.toString(monitorsGot));
    }

    String[] harvestedTypesExp = {
      "weblogic.management.runtime.JDBCServiceRuntimeMBean",
      "weblogic.management.runtime.ServerRuntimeMBean"
    };

    WLDFHarvestedTypeBean[] harvestedTypes = wldfRes.getHarvester().getHarvestedTypes();
    String[] harvestedTypesGot = new String[harvestedTypes.length];
    for (int i = 0; i < harvestedTypesGot.length; i++) {
      harvestedTypesGot[i] = harvestedTypes[i].getName();
      out.println("Harvester type :" + harvestedTypes[i].getName());
    }
    Arrays.sort(harvestedTypesExp);
    Arrays.sort(harvestedTypesGot);
    if (Arrays.equals(harvestedTypesExp, harvestedTypesGot)) {
      out.println("HARVESTORS:PASSED");
    } else {
      out.println("Didn't get all the configured harvesters, expected "
          + Arrays.toString(harvestedTypesExp)
          + " but got "
          + Arrays.toString(harvestedTypesGot));
      out.println("HARVESTORS:FAILED");
    }

    for (String harvestor : harvestedTypesExp) {
      WLDFHarvestedTypeBean lookupHarvestedType
          = wldfRes.getHarvester().lookupHarvestedType(harvestor);
      if (lookupHarvestedType.getName().equals(harvestor)) {
        out.println("HARVESTOR MATCHED:" + harvestor);
      } else {
        out.println("Harvested type name doesn't match");
      }
    }
  }

  /**
   * Returns the WLDFSystemResourceMBean from domain configuration matching the WLDF resource name.
   *
   * @param resourceName - name of the WLDF system module to lookup
   * @return the WLDFSystemResourceMBean
   */
  protected WLDFSystemResourceMBean getWldfSystemModule(String resourceName, PrintWriter out) {
    out.println("Looking up the wldf system module..." + resourceName);
    DomainMBean domain = runtimeService.getDomainConfiguration();
    WLDFSystemResourceMBean wldfResource = domain.lookupWLDFSystemResource(resourceName);
    if (wldfResource == null) {
      out.println("WLDF Resource MBean is null");
      return null;
    }
    return wldfResource;

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
