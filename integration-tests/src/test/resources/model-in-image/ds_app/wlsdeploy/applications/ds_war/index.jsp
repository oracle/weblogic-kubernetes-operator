<%-- Copyright (c) 2020, Oracle Corporation and/or its affiliates.
     Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl. --%>
<%
     out.println("Hello World, you have reached server: " + System.getProperty("weblogic.Name" ));
%>
<%@page import="java.sql.*, java.io.*, javax.sql.DataSource, javax.naming.*"%>
<%!
     String dsName = "jdbc/generic1";
     Connection conn = null;
%>
<%
     try {
          String driver = "";
          String db = "";

          if (!dsName.equals("")) {
               InitialContext ctx = new InitialContext();
               DataSource dataSource = (DataSource) ctx.lookup (dsName);
               conn = dataSource.getConnection();
               DatabaseMetaData dmd = conn.getMetaData();
               db = dmd.getDatabaseProductName() + " " + dmd.getDatabaseProductVersion();
               driver = dmd.getDriverName() + " " + dmd.getDriverVersion();
%>
Successfully looked up and got a connection to <%=dsName%><br>
Database: <%=db%> <br>
Driver version: <%=driver%><br>
<%
          }
     } catch (Exception e) {
          PrintWriter pw = new PrintWriter (out);
%>
There was an exeption getting a connection to or looking up the datasource:<%=dsName%><br>
<%
          e.printStackTrace (pw);
%>
<%
     } finally {
          if (conn != null ) {
               try {
                    conn.close();
               } catch (Exception ex) { }
          }
     }
%>