import java.util.Hashtable;
import java.util.Enumeration;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import jakarta.jms.Destination;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.JMSConsumer;
import jakarta.jms.QueueBrowser;

public class JmsClient {

  private String    url;
  private String    user;
  private String    password;
  private String    action;
  private String    qname;

  private Context   ctx;
  private ConnectionFactory qcf = null;
  private Destination queue = null;

  public JmsClient(String[] args)
  {
    url    = args[0];
    user   = args[1];
    qname  = args[2];
    action = args[3];

    String msgString      = null ;

    try {
      ctx = getInitialContext();
      qcf=(ConnectionFactory)ctx.lookup("weblogic.jms.ConnectionFactory");
      log("JMS ConnectionFactory lookup Successful ...");
      JMSContext context = qcf.createContext();
      log("JMS Context Created Successfully ...");
      queue = (Destination)ctx.lookup(qname);
      log("JMS Destination lookup Successful ...");

      if ( action.equals("send") )  {
       context.createProducer().send(queue, "Message to a Destination");
       log("JMS Message sent Successfully ..");
       log("\n");
       ctx.close();
      } else if ( action.equals("recv") ) {
       JMSConsumer consumer = (JMSConsumer) context.createConsumer(queue);
       log("JMS Consumer Created Successfully ..");
       Message msg=null;
       int count = 0;
       do {
        msg = consumer.receiveNoWait();
        if ( msg != null ) {
          // log("Message Drained ["+msg+"]");
          log("Message Drained ["+msg.getBody(String.class)+"]");
          count++;
         }
        } while( msg != null);
        log("Total Message(s) Received : " + count );
        log("\n");
      } else if ( action.equals("browse") ) {
        log("Browsing the destination ");
        QueueBrowser qb = 
           (QueueBrowser)context.createBrowser((jakarta.jms.Queue)queue);
        Enumeration enumeration = null;
        try {
            enumeration = qb.getEnumeration();
        } catch (jakarta.jms.JMSException e) {
            e.printStackTrace();
        }
        int size = 0;
        while(enumeration.hasMoreElements()) {
            enumeration.nextElement();
            size++;
        }
        log("Queue size:["+size+"]");
        log("\n");
      } else {
        log("Not a supported action " + action );
      }
     } catch ( Exception  ex ) { 
       ex.printStackTrace();
       log("Exception while performing JMS operation "+ ex);
       System.exit(-1);
     } 
   } 

   private void log(String err) { System.out.println(err); }

   private Context getInitialContext()
   {
     Context jndiContext = null;
     /**
      Hashtable props = new Hashtable();
      props.put(Context.INITIAL_CONTEXT_FACTORY,
      Environment.DEFAULT_INITIAL_CONTEXT_FACTORY);
       props.put(Context.PROVIDER_URL, url);
      **/
     password="welcome1";
     log("Context URL ---> : " + url);
     // log("User["+user+"] Password["+password+"]");
     String WLS_JNDI_FACTORY  = "weblogic.jndi.WLInitialContextFactory";
     Hashtable env = new Hashtable();
     env.put(Context.INITIAL_CONTEXT_FACTORY, WLS_JNDI_FACTORY);
     env.put(Context.PROVIDER_URL, url);
     env.put(Context.SECURITY_PRINCIPAL, user);
     env.put(Context.SECURITY_CREDENTIALS, password);
     // log("env in getInitialContext(): " + env);
      try {
        jndiContext = new InitialContext(env);
      } catch (Exception e) {
       log("Unable to getInitialContext "+e);
       System.exit(-1);
      }
      return jndiContext;
   }

   public static void main(String[] args){
    JmsClient client = new JmsClient(args);
   }
}
