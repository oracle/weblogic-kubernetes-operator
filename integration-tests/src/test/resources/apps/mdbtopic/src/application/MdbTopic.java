// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package application;

import java.text.SimpleDateFormat;
import java.text.DateFormat;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.Destination;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.JMSConsumer;
import javax.jms.JMSProducer;
import javax.jms.MessageListener;

import javax.ejb.MessageDriven;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import weblogic.javaee.MessageDestinationConfiguration;
import weblogic.javaee.TransactionIsolation;
import weblogic.javaee.TransactionTimeoutSeconds;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.DomainRuntimeMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

import javax.management.MBeanServer;
import javax.management.ObjectName;

@MessageDriven(
  activationConfig = {
   @ActivationConfigProperty(propertyName="destinationType",
     propertyValue="javax.jms.Topic"),
   @ActivationConfigProperty(propertyName="topicMessagesDistributionMode",
     propertyValue="One-Copy-Per-Server")
  }, 
  mappedName = "jms/testCdtUniformTopic"
) 

@MessageDestinationConfiguration( 
  connectionFactoryJNDIName="weblogic.jms.XAConnectionFactory",
  providerURL="t3://domain2-cluster-cluster-1.domain2-namespace:8001")

@TransactionAttribute(value = TransactionAttributeType.REQUIRED)

 public class MdbTopic implements MessageListener {
 
 public void onMessage (Message msg) {
  try {
  
    MBeanServer localMBeanServer;
    ServerRuntimeMBean serverRuntime;
    RuntimeServiceMBean runtimeService;
    MBeanServer domainMBeanServer;
    DomainRuntimeServiceMBean domainRuntimeServiceMbean;
    DomainRuntimeMBean domainRuntime;

    localMBeanServer = (MBeanServer)
       (new InitialContext()).lookup("java:comp/env/jmx/runtime");

    ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
   runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(localMBeanServer, runtimeserviceObjectName);
   serverRuntime = runtimeService.getServerRuntime();
   System.out.println("<TMDB> MDB is activated on Server " + serverRuntime.getName());

   DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
   java.util.Date date = new java.util.Date();
   TextMessage tmsg = (TextMessage) msg;
   System.out.println("["+dateFormat.format(date)+"]<TMDB> Got message :" + tmsg.getText() );
   Hashtable h = new Hashtable();
   h.put(Context.INITIAL_CONTEXT_FACTORY,
         "weblogic.jndi.WLInitialContextFactory");
   h.put(Context.PROVIDER_URL,"t3://domain1-admin-server:7001");
   h.put(Context.SECURITY_PRINCIPAL, "weblogic");
   h.put(Context.SECURITY_CREDENTIALS, "welcome1");
   Context cxt = new InitialContext(h);
    
    ConnectionFactory 
       cf=(ConnectionFactory)cxt.lookup("weblogic.jms.ConnectionFactory");
    Destination rq=(Destination)cxt.lookup("jms.testAccountingQueue");
    JMSContext context = cf.createContext();
    tmsg = context.createTextMessage("(On Server) " + serverRuntime.getName());
    context.createProducer().send(rq,tmsg);
    System.out.println("["+dateFormat.format(date)+"]<TMDB> message added to [@FWD_DEST_URL@]");
   } catch (Exception e) {
      e.printStackTrace ();
    }
  }
 
 }
