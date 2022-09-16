<%-- Copyright (c) 2022, Oracle and/or its affiliates. --%>
<%-- Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>
<%@page import="java.util.Calendar"
import="java.text.SimpleDateFormat"
import="javax.management.MBeanServer"
import="javax.management.ObjectName"
import="javax.naming.InitialContext"
import="javax.servlet.ServletException"
import="javax.servlet.http.HttpServlet"
import="javax.servlet.http.HttpServletRequest"
import="javax.servlet.http.HttpServletResponse"
import="java.net.InetAddress"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<body>
        <h1>Welcome to the WebLogic on Kubernetes Quick Start Sample</font></h1><br>
	<%
		StringBuffer message = new StringBuffer();
		message.append("<b>WebLogic Server Name:</b> " + System.getProperty("weblogic.Name"));
		message.append("<br>");
		message.append("<b>Pod Name:</b> " + InetAddress.getLocalHost().getHostName());
		message.append("<br>");
		message.append("<b>Current time:</b> " + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()));
		message.append("<br>");
		message.append("<p>");
	%>
	<%=message%>
</body>
</html>
