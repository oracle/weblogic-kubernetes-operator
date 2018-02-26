// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.custom;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.watcher.Watching;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Test CustomResourceDefinitions and custom objects
 */
public class CustomObjectApisTest {

  // Parameters for custom resources
  static final String NAMESPACE = "default";
  static final String CRDNAME = "TestDomain";
  static final String SINGULAR = "testdomain";
  static final String PLURAL = "testdomains";
  static final String GROUP = "weblogic.oracle";
  static final String VERSION = "v1";
  static final String SHORTNAME = "testdom";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private AtomicBoolean finished = new AtomicBoolean(false);
  private int timeoutLoop = 0;
  private List<Handler> savedHandlers;

  @Before
  public void setUp() throws Exception {
    savedHandlers = TestUtils.removeConsoleHandlers(LOGGER.getUnderlyingLogger());
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.restoreConsoleHandlers(LOGGER.getUnderlyingLogger(), savedHandlers);
  }

  @Test
  public void testCustomResourceWatches() throws Exception {

    ClientHolder client = ClientHelper.getInstance().take();
    try {
      // Create the custom resource definition
      createWeblogicCRD(client);

      // Watch for some custom respource
      LOGGER.info("Watching for custom resource events");
      Watcher watcher = watchForDomains();
      watcher.start();
      waitForFinished(client, watcher);
      watcher.closeAndDrain();
      LOGGER.info("watch has been drained");

      // Get rid of custom resource definition
      deleteWeblogicCRD(client);
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.info(MessageKeys.EXCEPTION, e);
    } finally {
      ClientHelper.getInstance().recycle(client);
    }
  }

  /**
   * Create the weblogic-operator custom resource
   */
  private void createWeblogicCRD(ClientHolder client) {

    V1beta1CustomResourceDefinition crd = new V1beta1CustomResourceDefinition();
    crd.setApiVersion("apiextensions.k8s.io/v1beta1");
    crd.setKind("CustomResourceDefinition");
    V1ObjectMeta metaData = new V1ObjectMeta();
    metaData.setName(PLURAL + "." + GROUP);
    metaData.setNamespace(NAMESPACE);
    crd.setMetadata(metaData);
    V1beta1CustomResourceDefinitionSpec crds = new V1beta1CustomResourceDefinitionSpec();
    crds.setGroup(GROUP);
    crds.setVersion(VERSION);
    crds.setScope("Namespaced");
    V1beta1CustomResourceDefinitionNames crdn = new V1beta1CustomResourceDefinitionNames();
    crdn.setPlural(PLURAL);
    crdn.setSingular(SINGULAR);
    crdn.setKind(CRDNAME);
    crdn.setShortNames(Collections.singletonList(SHORTNAME));
    crds.setNames(crdn);
    crd.setSpec(crds);

    LOGGER.info("Creating CRD");
    try {
      client.getApiExtensionClient().createCustomResourceDefinition(crd, "false");
    } catch (Exception e) {
      LOGGER.info("Failed to create CRD - " + e);
    }
  }

  /**
   * Delete the custom resource definition
   *
   * @throws ApiException
   */
  private void deleteWeblogicCRD(ClientHolder client) {

    try {
      V1Status status = client.getApiExtensionClient().deleteCustomResourceDefinition(
          PLURAL + "." + GROUP
          , new V1DeleteOptions()
          , "false"
          , 1
          , false
          , null
      );
    } catch (Exception ex) {
      // The API throws an exception which is nonsense. The
      // resource definition gets deleted anyway. It looks
      // like Kubernetes is sending an object which the
      // client cannot handle.
    }
    LOGGER.info("Deleted CRD");
  }

  // Watch for domain custom resources. Create the watch and then wait
  // for some to get created externally using kubectl.
  private Watcher<TestDomain> watchForDomains() throws IOException {

    // Necessary to create a separate connection because actual watching
    // is managed in another thread.
    ClientHolder client = ClientHelper.getInstance().take();

    Watching<TestDomain> w = new Watching<TestDomain>() {

      @Override
      public Watch initiateWatch(Object context, String resourceVersion) throws ApiException {

        // resourceVersion is passed for each watch creation interation
        // to skip previous watch events in the resource history. It
        // must be passed each time a watch is created.

        return Watch.createWatch(
            client.getApiClient()
            , client.getCustomObjectsApiClient().listNamespacedCustomObjectCall(
                GROUP                 // group
                , VERSION               // version
                , NAMESPACE             // namespace
                , PLURAL                // plural
                , "true"                // pretty
                , ""                    // labelSelector
                , resourceVersion       // resourceVersion
                , Boolean.TRUE          // watch is true
                , null                  // progressListener
                , null                  // progressRequestListener
            )
            , new TypeToken<Watch.Response<TestDomain>>() {
            }.getType()
        );
      }

      private void formatTheObject(String type, Object object) {
        TestDomain dom = (TestDomain) object;
      }

      @Override
      public void eventCallback(Response<TestDomain> response) {
        switch (response.type) {
        case "ADDED":
          formatTheObject("Added", response.object);
          break;
        case "MODIFIED":
          formatTheObject("Modified", response.object);
          break;
        case "DELETED":
          formatTheObject("Deleted", response.object);
          finished.set(true);
          break;
        }
      }

      @Override
      public boolean isStopping() {
        return finished.get();
      }
    };
    
    return new Watcher<TestDomain>(w);
  }

  /**
   * Just wait for the watch operator to complete or terminate after
   * a total of 5 minutes.
   *
   * @param watcher
   */
  private void waitForFinished(ClientHolder client, Watcher watcher) throws InterruptedException {

    while (!finished.get() && timeoutLoop < 300) {

      switch (timeoutLoop) {
        case 3:
          createCustomResource(client, "domain1");
          break;

        case 6:
          createCustomResource(client, "domain2");
          break;
        case 9:
          createCustomResource(client, "domain3");
          break;

        case 12:
          deleteCustomResource(client, "domain2");
          break;

        default:
          break;
      }

      Thread.sleep(1000); // sleep for a second and check
      timeoutLoop++;
    }
    if (!finished.get()) {
      LOGGER.info("Test timed out due to inactivity");
    }
  }

  /**
   * Create a custom resource
   *
   * @param name
   * @throws InterruptedException
   */
  private void createCustomResource(ClientHolder client, String name) {

    TestDomain domain = new TestDomain();
    domain.setApiVersion(GROUP + "/" + VERSION);
    domain.setKind(CRDNAME);
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace(NAMESPACE);
    domain.setMetadata(metadata);
    Map<String, String> spec = new HashMap<>();
    spec.put("version", "12.2.3.0.1");
    spec.put("managedServerCount", "99");
    domain.setSpec(spec);

    try {

      client.getCustomObjectsApiClient().createNamespacedCustomObject(
          GROUP
          , VERSION
          , NAMESPACE
          , PLURAL
          , domain
          , "false");
    } catch (Exception ex) {
      LOGGER.info("Failed to create custom resource: " + name
          + ", Error: " + ex);
    }
  }

  /**
   * Delete a custom resource
   *
   * @param name
   */
  private void deleteCustomResource(ClientHolder client, String name) {
    TestDomain domain = new TestDomain();
    domain.setApiVersion(GROUP + "/" + VERSION);
    domain.setKind(CRDNAME);
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace(NAMESPACE);
    domain.setMetadata(metadata);
    Map<String, String> spec = new HashMap<>();
    spec.put("version", "12.2.3.0.1");
    spec.put("managedServerCount", "99");
    domain.setSpec(spec);

    try {
      client.getCustomObjectsApiClient().deleteNamespacedCustomObject(
          GROUP
          , VERSION
          , NAMESPACE
          , PLURAL
          , name
          , new V1DeleteOptions()
          , 1                 // gracePeriodSEconds
          , false             // orphanDependents
          , ""                // propagationPolicy
      );
    } catch (Exception ex) {
      LOGGER.info("Failed to delete custom resource: " + name
          + ", Error: " + ex);
    }
  }
}
