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
<%@ page import="jakarta.jms.JMSConsumer" %>
<%@ page import="jakarta.jms.QueueBrowser" %>
<%@ page import="weblogic.transaction.TransactionHelper" %>
<%@ page import="weblogic.transaction.TransactionManager" %>
<%@ page import="jakarta.transaction.UserTransaction" %>

<%
try {
  Context lctx = null;
  Context rctx = null;

  String remoteurl = request.getParameter("remoteurl");
  out.println("Remote URL is");
  out.println(remoteurl);

  String action = request.getParameter("action"); 
  out.println("Transcation action -->");
  out.println(request.getParameter("action"));

  lctx = new InitialContext();
  out.println("(Local) Got Context successfully");
  TransactionHelper tranhelp =TransactionHelper.getTransactionHelper();
  UserTransaction ut = tranhelp.getUserTransaction();

  ConnectionFactory qcf=
       (ConnectionFactory)lctx.lookup("weblogic.jms.XAConnectionFactory");
  out.println("(Local) JMS ConnectionFactory lookup Successful ...");
  JMSContext context = qcf.createContext();
  out.println("(Local) JMS Context Created Successfully ...");
  Destination queue = (Destination)lctx.lookup("jms.admin.Queue");
  out.println("(Local) Destination (jms.admin.adminQueue) lookup Successful");

  ut.begin();

  // Send message to local Destination
  context.createProducer().send(queue, "Message to a Local Destination");
  lctx.close();

  Hashtable env = new Hashtable();
  env.put(Context.INITIAL_CONTEXT_FACTORY,
          "weblogic.jndi.WLInitialContextFactory");
  env.put(Context.PROVIDER_URL, remoteurl);
  // Remote anonymous RMI access via T3 not allowed
  env.put(Context.SECURITY_PRINCIPAL, "weblogic");
  env.put(Context.SECURITY_CREDENTIALS, "welcome1");
  rctx = new InitialContext(env);
  out.println("(Remote) Got Context successfully");

  // lookup JMS XAConnectionFactory
  ConnectionFactory qcf2=
       (ConnectionFactory)rctx.lookup("weblogic.jms.XAConnectionFactory");
  out.println("(Remote) JMS ConnectionFactory lookup Successful ...");

  JMSContext context2 = qcf2.createContext();
  out.println("(Remote) JMS Context Created Successfully ...");
  Destination queue2 = (Destination)rctx.lookup("jms.admin.Queue");
  out.println("(Remote) Destination (jms.admin.adminQueue) lookup Successful");
  context2.createProducer().send(queue2, "Message to a Remote Destination");
  rctx.close();

  out.println(ut);

  // Get the live context from Tx Coordinator before closing transaction 
  // Context ctx = new InitialContext(env);

  if ( action.equals("commit") ) {
    ut.commit();
    out.println("### User Transation is committed");
  } else {
    ut.rollback();
    out.println("### User Transation is rolled-back");
    
  }
  out.println("### Message sent w/o User Transation");
} catch(Exception e) {
   out.println(e);
   out.println("Got an Exception");
}
%>
