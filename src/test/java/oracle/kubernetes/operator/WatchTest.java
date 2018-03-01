// Copyright 2017,2018, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator;


import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.helpers.CRDHelper;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.builders.WatchBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

@NotThreadSafe
public class WatchTest {

  private static final Logger logger = LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
  private List<Handler> savedhandlers;

  @Before
  public void disableConsoleLogging() {
    savedhandlers = TestUtils.removeConsoleHandlers(logger);
  }

  @After
  public void restoreConsoleLogging() {
    TestUtils.restoreConsoleHandlers(logger, savedhandlers);
  }

  @Test
  public void testNamespaceWatch() throws Exception {
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());

    ClientHolder client = ClientHelper.getInstance().take();

    @SuppressWarnings("unused")
    Watch<V1Namespace> watch = new WatchBuilder(client).createNamespaceWatch();
  }


  @Test
  public void testCustomResourceWatch() throws Exception {
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());

    ClientHolder client = ClientHelper.getInstance().take();
    CRDHelper.checkAndCreateCustomResourceDefinition(client);

    @SuppressWarnings("unused")
    Watch<Domain> watch = new WatchBuilder(client).createDomainsInAllNamespacesWatch();
  }


}
