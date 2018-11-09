// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.steps.ExternalAdminChannelsStep;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExternalChannelTest {

  private static final String ADMIN_SERVER = "admin-server";
  private DomainSpec spec = new DomainSpec();
  private V1ObjectMeta meta = new V1ObjectMeta().namespace("default");
  private Domain domain = new Domain().withMetadata(meta).withSpec(spec);
  private WlsDomainConfig wlsDomainConfig;

  private Field protocol;
  private Field listenPort;
  private Field publicPort;
  private NetworkAccessPoint nap;

  private String jsonString =
      ""
          + "{\"servers\": {\"items\": ["
          + "    {"
          + "        \"listenAddress\": \"domain1-admin-server\","
          + "        \"name\": \"admin-server\","
          + "        \"listenPort\": 7001,"
          + "        \"cluster\": null,"
          + "        \"networkAccessPoints\": {\"items\": ["
          + "            {"
          + "                \"publicPort\": 31009,"
          + "                \"protocol\": \"t3\","
          + "                \"name\": \"Channel-3\","
          + "                \"listenPort\": 31009"
          + "            }"
          + "        ]}"
          + "    },"
          + "    {"
          + "        \"listenAddress\": \"domain1-managed-server1\","
          + "        \"name\": \"managed-server1\","
          + "        \"listenPort\": 8001,"
          + "        \"cluster\": ["
          + "            \"clusters\","
          + "            \"cluster-1\""
          + "        ],"
          + "        \"networkAccessPoints\": {\"items\": []}"
          + "    }"
          + "]}}";

  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void disableConsoleLogging() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @After
  public void restoreConsoleLogging() {
    for (Memento memento : mementos) memento.revert();
  }

  @Before
  public void setupConstants() throws NoSuchFieldException {
    wlsDomainConfig = WlsDomainConfig.create(jsonString);

    WlsServerConfig adminServerConfig = wlsDomainConfig.getServerConfig(ADMIN_SERVER);
    List<NetworkAccessPoint> naps = adminServerConfig.getNetworkAccessPoints();

    nap = naps.get(0);

    Class<?> cls = nap.getClass();
    protocol = cls.getDeclaredField("protocol");
    listenPort = cls.getDeclaredField("listenPort");
    publicPort = cls.getDeclaredField("publicPort");
    protocol.setAccessible(true);
    listenPort.setAccessible(true);
    publicPort.setAccessible(true);
  }

  private void configureExternalChannels(String... names) {
    DomainConfiguratorFactory.forDomain(domain)
        .configureAdminServer(ADMIN_SERVER)
        .withExportedNetworkAccessPoints(names);
  }

  @Test
  public void externalChannelNotDefinedTest() {
    configureExternalChannels("channel-99");

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  private void setProtocol(String newProtocol) throws IllegalAccessException {
    protocol.set(nap, newProtocol);
  }

  private void setListenPort(int port) throws IllegalAccessException {
    Integer thePort = port;
    listenPort.set(nap, thePort);
  }

  private void setPublicPort(int port) throws IllegalAccessException {
    Integer thePort = port;
    publicPort.set(nap, thePort);
  }

  @Test
  public void externalChannelOutsideRangeTest() throws Exception {
    configureExternalChannels("Channel-3");
    setListenPort(7001);
    setPublicPort(7001);

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  @Test
  public void externalChannelUnequalListenPortsTest() throws IllegalAccessException {
    configureExternalChannels("Channel-3");
    setListenPort(39001);
    setPublicPort(39002);

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  @Test
  public void externalChannelWrongProtocolTest() throws IllegalAccessException {
    configureExternalChannels("Channel-3");
    setListenPort(39001);
    setPublicPort(39001);
    setProtocol("http");

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  @Test
  public void externalChannelTest() throws IllegalAccessException {
    configureExternalChannels("Channel-3");
    setListenPort(30999);
    setPublicPort(30999);
    setProtocol("t3");

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(1, validNaps.size());
    assertEquals("Channel-3", validNaps.iterator().next().getName());
  }
}
