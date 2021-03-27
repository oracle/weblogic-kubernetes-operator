// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.jms.ConnectionFactory;

public class SslTestClient {
  private String    url;

  public SslTestClient(String[] args)
  {
    url = args[0];
    try {
       Context ctx = getInitialContext();
       System.out.println("Got the Initial JNDI Context ["+ ctx +"]");
       String cfName="weblogic.jms.ConnectionFactory";
       ConnectionFactory qcf=(ConnectionFactory)ctx.lookup(cfName);
       System.out.println("Looked up JMS connection factory ["+ qcf +"]");
     } catch ( Exception  ex ) { 
       System.out.println("Got Unknown Exception ["+ ex + "]");
       ex.printStackTrace();
       System.exit(-1);
     } 
   } 

   private Context getInitialContext()
   {
      Context jndiContext = null;
      System.out.println("Lookup URL [" + url + "]");
      Hashtable props = new Hashtable();
      props.put(Context.PROVIDER_URL, url);

      props.put("java.naming.factory.initial",
          "weblogic.jndi.WLInitialContextFactory" );
      props.put("java.naming.security.principal","weblogic");
      props.put("java.naming.security.credentials","welcome1");
      try {
        jndiContext = new InitialContext(props);
      } catch (Exception e) {
       System.out.println("Unable to get Initial JNDI Context " + e);
       System.exit(-1);
      }
      return jndiContext;
   }

   public static void main(String[] args){
    if ( args.length < 1 ) {
     System.out.println("Usage : SslTestClient [t3s]url ");
     System.exit(-1);
    }
    SslTestClient client = new SslTestClient(args);
   }
}
