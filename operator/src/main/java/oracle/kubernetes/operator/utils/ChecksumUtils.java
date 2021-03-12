// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public class ChecksumUtils {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Gets the MD5 hash of a string.
   *
   * @param data input string
   * @return MD5 hash value of the data, null in case of an exception.
   */
  public static String getMD5Hash(String data) {
    try {
      return bytesToHex(MessageDigest.getInstance("MD5").digest(data.getBytes("UTF-8")));
    } catch (Exception ex) {
      LOGGER.severe(MessageKeys.EXCEPTION, ex);
      return null;
    }
  }

  private static String bytesToHex(byte[] hash) {
    return DatatypeConverter.printHexBinary(hash).toLowerCase();
  }
}