<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Hashtable" %>
<%@ page import="javax.naming.Context" %>
<%@ page import="javax.naming.InitialContext" %>
<%@ page import="javax.naming.NamingException" %>
<%@ page import="javax.servlet.ServletException" %>
<%@ page import="javax.servlet.annotation.WebServlet" %>
<%@ page import="javax.servlet.http.HttpServlet" %>
<%@ page import="javax.servlet.http.HttpServletRequest" %>
<%@ page import="javax.servlet.http.HttpServletResponse" %>
<%@ page import="javax.servlet.http.HttpServletResponse" %>
<%@ page import="javax.jms.Destination" %>
<%@ page import="javax.jms.ConnectionFactory" %>
<%@ page import="javax.jms.JMSContext" %>
<%@ page import="javax.jms.Message" %>

<%
try {
  Context ctx = null;

  String remoteurl = request.getParameter("remoteurl");
  out.println("Remote URL is [" + remoteurl + "]");

  String dest = request.getParameter("dest"); 
  out.println("Remote Destination [" + dest + "]");

  Hashtable env = new Hashtable();
  env.put(Context.INITIAL_CONTEXT_FACTORY,
          "weblogic.jndi.WLInitialContextFactory");
  env.put(Context.PROVIDER_URL, remoteurl);
  // Remote anonymous RMI access via T3 not allowed
  env.put(Context.SECURITY_PRINCIPAL, "weblogic");
  env.put(Context.SECURITY_CREDENTIALS, "welcome1");
  ctx = new InitialContext(env);
  out.println("Got Remote Context successfully [" +ctx+ "]" );
  ConnectionFactory qcf=
       (ConnectionFactory)ctx.lookup("weblogic.jms.ConnectionFactory");
  out.println("JMS ConnectionFactory lookup Successful");
  JMSContext context = qcf.createContext();
  out.println("JMS Context Created Successfully");
  Destination queue = (Destination)ctx.lookup(dest);
  out.println("JMS Destination lookup Successful");
  context.createProducer().send(queue, "Message to a Destination");
  out.println("Sent a Text message to Remote Destination");
} catch(Exception e) {
   out.println("Got an Exception [" + e + "]");
}
%>
