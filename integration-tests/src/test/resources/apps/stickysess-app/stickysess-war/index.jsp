<%--
Copyright (c) 2020, 2021, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
--%>
<%@ page contentType="text/html; charset=utf-8" %>
<%@ page import="java.security.AccessController,
                 javax.servlet.http.HttpSession,
                 weblogic.management.provider.ManagementService,
                 weblogic.security.acl.internal.AuthenticatedSubject,
                 weblogic.security.service.PrivilegedActions" %>
<%!
     public String getServerName() {
          AuthenticatedSubject kernelId =
                  (AuthenticatedSubject)
                          AccessController.doPrivileged(PrivilegedActions.getKernelIdentityAction());

          String myServerName = ManagementService.getRuntimeAccess(kernelId).getServerName();

          return myServerName;
     }
%>
<%
     if (session != null) {
          out.println("<sessioncreatetime>" + session.getCreationTime() + "</sessioncreatetime>");
          out.println("<sessionid>" + session.getId() + "</sessionid>");
          out.println("<connectedservername>" + getServerName() + "</connectedservername>");

          if (request.getParameter("getCounter") != null) {
               if (session.isNew()) {
                    out.println("<countattribute>0</countattribute>");
               } else {
                    out.println(
                            "<countattribute>" + session.getAttribute("count") + "</countattribute>");
               }
          } else {
               if (request.getParameter("setCounter") != null) {
                    session.setAttribute("count", Integer.valueOf(request.getParameter("setCounter")));
               } else if (request.getParameter("setCounter") == null && session.isNew()) {
                    session.setAttribute("count", new Integer(1));
               } else {
                    int count = ((Integer) session.getAttribute("count")).intValue();
                    session.setAttribute("count", new Integer(++count));
               }

               out.println("<countattribute>" + session.getAttribute("count") + "</countattribute>");
          }
     } else {
          out.println("<sessioncreatetime>NA</sessioncreatetime>");
          out.println("<sessionid>NA</sessionid>");
          out.println("<connectedservername>NA</connectedservername>");
          out.println("<countattribute>00</countattribute>");
     }
%>