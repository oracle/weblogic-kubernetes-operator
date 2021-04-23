// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Base64;

public interface AuthorizationSource {

  byte[] getUserName();

  byte[] getPassword();

  /**
   * Create an HTTP basic authorization header using the credentials.
   * @return Basic authorization header
   */
  default String createBasicAuthorizationString() {
    return "Basic " + createEncodedBasicCredentials(getUserName(), getPassword());
  }

  /**
   * Notification that the credentials, when used, resulted in an authentication or authorization failure.
   */
  default void onFailure() {
    // no-op
  }

  // Create encoded credentials from username and password.
  private static String createEncodedBasicCredentials(final byte[] username, final byte[] password) {
    final byte[] usernameAndPassword = new byte[username.length + password.length + 1];
    System.arraycopy(username, 0, usernameAndPassword, 0, username.length);
    usernameAndPassword[username.length] = (byte) ':';
    System.arraycopy(password, 0, usernameAndPassword, username.length + 1, password.length);
    return Base64.getEncoder().encodeToString(usernameAndPassword);
  }
}
