// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WlsDynamicServerConfigTest {

  @Test
  public void testCreateWithFixedPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3", 10000, 30001);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template =
        new WlsServerConfig("template1", null, null, 1000, 2000, null, networkAccessPointList);

    WlsServerConfig wlsServerConfig =
        WlsDynamicServerConfig.create("server1", 2, "cluster1", "domain1", false, template);

    assertEquals(Integer.valueOf(1000), wlsServerConfig.getListenPort());
    assertEquals(Integer.valueOf(2000), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(Integer.valueOf(10000), networkAccessPoint1.getListenPort());
    assertEquals(Integer.valueOf(30001), networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithNullPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3", null, null);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template =
        new WlsServerConfig("template1", null, null, null, null, null, networkAccessPointList);

    WlsServerConfig wlsServerConfig =
        WlsDynamicServerConfig.create("server1", 2, "cluster1", "domain1", false, template);

    assertNull(wlsServerConfig.getListenPort());
    assertNull(wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertNull(networkAccessPoint1.getListenPort());
    assertNull(networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithCalculatedPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3", 10000, 30001);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template =
        new WlsServerConfig("template1", null, null, 1000, 2000, null, networkAccessPointList);

    WlsServerConfig wlsServerConfig =
        WlsDynamicServerConfig.create("server1", 2, "cluster1", "domain1", true, template);

    assertEquals(Integer.valueOf(1002), wlsServerConfig.getListenPort());
    assertEquals(Integer.valueOf(2002), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(Integer.valueOf(10002), networkAccessPoint1.getListenPort());
    assertEquals(Integer.valueOf(30001), networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithCalculatedDefaultPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3", null, null);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template =
        new WlsServerConfig("template1", null, null, null, null, null, networkAccessPointList);

    WlsServerConfig wlsServerConfig =
        WlsDynamicServerConfig.create("server1", 2, "cluster1", "domain1", true, template);

    assertEquals(Integer.valueOf(7102), wlsServerConfig.getListenPort());
    assertEquals(Integer.valueOf(8102), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(Integer.valueOf(9102), networkAccessPoint1.getListenPort());
    assertNull(networkAccessPoint1.getPublicPort());
  }

  @Test
  public void verifyAdminPortIsSetOnServerConfigs() {
    final int adminPort = 9002;
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    WlsServerConfig template =
        new WlsServerConfig("template1", null, null, null, null, 9002, networkAccessPointList);

    WlsServerConfig wlsServerConfig =
        WlsDynamicServerConfig.create("server1", 2, "cluster1", "domain1", true, template);

    assertThat(wlsServerConfig.getAdminPort(), is(adminPort));
  }
}
