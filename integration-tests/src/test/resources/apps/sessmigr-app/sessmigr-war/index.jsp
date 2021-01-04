<%--
Copyright (c) 2020, 2021, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
--%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ page import="javax.servlet.http.HttpSession,
                 weblogic.servlet.internal.MembershipControllerImpl,
                 weblogic.servlet.internal.session.RSID,
                 weblogic.servlet.spi.WebServerRegistry"
%>
<%!
     String getPrimaryServer(String sessionId) {
          MembershipControllerImpl cluster =
                  (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
          RSID rsid = new RSID(sessionId, cluster.getClusterMembers());
          if (rsid.getPrimary() == null) {
               return "No Primary";
          }
          return rsid.getPrimary().getServerName();
     }
     String getSecondaryServer(String sessionId) {
          MembershipControllerImpl cluster =
                  (MembershipControllerImpl) WebServerRegistry.getInstance().getClusterProvider();
          RSID rsid = new RSID(sessionId, cluster.getClusterMembers());
          if (rsid.getSecondary() == null) {
               return "No Secondary";
          }
          return rsid.getSecondary().getServerName();
     }
%>
<%
     if (session != null) {
          if (request.getParameter("invalidate") != null) {
               session.invalidate();
               out.println("Your session is invalidated");
          } else {
               if (request.getParameter("setCounter") != null) {
                    session.setAttribute("count", Integer.valueOf(request.getParameter("setCounter")));
               } else if (request.getParameter("getCounter") != null) {
                    session.setAttribute("count", ((Integer) session.getAttribute("count")));
               } else if (request.getParameter("setCounter") == null && session.isNew()) {
                    session.setAttribute("count", new Integer(1));
               } else {
                    int count = ((Integer) session.getAttribute("count")).intValue();
                    session.setAttribute("count", new Integer(++count));
               }
               out.println(
                       "<sessioncreatetime>" + session.getCreationTime() + "</sessioncreatetime>");
               out.println("<sessionid>" + session.getId() + "</sessionid>");
               out.println("<primary>" + getPrimaryServer(session.getId()) + "</primary>");
               out.println("<secondary>" + getSecondaryServer(session.getId()) + "</secondary>");
               out.println(
                       "<countattribute>" + session.getAttribute("count") + "</countattribute>");
          }
     } else {
          out.println("<sessioncreatetime>NA</sessioncreatetime>");
          out.println("<sessionid>NA</sessionid>");
          out.println("<primary>NA</primary>");
          out.println("<secondary>NA</secondary>");
          out.println("<countattribute>00</countattribute>");
     }
%>