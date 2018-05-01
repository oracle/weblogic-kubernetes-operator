// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WlsDynamicServerConfigTest {

  @Test
  public void testCreateWithFixedPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3",
            10000, 30001);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template = new WlsServerConfig("template1", 1000,
            null, 2000,
            true, null,  networkAccessPointList);

    WlsServerConfig wlsServerConfig = WlsDynamicServerConfig.create("server1", 2,
            "cluster1", "domain1", false, template);

    assertEquals(new Integer(1000), wlsServerConfig.getListenPort());
    assertEquals(new Integer(2000), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(new Integer(10000), networkAccessPoint1.getListenPort());
    assertEquals(new Integer(30001), networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithNullPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3",
            null, null);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template = new WlsServerConfig("template1", null,
            null, null,
            true, null,  networkAccessPointList);

    WlsServerConfig wlsServerConfig = WlsDynamicServerConfig.create("server1", 2,
            "cluster1", "domain1", false, template);

    assertNull(wlsServerConfig.getListenPort());
    assertNull(wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertNull(networkAccessPoint1.getListenPort());
    assertNull(networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithCalculatedPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3",
            10000, 30001);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template = new WlsServerConfig("template1", 1000,
            null, 2000,
            true, null,  networkAccessPointList);

    WlsServerConfig wlsServerConfig = WlsDynamicServerConfig.create("server1", 2,
            "cluster1", "domain1", true, template);

    assertEquals(new Integer(1002), wlsServerConfig.getListenPort());
    assertEquals(new Integer(2002), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(new Integer(10002), networkAccessPoint1.getListenPort());
    assertEquals(new Integer(30001), networkAccessPoint1.getPublicPort());
  }

  @Test
  public void testCreateWithCalculatedDefaultPorts() {
    NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint("Channel1", "t3",
            null, null);
    List<NetworkAccessPoint> networkAccessPointList = new ArrayList<>();
    networkAccessPointList.add(networkAccessPoint);
    WlsServerConfig template = new WlsServerConfig("template1", null,
            null, null,
            true, null,  networkAccessPointList);

    WlsServerConfig wlsServerConfig = WlsDynamicServerConfig.create("server1", 2,
            "cluster1", "domain1", true, template);

    assertEquals(new Integer(7102), wlsServerConfig.getListenPort());
    assertEquals(new Integer(8102), wlsServerConfig.getSslListenPort());
    NetworkAccessPoint networkAccessPoint1 = wlsServerConfig.getNetworkAccessPoints().get(0);
    assertEquals(new Integer(9102), networkAccessPoint1.getListenPort());
    assertNull(networkAccessPoint1.getPublicPort());
  }
}
