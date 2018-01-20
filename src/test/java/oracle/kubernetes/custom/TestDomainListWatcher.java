/* Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved. */
package oracle.kubernetes.custom;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainWatcher;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.fail;

/**
 * This test class verifies that watch events are received for custom resource
 * operations.
 */
public class TestDomainListWatcher implements WatchingEventDestination<Domain> {

  private static final String NAMESPACE = "default";
  private static final String NAME = "domains.weblogic.oracle";
  private static final String GROUP = "weblogic.oracle";
  private static final String VERSION = "v1";
  private static final String KIND = "Domain";
  private static final String PLURAL = "domains";
  private static final String SINGULAR = "domain";
  private static final String SHORTNAME = "dom";

  private ArrayList<String> feedback = new ArrayList<>();

  private boolean crdCreatedHere = false;

  @Before
  public void beforeTest() {
    // Create the Domain definition if it doesn't exist.
    createWeblogicCRD();
  }

  @After
  public void afterTest() {
    // If the domain definition was done here then get rid of it.
    deleteWeblogicCRD();
  }

  /**
   * Create and delete about 20 custom resources. The callback makes sure
   * they are all reported.
   */
  @Test
  public void testDomainListWatcher() {

    AtomicBoolean isStopping = new AtomicBoolean(false);
    DomainWatcher dlw = DomainWatcher.create("default", "", this, isStopping);
    
    sleep(4000);
    
    ClientHolder clientHolder = ClientHelper.getInstance().take();

    // Generate 20 custom objects.
    for (int i = 0; i < 20; i++) {
      createCustomResource(clientHolder, "testdomain" + i, true);
    }

    // wait a bit
    sleep(5000);

    // delete all the created objects
    for (int i = 0; i < 20; i++) {
      deleteCustomResource(clientHolder, "testdomain" + i, true);
    }

    // wait for all events to be processed, then say goodbye
    for (int count = 30; count > 0; count--) {
      synchronized (feedback) {
        if (feedback.isEmpty()) {
          System.out.println("Processed all events successfully, shutting down");
          isStopping.set(true);
          return;
        }
      }
      sleep(1000);
    }

    // Timed out. report all events that were not seen by the watcher
    synchronized (feedback) {
      for (String token : feedback) {
        System.out.println("Missing event for " + token);
      }
    }
    fail("Not all watch events were received for created objects");
  }

  // This override intercepts all watch events whioch would have
  // normally sent to the operator for processing.
  @Override
  public void eventCallback(Watch.Response<Domain> item) {

    Domain domain = (Domain) item.object;
    String token = item.type + "." + domain.getMetadata().getName();

    synchronized (feedback) {
      if (feedback.contains(token)) {
        System.out.println("Received watch for " + token);
        feedback.remove(token);
      }
    }
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ex) {

    }
  }

  /**
   * Create the weblogic-operator custom resource
   */
  private void createWeblogicCRD() {

    ClientHolder clientHolder = ClientHelper.getInstance().take();

    V1beta1CustomResourceDefinition crd = new V1beta1CustomResourceDefinition();
    crd.setApiVersion("apiextensions.k8s.io/v1beta1");
    crd.setKind("CustomResourceDefinition");
    V1ObjectMeta om = new V1ObjectMeta();
    om.setName(NAME);
    crd.setMetadata(om);
    V1beta1CustomResourceDefinitionSpec crds = new V1beta1CustomResourceDefinitionSpec();
    crds.setGroup(GROUP);
    crds.setVersion(VERSION);
    crds.setScope("Namespaced");
    V1beta1CustomResourceDefinitionNames crdn = new V1beta1CustomResourceDefinitionNames();
    crdn.setPlural(PLURAL);
    crdn.setSingular(SINGULAR);
    crdn.setKind(KIND);
    crdn.setShortNames(Collections.singletonList(SHORTNAME));
    crds.setNames(crdn);
    crd.setSpec(crds);

    try {
      clientHolder.getApiExtensionClient().createCustomResourceDefinition(crd, "false");
      System.out.println("Created CRD: " + NAME);
      crdCreatedHere = true;
    } catch (Exception e) {
      if (!e.getMessage().equalsIgnoreCase("Conflict")) {
        fail("Failed to create CRD: " + NAME + " - " + e);
      }
    }
    
    createCustomResource(clientHolder, "testdomain"+100, false);
    createCustomResource(clientHolder, "testdomain"+200, false);
    ClientHelper.getInstance().recycle(clientHolder);
  }

  /**
   * Delete the custom resource definition
   */
  private void deleteWeblogicCRD() {

    if (!crdCreatedHere) {
      return;
    }

    ClientHolder clientHolder = ClientHelper.getInstance().take();
    try {
      V1Status status = clientHolder.getApiExtensionClient().deleteCustomResourceDefinition(
          NAME
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
    ClientHelper.getInstance().recycle(clientHolder);
    System.out.println("Deleted CRD:" + NAME);
  }

  private void createCustomResource(ClientHolder clientHolder, String name, boolean tag) {

    Domain domain = new Domain();
    domain.setApiVersion(GROUP + "/" + VERSION);
    domain.setKind(KIND);
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace(NAMESPACE);
    domain.setMetadata(metadata);
    DomainSpec spec = new DomainSpec();
    spec.setAsName(name);
    spec.setDomainName(name);
    spec.setDomainUID(UUID.randomUUID().toString());
    domain.setSpec(spec);

    String token = "ADDED." + name;
    try {
      if ( tag ) {  
        System.out.println("Creating " + token);
        synchronized (feedback) {
           feedback.add(token);
        }
      }  

      clientHolder.getCustomObjectsApiClient().createNamespacedCustomObject(
          GROUP
          , VERSION
          , NAMESPACE
          , PLURAL
          , domain
          , "false");
    } catch (Exception ex) {
      fail("Failed to create custom resource: " + name
          + ", Error: " + ex);
    }
  }

  private void deleteCustomResource(ClientHolder client, String name, boolean tag) {

    Domain domain = new Domain();
    domain.setApiVersion(GROUP + "/" + VERSION);
    domain.setKind(KIND);
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName(name);
    metadata.setNamespace(NAMESPACE);
    domain.setMetadata(metadata);

    String token = "DELETED." + name;
    try {
        if ( tag ) {
            System.out.println("Deleting " + token);
            synchronized (feedback) {
                feedback.add(token);
            }
        }

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
      System.out.println("Failed to delete custom resource: " + name
          + ", Error: " + ex);
      if ( tag ) {
        synchronized (feedback) {
            feedback.remove(token);
        }
      }
    }
  }
}
