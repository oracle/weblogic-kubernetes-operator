// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator;


import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.concurrent.NotThreadSafe;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static junit.framework.TestCase.fail;

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

      Watch<V1Namespace> watch = Watch.createWatch(
          client.getApiClient(),
          client.getCoreApiClient().listNamespaceCall(null,
              null,
              null,
              null,
              null,
              5,
              null,
              60,
              Boolean.TRUE,
              null,
              null),
          new TypeToken<Watch.Response<V1Namespace>>() {
          }.getType());

  }


  @Test
  public void testCustomResourceWatch() throws Exception {
    Assume.assumeTrue(TestUtils.isKubernetesAvailable());

    ClientHolder client = ClientHelper.getInstance().take();
      Watch<Domain> watch = Watch.createWatch(
          client.getApiClient(),
          client.getWeblogicApiClient().listWebLogicOracleV1DomainForAllNamespacesCall(null,
              null,
              null,
              null,
              5,
              null,
              null,
              60,
              Boolean.TRUE,
              null,
              null),
          new TypeToken<Watch.Response<Domain>>() {
          }.getType());

  }


}
