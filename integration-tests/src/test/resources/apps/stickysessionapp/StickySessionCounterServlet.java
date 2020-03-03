// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package apps.stickysessionapp;

import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.security.AccessController;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import weblogic.management.provider.ManagementService;
import weblogic.security.acl.internal.AuthenticatedSubject;
import weblogic.security.service.PrivilegedActions;

/**
 * Simple HTTP servlet class for a client to 1. query weblogic server name on which the client HTTP
 * request is handled 2. set or get a count number
 */
public class StickySessionCounterServlet extends HttpServlet {

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

    out.println("<result>");
    out.println("<sessioncreatetime>" + currentSession.getCreationTime() + "</sessioncreatetime>");
    out.println("<sessionid>" + currentSession.getId() + "</sessionid>");
    out.println("<connectedservername>" + getServerName() + "</connectedservername>");

    if (request.getParameter("getCounter") != null) {
      if (currentSession.isNew()) {
        out.println("<countattribute>0</countattribute>");
      } else {
        out.println(
            "<countattribute>" + currentSession.getAttribute("count") + "</countattribute>");
      }
    } else {
      if (request.getParameter("setCounter") != null) {
        currentSession.setAttribute("count", Integer.valueOf(request.getParameter("setCounter")));
      } else if (request.getParameter("setCounter") == null && currentSession.isNew()) {
        currentSession.setAttribute("count", new Integer(1));
      } else {
        int count = ((Integer) currentSession.getAttribute("count")).intValue();
        currentSession.setAttribute("count", new Integer(++count));
      }

      out.println("<countattribute>" + currentSession.getAttribute("count") + "</countattribute>");
    }

    out.println("</result>");
  }

  /**
   * Method to return the server name on which the client HTTP request is handled.
   *
   * @return - a server name
   * @throws RemoteException excpetion
   */
  public String getServerName() throws RemoteException {
    AuthenticatedSubject kernelId =
        (AuthenticatedSubject)
            AccessController.doPrivileged(PrivilegedActions.getKernelIdentityAction());

    String myServerName = ManagementService.getRuntimeAccess(kernelId).getServerName();

    return myServerName;
  }
}
