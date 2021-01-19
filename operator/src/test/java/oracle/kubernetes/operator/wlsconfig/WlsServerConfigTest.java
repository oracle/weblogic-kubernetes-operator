// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class WlsServerConfigTest {

  static final int LISTEN_PORT = 8001;
  static final int SSL_LISTEN_PORT = 8002;
  static final int ADMIN_PORT = 9002;
  static final int NAP_ADMIN_PORT = 8082;
  static final int NAP_NON_ADMIN_PORT = 8081;

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithOnlyListenPort();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(LISTEN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(false));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsSslListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithListenPortAndSslListenPort();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(SSL_LISTEN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_returnsAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_withAdminNap_returnsNapAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithAdminNap();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(NAP_ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  @Test
  public void verify_getLocalAdminProtocolChannelPort_withNonAdminNap_returnsAdminPort() {
    WlsServerConfig wlsServerConfig = createConfigWithNonAdminNap();
    assertThat(wlsServerConfig.getLocalAdminProtocolChannelPort(), is(ADMIN_PORT));
    assertThat(wlsServerConfig.isLocalAdminProtocolChannelSecure(), is(true));
  }

  WlsServerConfig createConfigWithOnlyListenPort() {
    WlsServerConfig wlsServerConfig = new WlsServerConfig();
    wlsServerConfig.setListenPort(LISTEN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithListenPortAndSslListenPort() {
    WlsServerConfig wlsServerConfig = createConfigWithOnlyListenPort();
    wlsServerConfig.setSslListenPort(SSL_LISTEN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithAllListenPorts() {
    WlsServerConfig wlsServerConfig = createConfigWithListenPortAndSslListenPort();
    wlsServerConfig.setAdminPort(ADMIN_PORT);
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithAdminNap() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    wlsServerConfig.addNetworkAccessPoint(
        new NetworkAccessPoint("admin-channel", "admin", NAP_ADMIN_PORT, null));
    return wlsServerConfig;
  }

  WlsServerConfig createConfigWithNonAdminNap() {
    WlsServerConfig wlsServerConfig = createConfigWithAllListenPorts();
    wlsServerConfig.addNetworkAccessPoint(
        new NetworkAccessPoint("non-admin-channel", "t3", NAP_NON_ADMIN_PORT, null));
    return wlsServerConfig;
  }
}
