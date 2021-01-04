// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * Types of secrets which can be configured on a domain.
 */
public enum SecretType {
  WebLogicCredentials, 
  ImagePull, 
  ConfigOverride, 
  RuntimeEncryption,
  OpssWalletPassword, 
  OpssWalletFile
}
