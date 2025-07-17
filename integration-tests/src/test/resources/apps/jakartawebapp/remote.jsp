<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Hashtable" %>
<%@ page import="javax.naming.Context" %>
<%@ page import="javax.naming.InitialContext" %>
<%@ page import="javax.naming.NamingException" %>
<%@ page import="jakarta.servlet.ServletException" %>
<%@ page import="jakarta.servlet.annotation.WebServlet" %>
<%@ page import="jakarta.servlet.http.HttpServlet" %>
<%@ page import="jakarta.servlet.http.HttpServletRequest" %>
<%@ page import="jakarta.servlet.http.HttpServletResponse" %>
<%@ page import="jakarta.servlet.http.HttpServletResponse" %>
<%@ page import="jakarta.jms.Destination" %>
<%@ page import="jakarta.jms.ConnectionFactory" %>
<%@ page import="jakarta.jms.JMSContext" %>
<%@ page import="jakarta.jms.Message" %>

<%
try {
  Context ctx = null;

  String remoteurl = request.getParameter("remoteurl");
  out.println("Remote URL is [" + remoteurl + "]");

  String dest = request.getParameter("dest"); 
  out.println("Remote Destination action [" + dest + "]");

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
  out.println("Text Message sent remote destination");
  context.clise()
} catch(Exception e) {
   out.println("Got an Exception [" + e + "]");
}
%>
