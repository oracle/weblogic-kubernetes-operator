// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.Destination;
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.DeliveryMode;
import javax.jms.TextMessage;
import javax.jms.MessageProducer;
import javax.jms.MessageConsumer;
import javax.jms.JMSException;

import javax.jms.JMSContext;
import javax.jms.JMSConsumer;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;

import weblogic.jms.client.WLConnectionImpl;
import weblogic.jms.extensions.WLConnection;

/**
 * This JMS client that sends 300 messages to a Uniform Distributed Queue 
 * using load balancer http(s) url which maps to custom channel on cluster 
 * member server on WebLogic cluster.
 * It also verifies that the messages are load balanced across all members.
 * The test returns 
 *    success(0)  if it finds equal number of messages on each member 
 *    failure(-1) if it fails to find equal number of messages on each member 
 * Usage java JmsTestClient http(s)://host:port server-count
 */

public class JmsTestClient {

  public  String username ="weblogic";
  public  String password ="welcome1";

  public  String clusterurl ="t3://localhost:7001";
  public  String testQueue ="jms/DistributedQueue";
  public  String testcf ="jms.ClusterConnectionFactory";
  public  int membercount = 2;

  public JmsTestClient(String[] args)
  {
    clusterurl = args[0];
    membercount = Integer.parseInt(args[1]);
    int msgcount = 300;
    boolean loadbalance=true;
    int mc = 0;

    try {

     Context ctx = null;
     ConnectionFactory qcf= null;

     for ( int i=0; i<10; i++ ) {
       ctx = getInitialContext(clusterurl);
       qcf= (ConnectionFactory)ctx.lookup(testcf);
       javax.jms.Connection con = qcf.createConnection();
       String server = ((WLConnectionImpl) con).getWLSServerName();
       System.out.println("Returned WebLogic Server --> " + server);
       con.close();
       qcf=null;
       ctx.close();
     }

     ctx = getInitialContext(clusterurl);
     qcf= (ConnectionFactory)ctx.lookup(testcf);
     Destination queue = (Destination)ctx.lookup(testQueue);

     System.out.println("JNDI Cluster Context URL --> " + clusterurl);

     JMSContext context = qcf.createContext();
     for (int i=0;i<msgcount;i++)
        context.createProducer().send(queue, "Welcome to Weblogic on K8s");
     ctx.close();

     for ( int i=1; i<= membercount ; i++ ) {
       String member_jndi = "ClusterJmsServer@managed-server" + i + 
                      "@jms.DistributedQueue";
       System.out.println("Member JNDI [" + member_jndi + "]");
       mc = cleanQueue(clusterurl, member_jndi );
       System.out.println("Member@managed-server" +i+ " got ["+mc+"] messages");
       if ( mc != msgcount/membercount ) loadbalance = false;
     }

     if ( ! loadbalance ) {
        System.out.println("ERROR: The messages are not evenly distributed");
        System.exit(-1);
     } else {
        System.out.println("SUCCESS: The messages are evenly distributed");
        System.exit(0);
     }
        
     } catch (Exception ex ) {
       System.out.println("Unknown Exception " + ex );
       System.exit(-1);
     }
   } 
  
  public int cleanQueue(String url, String Queue) throws Exception {
   // System.out.println("(CQ) Context URL ["+url+"]");
   // System.out.println("(CQ) Cleaning the Queue ["+Queue+"]");
   Context ctx = getInitialContext(url);
   Destination queue = (Destination)ctx.lookup(Queue);
   ConnectionFactory qcf= (ConnectionFactory)
        ctx.lookup("weblogic.jms.ConnectionFactory");
   JMSContext context = qcf.createContext();
   // System.out.println("(CQ) JMS Context Created ..");
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

   private Context getInitialContext(String url )
   {
     Context jndiContext = null;
     String user="weblogic";
     String password="welcome1";
     String WLS_JNDI_FACTORY  = "weblogic.jndi.WLInitialContextFactory";

     Hashtable env = new Hashtable();
     env.put(Context.INITIAL_CONTEXT_FACTORY, WLS_JNDI_FACTORY);
     env.put(Context.PROVIDER_URL, url);
     env.put(Context.SECURITY_PRINCIPAL, user);
     env.put(Context.SECURITY_CREDENTIALS, password);
     // System.out.println("env in getInitialContext(): " + env);
     // System.out.println("JNDI Context URL --> " + url);
      try {
        jndiContext = new InitialContext(env);
        // System.out.println("GOT INITIALCONTEXT(): " + jndiContext);
      } catch (Exception e) {
       System.out.println("Unable to getInitialContext "+e);
       System.exit(-1);
      }
      return jndiContext;
   }

   public static void main(String[] args){
    JmsTestClient client = new JmsTestClient(args);
   }
}
