<!--
Copyright (c) 2024, Oracle and/or its affiliates.
Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
-->
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
<%@ page import="javax.jms.JMSConsumer" %>
<%@ page import="javax.jms.QueueBrowser" %>

<%
try {
  Context ctx = null;

  String remoteurl = request.getParameter("remoteurl");
  out.println("Remote URL is [" + remoteurl + "]");

  String action = request.getParameter("action"); 
  out.println("action [" + action + "]");

  String dest = request.getParameter("dest"); 
  out.println("Destination  [" + dest + "]");

  Hashtable env = new Hashtable();
  env.put(Context.INITIAL_CONTEXT_FACTORY,
          "weblogic.jndi.WLInitialContextFactory");
  env.put(Context.PROVIDER_URL, remoteurl);
  // Remote anonymous RMI access via T3 not allowed
  env.put(Context.SECURITY_PRINCIPAL, "weblogic");
  env.put(Context.SECURITY_CREDENTIALS, "welcome1");
  ctx = new InitialContext(env);
  out.println("Got Remote Context successfully");

  // lookup JMS XAConnectionFactory
  ConnectionFactory qcf=
       (ConnectionFactory)ctx.lookup("weblogic.jms.XAConnectionFactory");
  out.println("JMS ConnectionFactory lookup Successful ...");

  JMSContext context = qcf.createContext();
  out.println("JMS Context Created Successfully ...");
  Destination queue = (Destination)ctx.lookup(dest);
  out.println("JMS Destination lookup Successful ...");

  if ( action.equals("send") ) {
    context.createProducer().send(queue, "Message to a Destination");
    out.println("Message sent to the JMS Destination");
  }
  
  if ( action.equals("recv") ) {
    JMSConsumer consumer = (JMSConsumer) context.createConsumer(queue);
    out.println("JMS Consumer Created Successfully ..");
    Message msg=null;
    int count = 0;
    do {
      msg = consumer.receiveNoWait();
        if ( msg != null ) {
          // out.println("Message Drained ["+msg+"]");
          // out.println("Message Drained ["+msg.getBody(String.class)+"]");
          count++;
       }
    } while( msg != null);
      out.println("Total Message(s) Received : " + count);
  }

} catch(Exception e) {
   out.println("Got an Exception [" + e + "]");
}
%>
