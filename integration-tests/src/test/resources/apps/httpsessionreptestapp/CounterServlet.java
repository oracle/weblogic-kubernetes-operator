// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package apps.httpsessionreptestapp;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import weblogic.servlet.internal.MembershipControllerImpl;
import weblogic.servlet.internal.session.RSID;
import weblogic.servlet.spi.WebServerRegistry;

/**
 * Simple HTTP servlet class for a client to 1. query weblogic primary and secondary server 2. set
 * or get a count number
 */
public class CounterServlet extends HttpServlet {
  private String message;

  public CounterServlet() {
    setServletName(this.getClass().getName());
  }

  /**
   * Method to handle GET method request.
   *
   * @param request - HTTP request
   * @param response - HTTP response
   * @throws IOException io exception
   * @throws ServletException servlet exception
   */
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    process(request, response);
  }

  /**
   * Method to handle POST method request.
   *
   * @param request - HTTP request
   * @param response - HTTP response
   * @throws IOException io exception
   * @throws ServletException servlet exception
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    process(request, response);
  }

  /**
   * Method to handle PROCESS method request.
   *
   * @param request - HTTP request
   * @param response - HTTP response
   * @throws IOException io exception
   * @throws ServletException servlet exception
   */
  protected void process(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    response.setContentType("text/xml; charset=UTF-8");
    PrintWriter out = response.getWriter();
    HttpSession currentSession = request.getSession(true);

    if (currentSession != null) {
      out.println("<result>");

      if (request.getParameter("delayTime") != null) {
        try {
          Integer ival = (Integer)currentSession.getAttribute("simplesession.counter");
          if (ival == null) {
            // Initialize the counter
            ival = new Integer(1);
          } else {
            // Increment the counter
            ival = new Integer(ival.intValue() + 1);
          }
          // Set the new attribute value in the session
          currentSession.setAttribute("simplesession.counter", ival);

          currentSession.setMaxInactiveInterval(Integer.valueOf(request.getParameter("delayTime")));
          out.println(
              "<sleep>Starting to sleep : "
                  + (Integer.valueOf(request.getParameter("delayTime")))
                  + "</sleep>");
          Thread.sleep(Integer.valueOf(request.getParameter("delayTime")));
          out.println("<sleep>Ending to sleep</sleep>");
        } catch (Exception ex) {
          // just ignore
        }
      }

      if (request.getParameter("invalidate") != null) {
        currentSession.invalidate();
        out.println("Your session is invalidated");
      } else {
        if (request.getParameter("setCounter") != null) {
          currentSession.setAttribute("count", Integer.valueOf(request.getParameter("setCounter")));
        } else if (request.getParameter("getCounter") != null) {
          currentSession.setAttribute("count", ((Integer) currentSession.getAttribute("count")));
        } else if (request.getParameter("setCounter") == null && currentSession.isNew()) {
          currentSession.setAttribute("count", new Integer(1));
        } else {
          int count = ((Integer) currentSession.getAttribute("count")).intValue();
          currentSession.setAttribute("count", new Integer(++count));
        }

        out.println(
            "<sessioncreatetime>" + currentSession.getCreationTime() + "</sessioncreatetime>");
        out.println("<sessionid>" + currentSession.getId() + "</sessionid>");
        out.println("<primary>" + getPrimaryServer(currentSession.getId()) + "</primary>");
        out.println("<secondary>" + getSecondaryServer(currentSession.getId()) + "</secondary>");
        out.println(
            "<countattribute>" + currentSession.getAttribute("count") + "</countattribute>");
      }

      out.println("</result>");
    } else {
      out.println("<result>");
      out.println("<primary>NA</primary>");
      out.println("<secondary>NA</secondary>");
      out.println("<countattribute>00</countattribute>");
      out.println("</result>");
    }
  }

  /**
   * Method to return the primary server name.
   *
   * @param sessionId - HTTP session ID
   * @return the weblogic primary server name
   */
  private String getPrimaryServer(String sessionId) {
    MembershipControllerImpl cluster =
        (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
    RSID rsid = new RSID(sessionId, cluster.getClusterMembers());

    if (rsid.getPrimary() == null) {
      return "No Primary";
    }

    return rsid.getPrimary().getServerName();
  }

  /**
   * Method to return the secondary server name.
   *
   * @param sessionId - HTTP session ID
   * @return the weblogic secondary server name
   */
  private String getSecondaryServer(String sessionId) {
    MembershipControllerImpl cluster =
        (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
    RSID rsid = new RSID(sessionId, cluster.getClusterMembers());

    if (rsid.getSecondary() == null) {
      return "No Secondary";
    }

    return rsid.getSecondary().getServerName();
  }

  /**
   * Method to get the servlet name.
   *
   * @return the servlet name
   */
  public String getServletName() {
    return this.message;
  }

  /**
   * Method to set servlet name.
   *
   * @param sessionId - HTTP session ID
   */
  private void setServletName(String message) {
    this.message = message;
  }
}
