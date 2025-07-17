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
<%@ page import="weblogic.transaction.TransactionHelper" %>
<%@ page import="weblogic.transaction.TransactionManager" %>
<%@ page import="javax.transaction.UserTransaction" %>

<%
try {
  Context lctx = null;
  Context rctx = null;
  String remoteurl = request.getParameter("remoteurl");
  out.println("#### Remote URL is ["+remoteurl+"]");
  String action = request.getParameter("action"); 
  out.println("#### Transcation action ["+action+"]");

  lctx = new InitialContext();
  out.println("(Local) Got JNDI Context successfully ["+lctx+"]");
  TransactionHelper tranhelp =TransactionHelper.getTransactionHelper();
  UserTransaction ut = tranhelp.getUserTransaction();

  ConnectionFactory qcf=
       (ConnectionFactory)lctx.lookup("weblogic.jms.XAConnectionFactory");
  out.println("(Local) JMS ConnectionFactory lookup successful");
  JMSContext context = qcf.createContext();
  out.println("(Local) JMS Context created successfully ...");
  Destination queue = (Destination)lctx.lookup("jms.admin.Queue");
  out.println("(Local) JMS Destination (jms.admin.Queue) lookup successful");

  if ( ! action.equals("notx") ) {
    out.println("Started a user transaction");
    ut.begin();
  }

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
  out.println("(Remote) Got JNDI Context successfully ["+rctx+"]");

  ConnectionFactory qcf2=
       (ConnectionFactory)rctx.lookup("jms.test.cluster.ConnectionFactory");
  out.println("(Remote) JMS ConnectionFactory lookup successful");

  JMSContext context2 = qcf2.createContext();
  out.println("(Remote) JMS Context created successfully");
  Destination queue2 = (Destination)rctx.lookup("jms.test.UniformQueue");
  out.println("(Remote) JMS Destination (jms.test.UniformQueue) lookup successful");

  for (int i=0; i<10; i++)
    context2.createProducer().send(queue2, "Message to a Remote Destination");
  out.println("Sent 10 messages to Destination (jms.test.UniformQueue)");
  rctx.close();

  // Get the live context from Tx Coordinator before closing transaction 
  // Context ctx = new InitialContext(env);

  if ( action.equals("commit") ) {
    out.println(ut);
    ut.commit();
    out.println("#### User Transation is committed");
  } else if ( action.equals("rollback")) {
    out.println(ut);
    ut.rollback();
    out.println("#### User Transation is rolled-back");
  } else {
    out.println("#### Message sent w/o Transaction");
  }
} catch(Exception e) {
   out.println("#### Got an Exception [" +e+"]");
}
%>
