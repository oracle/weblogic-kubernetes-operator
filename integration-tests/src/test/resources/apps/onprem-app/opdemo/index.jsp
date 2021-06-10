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
        <h1>WebLogic on prem to wko App </font></h1><br>
	<%
		String jdbcDataSourceName = request.getParameter("dsname");
		StringBuffer message = new StringBuffer();
		message.append("<b>Server time:</b> " + new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()));
		message.append("<br>");
		message.append("<b>Hostname:</b> " + InetAddress.getLocalHost().getHostName());
		message.append("<br>");
		message.append("<h2>Datasource properties</h2> ");
		message.append("<p>");
		if (jdbcDataSourceName == null || jdbcDataSourceName.length() == 0) {
			message.append("<font color=\"red\">No datasource name provided.</font><br>");
			message.append("<font color=\"red\">Append ?dsname=YOUR_DATA_SOURCE_NAME to the URL.</font>");
			message.append("</p>");
		} else {
			message.append("<b>Datasource name:</b> " + jdbcDataSourceName);
			message.append("<br>");
			try {
				InitialContext ctx = new InitialContext();
				MBeanServer connection = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");

				ObjectName jdbcSystemResource = new ObjectName(
						"com.bea:Name=" + jdbcDataSourceName + ",Type=JDBCSystemResource");
				ObjectName jdbcDataSourceBean = (ObjectName) connection.getAttribute(jdbcSystemResource,
						"JDBCResource");
				ObjectName jdbcDriverParams = (ObjectName) connection.getAttribute(jdbcDataSourceBean,
						"JDBCDriverParams");

				String URL = (String) connection.getAttribute(jdbcDriverParams, "Url");

				System.out.println("DB URL = " + URL);

				ObjectName dsProperties = (ObjectName) connection.getAttribute(jdbcDriverParams, "Properties");

				ObjectName[] jdbcPropertyBeans = (ObjectName[]) connection.getAttribute(dsProperties, "Properties");
				for (int j = 0; j < jdbcPropertyBeans.length; j++) {
					ObjectName jdbcPropertyBean = null;
					jdbcPropertyBean = jdbcPropertyBeans[j];
					String jdbcPropertyName = (String) connection.getAttribute(jdbcPropertyBean, "Name");
					String jdbcPropertyValue = (String) connection.getAttribute(jdbcPropertyBean, "Value");
					if (jdbcPropertyName.equals("user")) {
						message.append("<b>Database User:</b> " + jdbcPropertyValue);
						message.append("<br>");
					}
				}
				message.append("<b>Database URL:</b> " + URL);
				message.append("</p>");
			} catch (Exception e) {
				e.printStackTrace();
				message.append("<b>Error:</b> " + e.getClass().getName() + " - " + e.getLocalizedMessage());
			}
		}
	%>
	<%=message%>
</body>
</html>
