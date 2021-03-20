// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.Destination;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.JMSConsumer;

public class JmsSendReceiveClient {

  private String    url;
  private String    action;
  private String    qname;
  private int       mcount;

  public JmsSendReceiveClient(String[] args)
  {
    url      = args[0];
    action   = args[1];
    qname    = args[2];
    mcount   = Integer.parseInt(args[3]);


    try {
     if ( action.equals("send") )  {
        doSend(qname);
        System.out.println("Successfully sent [" + mcount + "] messages");
      } else if ( action.equals("receive") )  {
        int ct = doReceive(qname);
        System.out.println("Successfully received [" + ct + "] messages");
        if ( ct != mcount ) {
         log("ERROR: Expected # of message ["+mcount+"] not found");
         System.exit(-1);
        }
      } else {
        System.out.println("Usage: Unknown message option ["+action+"]");
        System.out.println("Message option must be send|receive");
        System.exit(-1);
      }
     } catch ( Exception  ex ) { 
       ex.printStackTrace();
       System.out.println("Exception while Send/Receive Message:"+ ex);
       System.exit(-1);
     } 
   } 

  public void doSend(String Queue) throws Exception {
   System.out.println("Sending message(s) to [" + Queue + "]");
   Context ctx = getInitialContext();
   Destination queue = (Destination)ctx.lookup(Queue);
   ConnectionFactory qcf= (ConnectionFactory)
        ctx.lookup("weblogic.jms.ConnectionFactory");
   JMSContext context = qcf.createContext();
   for (int i=0; i<mcount; i++)
     context.createProducer().send(queue, "Welcome to Weblogic on K8s");

   context.close();
   ctx.close();

   }

  public int doReceive(String Queue) throws Exception {
   System.out.println("Geting message(s) from  [" + Queue + "]");
   Context ctx = getInitialContext();
   Destination queue = (Destination)ctx.lookup(Queue);
   ConnectionFactory qcf= (ConnectionFactory)
        ctx.lookup("weblogic.jms.ConnectionFactory");
   JMSContext context = qcf.createContext();
   JMSConsumer consumer = (JMSConsumer) context.createConsumer(queue);
   Message msg=null;
   int count = 0;
   do {
     msg = consumer.receiveNoWait();
     if ( msg != null ) { count++; }
    } while( msg != null);
    // System.out.println("DRAINED ["+count+"] message from ["+Queue+"]");
    return count;
   }

   private Context getInitialContext()
   {
     Context jndiContext = null;
     String user="weblogic";
     String password="welcome1";

     System.out.println("JNDI Context URL [" + url +"]");
     // System.out.println("User["+user+"] Password[******]");
     String WLS_JNDI_FACTORY  = "weblogic.jndi.WLInitialContextFactory";

     Hashtable env = new Hashtable();
     env.put(Context.INITIAL_CONTEXT_FACTORY, WLS_JNDI_FACTORY);
     env.put(Context.PROVIDER_URL, url);
     env.put(Context.SECURITY_PRINCIPAL, user);
     env.put(Context.SECURITY_CREDENTIALS, password);
     // System.out.println("env in getInitialContext(): " + env);
      try {
        jndiContext = new InitialContext(env);
        System.out.println("Got initial JNDI Context(): " + jndiContext);
      } catch (Exception e) {
       System.out.println("Unable to getInitialContext "+e);
       System.exit(-1);
      }
      return jndiContext;
   }

   private void log(String err)
   {
     System.out.println(err);
   }

   public static void main(String[] args){
    if ( args.length < 4 ) {
     System.out.println("Usage : JmsSendReceiveClient  url action(send|receive) qname count");
     System.exit(-1);
    }
    JmsSendReceiveClient client = new JmsSendReceiveClient(args);
   }
}
