<%--
Copyright (c) 2020, 2021, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
--%>
<%@ page import="java.net.InetAddress" %>
<%@ page import="javax.servlet.http.Cookie" %>
<html>
<title>OpenSessionApp</title>
<body>
<h1>Welcome to Open Session Application !</h1>

<hr>
<%
  out.println("<p><h4> Server Name: " + System.getProperty("weblogic.Name") + "<br></p></h4>");
%>
</body>
</html>



