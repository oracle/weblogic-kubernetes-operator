// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.ContainerResolver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.*;

public class HealthCheckHelperTest {

  private HealthCheckHelper defaultHealthCheckHelper;
  private HealthCheckHelper unitHealthCheckHelper;

  private final static String UNIT_NAMESPACE = "unit-test-namespace";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static ByteArrayOutputStream bos = new ByteArrayOutputStream();
  private static Handler hdlr = new StreamHandler(bos, new SimpleFormatter());
  private List<Handler> savedHandlers = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    savedHandlers = TestUtils.removeConsoleHandlers(LOGGER.getUnderlyingLogger());
    LOGGER.getUnderlyingLogger().addHandler(hdlr);

    if (!TestUtils.isKubernetesAvailable()) return;

    createNamespace(UNIT_NAMESPACE);

    defaultHealthCheckHelper = new HealthCheckHelper("default", Collections.singleton("default"));
    unitHealthCheckHelper = new HealthCheckHelper(UNIT_NAMESPACE, Collections.singleton(UNIT_NAMESPACE));
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.restoreConsoleHandlers(LOGGER.getUnderlyingLogger(), savedHandlers);
    LOGGER.getUnderlyingLogger().removeHandler(hdlr);

    // Delete anything we created
  }

  @Test
  @Ignore
  public void testDefaultNamespace() throws Exception {
    ContainerResolver.getInstance().getContainer().getComponents().put(
        ProcessingConstants.MAIN_COMPONENT_NAME,
        Component.createFor(new CallBuilderFactory(null)));
    
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
    ContainerResolver.getInstance().getContainer().getComponents().put(
        ProcessingConstants.MAIN_COMPONENT_NAME,
        Component.createFor(new CallBuilderFactory(null)));
    
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
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());
    
    ContainerResolver.getInstance().getContainer().getComponents().put(
        ProcessingConstants.MAIN_COMPONENT_NAME,
        Component.createFor(new CallBuilderFactory(null)));
    
    unitHealthCheckHelper.performSecurityChecks("unit-test-svc-account-no-privs");
    hdlr.flush();
    String logOutput = bos.toString();

    Assert.assertTrue("Log output did not contain Access Denied error",
        logOutput.contains("Access denied for service account"));
    bos.reset();
  }

  // Create a named namespace
  private V1Namespace createNamespace(String name) throws Exception {
    CallBuilderFactory factory = new CallBuilderFactory(null);
    try {
      V1Namespace existing = factory.create().readNamespace(name);
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

    return factory.create().createNamespace(body);
  }
}
