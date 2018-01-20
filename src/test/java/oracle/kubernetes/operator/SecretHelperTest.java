// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.SecretHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;


public class SecretHelperTest {

  private final static String SECRET_NAME = "wls-admin-server-credentials";
  private final static String INVALID_SECRET_NAME = "wls-admin-server-invalid";
  private final static String NOT_EXIST_SECRET_NAME = "wls-admin-server-not-exist";
  private final static String UNIT_NAMESPACE = "unit-test";
  private final static String USERNAME = "weblogic";
  private final static String PASSWORD = "welcome1";
  private final static String PRETTY = "true";
  private SecretHelper defaultSecretHelper;
  private SecretHelper unitSecretHelper;
  private boolean isVersion18 = false;

  @Before
  public void setUp() throws Exception {
    ClientHolder client = ClientHelper.getInstance().take();
    try {
      // Determine if 1.8 since some bugs with kubernetes-client / java and secrets
      VersionInfo verInfo = client.getVersionApiClient().getCode();
      if ("1".equals(verInfo.getMajor()) && "8".equals(verInfo.getMinor())) {
        isVersion18 = true;
      }

      createNamespace(client, UNIT_NAMESPACE);
      createSecret(client, SECRET_NAME, UNIT_NAMESPACE);
      createInvalidSecret(client, INVALID_SECRET_NAME, UNIT_NAMESPACE);
      createSecret(client, SECRET_NAME, "default");

      defaultSecretHelper = new SecretHelper(client, "default");
      unitSecretHelper = new SecretHelper(client, UNIT_NAMESPACE);
    } finally {
      ClientHelper.getInstance().recycle(client);
    }
  }

  @After
  public void tearDown() throws Exception {
    ClientHolder client = ClientHelper.getInstance().take();
    try {
      // Delete the secret if we created one.
      deleteSecret(client, SECRET_NAME, UNIT_NAMESPACE);
      deleteSecret(client, INVALID_SECRET_NAME, UNIT_NAMESPACE);
    } finally {
      ClientHelper.getInstance().recycle(client);
    }
  }

  @Test
  public void testDefaultNamespace() throws Exception {

    // The secret name will eventually come from the configuration.
    String secretName = SECRET_NAME;

    Map<String, byte[]> secretData =
        defaultSecretHelper.getSecretData(SecretHelper.SecretType.AdminServerCredentials, secretName);

    Assert.assertNotNull(
        "Expected secret data not returned for " + secretName + "in default namespace",
        secretData);

    byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);

    Assert.assertNotNull("Expected username not found in data ", username);
    Assert.assertNotNull("Expected password not found in data ", password);

    Assert.assertEquals("Password does not match ", PASSWORD, new String(password));
    Assert.assertEquals("Username does not match ", USERNAME, new String(username));

    secretName = NOT_EXIST_SECRET_NAME;
    secretData =
        defaultSecretHelper.getSecretData(SecretHelper.SecretType.AdminServerCredentials, secretName);
    Assert.assertNull(
        "Secret data not expected for " + secretName + " in default namespace",
        secretData);

  }

  @Test
  public void testUnitTestNamespace() throws Exception {

    String secretName = SECRET_NAME;

    // Normal secret

    Map<String, byte[]> secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminServerCredentials, secretName);

    Assert.assertNotNull(
        "Expected secret data not returned for " + secretName + " in unit namespace",
        secretData);

    byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);

    Assert.assertNotNull("Expected username not found in data ", username);
    Assert.assertNotNull("Expected password not found in data ", password);

    Assert.assertEquals("Password does not match ", PASSWORD, new String(password));
    Assert.assertEquals("Username does not match ", USERNAME, new String(username));

    // Invalid secret with no data

    secretName = INVALID_SECRET_NAME;
    secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminServerCredentials, secretName);

    Assert.assertNull(
        "Unexpected secret data returned for " + secretName + " in unit namespace",
        secretData);


    // Non-existent secret

    secretName = NOT_EXIST_SECRET_NAME;
    secretData =
        unitSecretHelper.getSecretData(SecretHelper.SecretType.AdminServerCredentials, secretName);
    Assert.assertNull(
        "Secret data not expected for " + secretName + "in unit namespace",
        secretData);


  }


  // Create a named secret with username / password in specified name
  private V1Secret createSecret(ClientHolder client, String name, String namespace) throws Exception {

    try {
      V1Secret existing = client.getCoreApiClient().readNamespacedSecret(name, namespace, PRETTY, true, true);
      if (existing != null)
        return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    if (isVersion18)
      return null;

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
      return client.getCoreApiClient().createNamespacedSecret(namespace, body, PRETTY);
    } catch (Exception e) {
      e.printStackTrace(System.out);
      throw e;
    }
  }


  // Create a named secret with no username / password in specified namespace
  private V1Secret createInvalidSecret(ClientHolder client, String name, String namespace) throws Exception {

    try {
      V1Secret existing = client.getCoreApiClient().readNamespacedSecret(name, namespace, PRETTY, true, true);
      if (existing != null)
        return existing;
    } catch (ApiException ignore) {
      // Just ignore and try to create it
    }

    if (isVersion18)
      return null;

    V1Secret body = new V1Secret();

    // Set the required api version and kind of resource
    body.setApiVersion("v1");
    body.setKind("Secret");

    // Setup the standard object metadata
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace(namespace);
    body.setMetadata(meta);

    return client.getCoreApiClient().createNamespacedSecret(namespace, body, PRETTY);
  }


  // Delete a named secret from the specified namespace
  private V1Status deleteSecret(ClientHolder client, String name, String namespace) throws Exception {
    if (isVersion18)
      return null;

    return client.getCoreApiClient().deleteNamespacedSecret(name, namespace, new V1DeleteOptions(), PRETTY,
        30, true, null);
  }

  // Create a named namespace
  private V1Namespace createNamespace(ClientHolder client, String name) throws Exception {

    try {
      V1Namespace existing = client.getCoreApiClient().readNamespace(name, PRETTY, true, true);
      if (existing != null)
        return existing;
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

    return client.getCoreApiClient().createNamespace(body, PRETTY);
  }

}
