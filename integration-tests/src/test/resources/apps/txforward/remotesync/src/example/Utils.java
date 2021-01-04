// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package example;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class Utils {

  public static Context getContext(String url) throws NamingException {
    Hashtable env = new Hashtable();
    env.put(Context.INITIAL_CONTEXT_FACTORY, 
        "weblogic.jndi.WLInitialContextFactory");
    env.put(Context.PROVIDER_URL, url); 
    return new InitialContext(env);
  }

  public static String getLocalServerID() {
    return "[" + getDomainName() + "+"
        + System.getProperty("weblogic.Name") + "]";
  }
  
  private static String getDomainName() {
    String domainName = System.getProperty("weblogic.Domain");
    if (domainName == null) domainName = System.getenv("DOMAIN_NAME");
    return domainName;
  }
}
