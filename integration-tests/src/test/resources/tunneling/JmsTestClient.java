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
 * using WebLogic cluster url.
 *
 * When this standalone client is executed outside of k8s cluster, 
 *  the cluster url refers to load balancer http(s) url which maps to 
 *  custom channel on cluster
 *
 * When this standalone client is executed inside k8s cluster(inside admin pod) 
 *  the cluster url refers to cluster service url e.g.
 *  t3://mii-tunneling-cluster-cluster-1:8001
 *
 * The test also verifies
 * (a) JMS Connection are load balanced across all servers
 * (b) messages are load balanced across all members.
 * The test returns 
 *    SUCCESS(0)  if all verification criteria are met 
 *    FAILURE(-1) if any of the verification criteria is not met   
 * Usage java JmsTestClient http(s)://host:port membercount true|false
 * Here the 3rd boolean argument is to check JMS connection loadbalancing
 * Note: The connection loadbalancing can be only verified inside k8s cluster
 * When using RMI tunneling, mutiple layer of loadbalancing does not gurantee
 * WebLogic JMS load balancing.
 */

public class JmsTestClient {

  public  String clusterurl ="t3://localhost:7001";
  public  String testQueue ="jms/DistributedQueue";
  public  String testcf ="jms.ClusterConnectionFactory";
  public  int membercount = 2;
  public  int msgcount = 300;
  public  String[] servers = new String[10];
  public  boolean checkConnection = false;

  public JmsTestClient(String[] args)
  {
    clusterurl = args[0];

    if ( args.length >= 2 ) 
      membercount = Integer.parseInt(args[1]);
    else 
      System.out.println("(INFO) Assuming 2 member distributed destination");

    if ( args.length == 3 ) 
      checkConnection = Boolean.parseBoolean(args[2]);
    else 
     System.out.println("(INFO) Assuming no JMS Connection loadbalancing check");
    boolean loadbalance=true;
    int mc = 0;

    System.out.println("WebLogic Cluster Context URL --> " + clusterurl);
    try {
     Context ctx = null;
     ConnectionFactory qcf= null;
     ctx = getInitialContext(clusterurl);
     qcf = (ConnectionFactory) ctx.lookup(testcf);
     for ( int i=0; i<10; i++ ) {
       javax.jms.Connection con = qcf.createConnection();
       String server = ((WLConnectionImpl) con).getWLSServerName();
       servers[i]=server; 
       // con.close();
     }
     ctx.close();

     for ( int i=1; i<= membercount ; i++ ) {
       String server = "managed-server"+i;
       boolean found = checkServer(servers, server);
       if ( ! found ) {
         System.out.println("Found no JMS connection from server " + server);
         if ( checkConnection ) {
          System.exit(-1);
         } else {
          System.out.println("Skipping JMS Connection loadbalancing check");
         }
       } else {
        System.out.println("Found JMS connection from server " + server);
       }
     }

     ctx = getInitialContext(clusterurl);
     System.out.println("Got anonymous JNDI Context");
     qcf= (ConnectionFactory)ctx.lookup(testcf);
     Destination queue = (Destination)ctx.lookup(testQueue);

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
        System.out.println("FAILURE: The messages are not evenly distributed");
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
  
  public boolean checkServer(String servers[], String server) {
     boolean found = false;
     for (String strTemp : servers){
       if ( strTemp.equals(server) ) {
         found = true;
         break;
       }
     }
     return found;
  }

  public int cleanQueue(String url, String Queue) throws Exception {
   Context ctx = getInitialContext(url);
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

   private Context getInitialContext(String url )
   {
     Context jndiContext = null;
     String user="weblogic";
     String password="welcome1";
     String WLS_JNDI_FACTORY  = "weblogic.jndi.WLInitialContextFactory";
     // System.out.println("Using user ["+user+"] pwd ["+password+"]");

     Hashtable env = new Hashtable();
     env.put(Context.INITIAL_CONTEXT_FACTORY, WLS_JNDI_FACTORY);
     env.put(Context.PROVIDER_URL, url);
     // env.put(Context.SECURITY_PRINCIPAL, user);
     // env.put(Context.SECURITY_CREDENTIALS, password);
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
