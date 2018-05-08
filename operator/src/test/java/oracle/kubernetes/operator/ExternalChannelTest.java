// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.kubernetes.client.models.V1ObjectMeta;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.ExternalAdminChannelsStep;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExternalChannelTest {

  private Domain domain = new Domain();
  private DomainSpec spec = new DomainSpec();
  private V1ObjectMeta meta = new V1ObjectMeta();
  private List<String> chlist = new ArrayList<>();
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

  private static final Logger UNDERLYING_LOGGER =
      LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
  private List<Handler> savedhandlers;

  @Before
  public void disableConsoleLogging() {
    savedhandlers = TestUtils.removeConsoleHandlers(UNDERLYING_LOGGER);
  }

  @After
  public void restoreConsoleLogging() {
    TestUtils.restoreConsoleHandlers(UNDERLYING_LOGGER, savedhandlers);
  }

  @Before
  public void setupConstants() throws NoSuchFieldException {

    domain.setMetadata(meta);
    domain.setSpec(spec);

    meta.setNamespace("default");
    spec.setAsName("admin-server");
    spec.setExportT3Channels(chlist);

    wlsDomainConfig = WlsDomainConfig.create(jsonString);

    WlsServerConfig adminServerConfig = wlsDomainConfig.getServerConfig(spec.getAsName());
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

  @Test
  public void externalChannelNotDefinedTest() {
    chlist.clear();
    chlist.add("channel-99");

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

    chlist.clear();
    chlist.add("Channel-3");
    setListenPort(7001);
    setPublicPort(7001);

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  @Test
  public void externalChannelUnequalListenPortsTest() throws IllegalAccessException {

    chlist.clear();
    chlist.add("Channel-3");
    setListenPort(39001);
    setPublicPort(39002);

    Collection<NetworkAccessPoint> validNaps =
        ExternalAdminChannelsStep.adminChannelsToCreate(wlsDomainConfig, domain);
    assertNotNull(validNaps);
    assertEquals(0, validNaps.size());
  }

  @Test
  public void externalChannelWrongProtocolTest() throws IllegalAccessException {

    chlist.clear();
    chlist.add("Channel-3");
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

    chlist.clear();
    chlist.add("Channel-3");
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
