// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator;


import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import org.junit.Assume;
import org.junit.Test;

import javax.annotation.concurrent.NotThreadSafe;

import static junit.framework.TestCase.fail;

@NotThreadSafe
public class WatchTest {

  @Test
  public void testNamespaceWatch() {
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("nix"));

    ClientHolder client = ClientHelper.getInstance().take();
    try {

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


      for (Watch.Response<V1Namespace> item : watch) {
        System.out.printf("%s : %s%n", item.type, item.object.getMetadata().getName());
      }

    } catch (ApiException e) {
      fail();
    } catch (RuntimeException e) {
      System.out.println("stream finished");
    }

  }


  @Test
  public void testCustomResourceWatch() {
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("nix"));

    ClientHolder client = ClientHelper.getInstance().take();
    try {
      // create a watch

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


      for (Watch.Response<Domain> item : watch) {
        System.out.printf("%s : %s%n", item.type, item.object.getMetadata().getName());
      }

    } catch (ApiException e) {
      if (e.getCode() == 404) {
        System.out.println("***\n***This test is not able to run because the CRD that I want to watch does not exist on the server\n***");
      } else {
        fail();
      }
    } catch (RuntimeException e) {
      System.out.println("stream finished");
    }

  }


}
