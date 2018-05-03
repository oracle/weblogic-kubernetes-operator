// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1beta1HTTPIngressPath;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValue;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackend;
import io.kubernetes.client.models.V1beta1IngressRule;
import io.kubernetes.client.models.V1beta1IngressSpec;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * To test Ingress Helper
 */
@Ignore
public class IngressHelperTest {
  private final String namespace = "weblogic-operator";
  private final String domainUID = "domianIngressHelperTest";
  private final String clusterName = "cluster1";
  private final String server1Name = "server1";
  private final Integer server1Port = 8001;
  private final String server2Name = "server2";
  private final Integer server2Port = 8002;
  private final String service1Name = CallBuilder.toDNS1123LegalName(domainUID + "-" + server1Name);
  private final String service2Name = CallBuilder.toDNS1123LegalName(domainUID + "-" + server2Name);
  private final String ingressName =  CallBuilder.toDNS1123LegalName(domainUID + "-" + clusterName);
  private final String clusterServiceName = CallBuilder.toDNS1123LegalName(domainUID + "-cluster-" + clusterName);

  
  private DomainPresenceInfo info;
  private Engine engine;
  
  @Before
  public void setUp() throws ApiException {
    // make sure test bed is clean
    tearDown();
    
    // Create domain
    Domain domain = new Domain();
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setName("domianIngressHelperTest");
    metadata.setNamespace(namespace);
    domain.setMetadata(metadata);
    
    DomainSpec spec = new DomainSpec();
    spec.setDomainName("base_domain");
    spec.setDomainUID(domainUID);
    domain.setSpec(spec);
    
    info = new DomainPresenceInfo(domain);
    
    // Create scan
    WlsDomainConfig scan = new WlsDomainConfig(null);
    WlsServerConfig server1Scan = new WlsServerConfig(server1Name, server1Port, server1Name, null, false, null, null);
    WlsServerConfig server2Scan = new WlsServerConfig(server2Name, server2Port, server2Name, null, false, null, null);

    scan.getServerConfigs().put(server1Name, server1Scan);
    scan.getServerConfigs().put(server2Name, server2Scan);

    WlsClusterConfig cluster1Scan = new WlsClusterConfig(clusterName);
    cluster1Scan.getServerConfigs().add(server1Scan);
    cluster1Scan.getServerConfigs().add(server2Scan);
    
    scan.getClusterConfigs().put(clusterName, cluster1Scan);
    
    info.setScan(scan);
    
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    V1Service service = new V1Service();
    V1ObjectMeta sm = new V1ObjectMeta();
    sm.setName(service1Name);
    sm.setNamespace(namespace);
    service.setMetadata(sm);
    V1ServiceSpec ss = new V1ServiceSpec();
    V1ServicePort port = new V1ServicePort();
    port.setPort(server1Port);
    ss.addPortsItem(port);
    service.setSpec(ss);
    sko.getService().set(service);
    info.getServers().put(server1Name, sko);
    
    sko = new ServerKubernetesObjects();
    service = new V1Service();
    sm = new V1ObjectMeta();
    sm.setName(service2Name);
    sm.setNamespace(namespace);
    service.setMetadata(sm);
    ss = new V1ServiceSpec();
    port = new V1ServicePort();
    port.setPort(server2Port);
    ss.addPortsItem(port);
    service.setSpec(ss);
    sko.getService().set(service);
    info.getServers().put(server2Name, sko);

    engine = new Engine("IngressHelperTest");
  }
  
  @After
  public void tearDown() throws ApiException {
    CallBuilderFactory factory = new CallBuilderFactory();
    try {
      factory.create().deleteIngress(ingressName, namespace, new V1DeleteOptions());
    } catch (ApiException api) {
      if (api.getCode() != CallBuilder.NOT_FOUND) {
        throw api;
      }
    }
  }

  @Test
  public void testAddThenRemoveServer() throws Throwable {
    Packet p = new Packet();
    p.getComponents().put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(info));
    p.put(ProcessingConstants.SERVER_SCAN, info.getScan().getServerConfig(server1Name));
    p.put(ProcessingConstants.CLUSTER_SCAN, info.getScan().getClusterConfig(clusterName));
    p.put(ProcessingConstants.SERVER_NAME, server1Name);

    Fiber f = engine.createFiber();
    Step s = IngressHelper.createClusterStep(null);
    AtomicReference<Throwable> t = new AtomicReference<>();
    f.start(s, p, new CompletionCallback() {
      @Override
      public void onCompletion(Packet packet) {
        // no-op
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        t.set(throwable);
      }
    });
    f.get(30, TimeUnit.SECONDS);
    if (t.get() != null) {
      throw t.get();
    }
    
    // Now check
    CallBuilderFactory factory = new CallBuilderFactory();
    V1beta1Ingress v1beta1Ingress = factory.create().readIngress(ingressName, namespace);
    
    List<V1beta1HTTPIngressPath> v1beta1HTTPIngressPaths = getPathArray(v1beta1Ingress);
    Assert.assertEquals("IngressPaths should have one instance of IngressPath", 1, v1beta1HTTPIngressPaths.size());
    V1beta1HTTPIngressPath v1beta1HTTPIngressPath = v1beta1HTTPIngressPaths.get(0);
    Assert.assertEquals("/", v1beta1HTTPIngressPath.getPath());
    V1beta1IngressBackend v1beta1IngressBackend = v1beta1HTTPIngressPath.getBackend();
    Assert.assertNotNull("IngressBackend Object should not be null", v1beta1IngressBackend);
    Assert.assertEquals("Service name should be " + clusterServiceName, clusterServiceName, v1beta1IngressBackend.getServiceName());
    Assert.assertEquals("Service port should be " + server1Port, server1Port, v1beta1IngressBackend.getServicePort().getIntValue());

  }

  private List<V1beta1HTTPIngressPath> getPathArray(V1beta1Ingress v1beta1Ingress) {
    Assert.assertNotNull("Ingress Object should not be null", v1beta1Ingress);
    V1beta1IngressSpec v1beta1IngressSpec = v1beta1Ingress.getSpec();
    Assert.assertNotNull("Spec Object should not be null", v1beta1IngressSpec);
    List<V1beta1IngressRule> v1beta1IngressRules = v1beta1IngressSpec.getRules();
    Assert.assertNotNull("Rules List should not be null", v1beta1IngressRules);
    Assert.assertTrue("Rules List  should have one instance of IngressRule", v1beta1IngressRules.size() == 1);
    V1beta1IngressRule v1beta1IngressRule = v1beta1IngressRules.get(0);
    Assert.assertNotNull("IngressRule Object should not be null", v1beta1IngressRule);
    V1beta1HTTPIngressRuleValue v1beta1HTTPIngressRuleValue = v1beta1IngressRule.getHttp();
    Assert.assertNotNull("IngressRuleValue Object should not be null", v1beta1HTTPIngressRuleValue);
    List<V1beta1HTTPIngressPath> v1beta1HTTPIngressPaths = v1beta1HTTPIngressRuleValue.getPaths();
    Assert.assertNotNull("IngressPath list should not be null", v1beta1HTTPIngressPaths);
    return v1beta1HTTPIngressPaths;
  }
}
