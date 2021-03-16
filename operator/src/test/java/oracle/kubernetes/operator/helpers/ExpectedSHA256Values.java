// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * To present pods from rolling when a customer upgrades the operator, we must ensure that the computed SHA256 hash
 * will not change. To do this, we capture the hash for some representative cases and test that those cases
 * continue to result in the same values as we make changes.
 *
 * The changes here are taken from 3.1.0.
 */
interface ExpectedSHA256Values {

  String MANAGED_SERVER_PLAINPORT_SHA256 = "6e1c6a716a9e0078a56c2df86ffe1cd2f0f9bdbe271f169dc1d548d026aa52b7";
  String MANAGED_SERVER_SSLPORT_SHA256 = "368b26eaf1645f966d4c350906b59a0afcc0fbdf9ed30779b71a6d044eb480dd";
  String MANAGED_SERVER_MIIDOMAIN_SHA256 = "bdebf4e8152fea393e06f037bbb4ed243a938f2f9e1aeeffe20768724a0b32cc";
  String ADMIN_SERVER_PLAINPORT_SHA256 = "fc735cf7c34bc1492a237149c4e27c9bb27575de463dbc55e953588862098df4";
  String ADMIN_SERVER_SSLPORT_SHA256 = "854c2ce0b646268a7e72f601f625a8466f45d71f7f635a65ad3740ecb9bdf39a";
  String ADMIN_SERVER_MIIDOMAIN_SHA256 = "98ac04bc5ddbc792ee37912ea5efe8097016ed10ce93b13f8e57013547429e6c";
}
