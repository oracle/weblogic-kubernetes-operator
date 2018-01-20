// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class HealthCheckHelperTest {

  private HealthCheckHelper defaultHealthCheckHelper;
  private HealthCheckHelper unitHealthCheckHelper;

  private final static String UNIT_NAMESPACE = "unit-test-namespace";

  private final static String PRETTY = "true";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static ByteArrayOutputStream bos = new ByteArrayOutputStream();
  private static Handler hdlr = new StreamHandler(bos, new SimpleFormatter());

  @Before
  public void setUp() throws Exception {

    ClientHolder client = ClientHelper.getInstance().take();
    try {
      createNamespace(client, UNIT_NAMESPACE);

      defaultHealthCheckHelper = new HealthCheckHelper(client, "default", Collections.singleton("default"));
      unitHealthCheckHelper = new HealthCheckHelper(client, UNIT_NAMESPACE, Collections.singleton(UNIT_NAMESPACE));

      LOGGER.getUnderlyingLogger().addHandler(hdlr);
    } finally {
      ClientHelper.getInstance().recycle(client);
    }
  }

  @After
  public void tearDown() throws Exception {

    LOGGER.getUnderlyingLogger().removeHandler(hdlr);

    // Delete anything we created
  }

  @Test
  public void testDefaultNamespace() throws Exception {

    defaultHealthCheckHelper.performSecurityChecks("default");
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertTrue("Log output did not contain namespace warning",
        logOutput.contains("A namespace has not been created for the"));
    Assert.assertTrue("Log output did not contain account warning",
        logOutput.contains("A service account has not been created"));
    Assert.assertFalse("Log output contained Access Denied error",
        logOutput.contains("Access denied for service account"));
    bos.reset();
  }

  @Ignore
  // TODO work out why this test is failing - it is a rbac issue, need to trace where it is coming from
  public void testUnitTestNamespace() throws Exception {
    unitHealthCheckHelper.performSecurityChecks("weblogic-operator-account");
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertFalse("Log output did contain namespace warning",
        logOutput.contains("A namespace has not been created for the"));
    Assert.assertFalse("Log output did contain account warning",
        logOutput.contains("A service account has not been created"));
    Assert.assertFalse("Log output contained Access Denied error",
        logOutput.contains("Access denied for service account"));
    bos.reset();
  }

  @Test
  public void testAccountNoPrivs() throws Exception {
    unitHealthCheckHelper.performSecurityChecks("unit-test-svc-account-no-privs");
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertTrue("Log output did not contain Access Denied error",
        logOutput.contains("Access denied for service account"));
    bos.reset();
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
