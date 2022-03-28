// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package application;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name="JmsServlet", urlPatterns={"/jmstest"},
     initParams={ @WebInitParam(name="simpleParam", value="paramValue") } )
     
public class JmsServlet extends HttpServlet {

 private static int s1count=0;
 private static int s2count=0;

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
     int  scount  = 10;
     int  rcount  = 20;

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

      if (request.getParameter("scount") != null )
        scount = Integer.parseInt(request.getParameter("scount"));

      if (request.getParameter("rcount") != null )
        scount = Integer.parseInt(request.getParameter("rcount"));

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
       out.println("Sending ("+scount+") message to ["+destination+"]");
       String msg = "["+dateFormat.format(date)+"] Welcome to WebLogic Kubenates Operator";
       for ( int i=0; i<scount; i++)
       context.createProducer().send(d,msg);
       out.println("["+dateFormat.format(date)+"] Sent ("+scount+") message to ["+destination+"]");
      } else if ( action.equals("receive") ) {
       out.println("Receiving message from ["+destination+"]");
       Message msg=null;
       int count = 0;
       JMSConsumer consumer = (JMSConsumer) context.createConsumer(d);
       do { 
         // msg = consumer.receiveNoWait();
         msg = consumer.receive(5000);
         if ( msg != null ) { 
           // out.println("message content ["+msg.getBody(String.class)+"]");
           if (msg.getBody(String.class).contains("managed-server1"))
               s1count++;
           if (msg.getBody(String.class).contains("managed-server2"))
               s2count++;
           count++; 
         }
       } while( msg != null);

      out.println("Recorded ("+s1count+") message from [managed-server1]");
      out.println("Recorded ("+s2count+") message from [managed-server2]");

      // Intermittently, in a single attempt all 20 messages are not 
      // received on accouting Queue. So the logic is modified to make sure
      // the accounting Queue get 20 messages all together with multiple 
      // attempts. Here the s1count, s2count variables have been made static 
      // to keep a record of the message received from managed-server1 and 
      // managed-server2 of domain2 respectively. 
      // Finally it make sure that s1count and s2count are same

      int ccount = s1count + s2count; 
      if ( ccount == rcount ) {
        if ( s1count == s2count ) {
         out.println("Messages are distributed across MDB instances");
        } else { 
         out.println("Messages are NOT distributed across MDB instances");
        }
        // reset the message counter once all messages are received
        s1count=0;
        s2count=0;
      } else {
        out.println("Total messages received so far is ["+ccount+"]");
        out.println("Waiting for more messages to appears on accounting queue");
       }
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
