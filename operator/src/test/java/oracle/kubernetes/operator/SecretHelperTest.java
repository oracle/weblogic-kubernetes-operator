// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.VersionInfo;
import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.SecretHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class SecretHelperTest {

  private static final String SECRET_NAME = "wls-admin-server-credentials";
  private static final String INVALID_SECRET_NAME = "wls-admin-server-invalid";
  private static final String NOT_EXIST_SECRET_NAME = "wls-admin-server-not-exist";
  private static final String UNIT_NAMESPACE = "unit-test";
  private static final String USERNAME = "weblogic";
  private static final String PASSWORD = "welcome1";
  private SecretHelper defaultSecretHelper;
  private SecretHelper unitSecretHelper;
  private boolean isVersion18 = false;

  @Before
  public void setUp() throws Exception {
    CallBuilderFactory factory = new CallBuilderFactory();
    // Determine if 1.8 since some bugs with kubernetes-client / java and secrets
    VersionInfo verInfo = factory.create().readVersionCode();
    if ("1".equals(verInfo.getMajor()) && "8".equals(verInfo.getMinor())) {
      isVersion18 = true;
    }

    createNamespace(UNIT_NAMESPACE);
    createSecret(SECRET_NAME, UNIT_NAMESPACE);
    createInvalidSecret(INVALID_SECRET_NAME, UNIT_NAMESPACE);
    createSecret(SECRET_NAME, "default");

    defaultSecretHelper = new SecretHelper("default");
    unitSecretHelper = new SecretHelper(UNIT_NAMESPACE);
  }

  @After
  public void tearDown() throws Exception {
    // Delete the secret if we created one.
    deleteSecret(SECRET_NAME, UNIT_NAMESPACE);
    deleteSecret(INVALID_SECRET_NAME, UNIT_NAMESPACE);
  }

  @Test
  public void testDefaultNamespace() throws Exception {

    // The secret name will eventually come from the configuration.
    String secretName = SECRET_NAME;

    Map<String, byte[]> secretData =
        defaultSecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, secretName);

    Assert.assertNotNull(
        "Expected secret data not returned for " + secretName + "in default namespace", secretData);

    byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);

    Assert.assertNotNull("Expected username not found in data ", username);
    Assert.assertNotNull("Expected password not found in data ", password);

    Assert.assertEquals("Password does not match ", PASSWORD, new String(password));
    Assert.assertEquals("Username does not match ", USERNAME, new String(username));

    secretName = NOT_EXIST_SECRET_NAME;
    secretData =
        defaultSecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, secretName);
    Assert.assertNull(
        "Secret data not expected for " + secretName + " in default namespace", secretData);
  }

  @Test
  public void testUnitTestNamespace() throws Exception {

    String secretName = SECRET_NAME;

    // Normal secret

    Map<String, byte[]> secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, secretName);

    Assert.assertNotNull(
        "Expected secret data not returned for " + secretName + " in unit namespace", secretData);

    byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);

    Assert.assertNotNull("Expected username not found in data ", username);
    Assert.assertNotNull("Expected password not found in data ", password);

    Assert.assertEquals("Password does not match ", PASSWORD, new String(password));
    Assert.assertEquals("Username does not match ", USERNAME, new String(username));

    // Invalid secret with no data

    secretName = INVALID_SECRET_NAME;
    secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, secretName);

    Assert.assertNull(
        "Unexpected secret data returned for " + secretName + " in unit namespace", secretData);

    // Non-existent secret

    secretName = NOT_EXIST_SECRET_NAME;
    secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, secretName);
    Assert.assertNull(
        "Secret data not expected for " + secretName + "in unit namespace", secretData);
  }

  // Create a named secret with username / password in specified name
  private V1Secret createSecret(String name, String namespace) throws Exception {

    CallBuilderFactory factory = new CallBuilderFactory();
    try {
      V1Secret existing = factory.create().readSecret(name, namespace);
      if (existing != null) return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    if (isVersion18) return null;

    V1Secret body = new V1Secret();

    // Set the required api version and kind of resource
    body.setApiVersion("v1");
    body.setKind("Secret");

    // Setup the standard object metadata
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace(namespace);
    body.setMetadata(meta);

    Map<String, byte[]> data = new HashMap<String, byte[]>();
    data.put(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME, USERNAME.getBytes());
    data.put(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD, PASSWORD.getBytes());
    body.setData(data);
    try {
      return factory.create().createSecret(namespace, body);
    } catch (Exception e) {
      e.printStackTrace(System.out);
      throw e;
    }
  }

  // Create a named secret with no username / password in specified namespace
  private V1Secret createInvalidSecret(String name, String namespace) throws Exception {

    CallBuilderFactory factory = new CallBuilderFactory();
    try {
      V1Secret existing = factory.create().readSecret(name, namespace);
      if (existing != null) return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    if (isVersion18) return null;

    V1Secret body = new V1Secret();

    // Set the required api version and kind of resource
    body.setApiVersion("v1");
    body.setKind("Secret");

    // Setup the standard object metadata
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace(namespace);
    body.setMetadata(meta);

    return factory.create().createSecret(namespace, body);
  }

  // Delete a named secret from the specified namespace
  private V1Status deleteSecret(String name, String namespace) throws Exception {
    if (isVersion18) return null;

    CallBuilderFactory factory = new CallBuilderFactory();
    return factory.create().deleteSecret(name, namespace, new V1DeleteOptions());
  }

  // Create a named namespace
  private V1Namespace createNamespace(String name) throws Exception {

    CallBuilderFactory factory = new CallBuilderFactory();
    try {
      V1Namespace existing = factory.create().readNamespace(name);
      if (existing != null) return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    V1Namespace body = new V1Namespace();

    // Set the required api version and kind of resource
    body.setApiVersion("v1");
    body.setKind("Namespace");

    // Setup the standard object metadata
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    body.setMetadata(meta);

    return factory.create().createNamespace(body);
  }
}
