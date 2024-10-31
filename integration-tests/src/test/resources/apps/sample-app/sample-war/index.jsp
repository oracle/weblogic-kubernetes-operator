<%--
Copyright (c) 2020, 2021, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
--%>
<%
     out.println("Hello World, you have reached server " + System.getProperty("weblogic.Name" ));
     // Check if the "terminateSession" parameter is set to "true"
     String terminateSession = request.getParameter("terminateSession");
     if ("true".equalsIgnoreCase(terminateSession) && session != null) {
          // Print session info
         out.println("Session ID: " + session.getId() + "<br/>");
         out.println("Creation Time: " + new java.util.Date(session.getCreationTime()) + "<br/>");
         out.println("Last Accessed Time: " + new java.util.Date(session.getLastAccessedTime()) + "<br/>");     
         session.invalidate();
         out.println("Session has been terminated.");
     } else {
         out.println("No active session.");
     }
%>
