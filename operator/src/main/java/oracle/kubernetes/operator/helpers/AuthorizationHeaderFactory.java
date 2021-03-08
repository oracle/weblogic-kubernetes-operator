// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Base64;
import javax.annotation.Nonnull;

public class AuthorizationHeaderFactory {
  private final byte[] encodedUsername;
  private final byte[] encodedPassword;

  public AuthorizationHeaderFactory(@Nonnull byte[] userName, @Nonnull byte[] password) {
    encodedUsername = encode(userName);
    encodedPassword = encode(password);
  }

  private byte[] encode(byte[] source) {
    return Base64.getEncoder().encode(source);
  }

  public String createBasicAuthorizationString() {
    return "Basic " + createEncodedBasicCredentials(decode(encodedUsername), decode(encodedPassword));
  }

  private byte[] decode(byte[] source) {
    return Base64.getDecoder().decode(source);
  }

  // Create encoded credentials from username and password.
  private static String createEncodedBasicCredentials(final byte[] username, final byte[] password) {
    final byte[] usernameAndPassword = new byte[username.length + password.length + 1];
    System.arraycopy(username, 0, usernameAndPassword, 0, username.length);
    usernameAndPassword[username.length] = (byte) ':';
    System.arraycopy(password, 0, usernameAndPassword, username.length + 1, password.length);
    return Base64.getEncoder().encodeToString(usernameAndPassword);
  }

  public static class SecretDataMissingException extends RuntimeException {

    SecretDataMissingException() {
    }
  }
}
