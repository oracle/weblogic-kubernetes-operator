<%--
Copyright (c) 2020, Oracle Corporation and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
--%>
<%@ page contentType="text/html; charset=utf-8" %>
<%@ page import="javax.servlet.http.HttpSession,
                 weblogic.servlet.internal.MembershipControllerImpl,
                 weblogic.servlet.internal.session.RSID,
                 weblogic.servlet.spi.WebServerRegistry" %>
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
     HttpSession currentSession = request.getSession(true);

     if (currentSession != null) {
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
     } else {
          out.println("<primary>NA</primary>");
          out.println("<secondary>NA</secondary>");
          out.println("<countattribute>00</countattribute>");
     }
%>
