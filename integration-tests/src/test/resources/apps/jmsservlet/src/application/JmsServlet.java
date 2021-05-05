// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package application;

import java.io.IOException;
import java.io.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import java.util.*;
import java.text.*;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.MessageConsumer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

import javax.transaction.UserTransaction;
import weblogic.transaction.TransactionHelper;

@WebServlet(name="JmsServlet", urlPatterns={"/jmstest"},
     initParams={ @WebInitParam(name="simpleParam", value="paramValue") } )
     
public class JmsServlet extends HttpServlet {

 protected void doGet(HttpServletRequest request,
                     HttpServletResponse response)
     throws ServletException, IOException {
     response.setContentType("text/plain");
     PrintWriter out = response.getWriter();
     String action = "";
     String destination  = "";
     String cfactory  = "weblogic.jms.ConnectionFactory";
     String url  = "t3://localhost:7001";
     String host  = "localhost";
     String port  = "7001";
     int  mcount  = 10;

     DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
     java.util.Date date = new java.util.Date();

     try {

      action  = request.getParameter("action");
      out.println("Action ["+action+"]");

      destination   = request.getParameter("dest");
      out.println("Destination ["+destination+"]");

      if (request.getParameter("host") != null )
        host = request.getParameter("host");

      if (request.getParameter("port") != null )
        port = request.getParameter("port");

      url = "t3://"+host+":"+port;

      // Override if url parameter is provided 
      if (request.getParameter("url") != null )
        url  = request.getParameter("url");

      out.println("Url  ["+url+"]");

      if (request.getParameter("cf") != null )
        cfactory = request.getParameter("cf");

      out.println("ConnectionFactory ["+cfactory+"]");

      if (request.getParameter("mcount") != null )
        mcount = Integer.parseInt(request.getParameter("mcount"));

      Hashtable h = new Hashtable();
      h.put(Context.INITIAL_CONTEXT_FACTORY,
           "weblogic.jndi.WLInitialContextFactory");
      h.put(Context.PROVIDER_URL,url);
      h.put(Context.SECURITY_PRINCIPAL, "weblogic");
      h.put(Context.SECURITY_CREDENTIALS, "welcome1");
      Context cxt = null;
      cxt = new InitialContext(h);
      out.println("Got Initial Context from " + url);
      Destination d = (Destination)cxt.lookup(destination);
      out.println("Destination Lookup Successful ");
      ConnectionFactory qcf= (ConnectionFactory)cxt.lookup(cfactory);
      out.println("ConnectionFactory Lookup Successful");
      
      JMSContext context = qcf.createContext();

      if ( action.equals("send") ) {
       out.println("Sending ("+mcount+") message to ["+destination+"]");
       String msg = "["+dateFormat.format(date)+"] Welcome to WebLogic Kubenates Operator";
       for ( int i=0; i<mcount; i++)
       context.createProducer().send(d,msg);
       out.println("["+dateFormat.format(date)+"] Sent ("+mcount+") message to ["+destination+"]");
      } else if ( action.equals("receive") ) {
       out.println("Receiving message from ["+destination+"]");
       Message msg=null;
       int count = 0;
       JMSConsumer consumer = (JMSConsumer) context.createConsumer(d);
       do { 
         msg = consumer.receiveNoWait();
         // out.println("Receiving message ["+msg.getBody()+"]");
         if ( msg != null ) { count++; }
       } while( msg != null);
      out.println("["+dateFormat.format(date)+"] Drained ("+count+") message from ["+destination+"]");
     } 

     } catch (Exception e) {
        out.println("Send/Receive FAILED with Unknown Exception " + e);
        e.printStackTrace();
        if (e instanceof JMSException)
            if (((JMSException)e).getLinkedException() != null)
             ((JMSException)e).getLinkedException().printStackTrace();
     } finally {
       out.close();
     }

    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
    throws ServletException, IOException {
    doGet(request,response);
    }
}
