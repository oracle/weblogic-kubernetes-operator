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
        <h1>Welcome to WebLogic on Kubernetes Quick Start Sample</font></h1><br>
	<%
		StringBuffer message = new StringBuffer();
		message.append("<h2>WebLogic Server Hosting the Application</h2> ");
		message.append("<b>Server Name:</b> " + InetAddress.getLocalHost().getHostName());
		message.append("<br>");
		message.append("<b>Server time:</b> " + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()));
		message.append("<br>");
		message.append("<p>");
	%>
	<%=message%>
</body>
</html>
