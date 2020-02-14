// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

class MyTestAuthenticator extends Authenticator {
  public PasswordAuthentication getPasswordAuthentication() {
    String username = BaseTest.getUsername();
    String password = BaseTest.getPassword();
    return (new PasswordAuthentication(username, password.toCharArray()));
  }
}
