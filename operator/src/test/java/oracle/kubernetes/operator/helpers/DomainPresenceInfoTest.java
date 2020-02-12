// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

public class DomainPresenceInfoTest {
  private DomainPresenceInfo info = new DomainPresenceInfo("ns", "domain");

  @Test
  public void whenNoneDefined_getClusterServiceReturnsNull() {
    assertThat(info.getClusterService("cluster"), nullValue());
  }

  @Test
  public void afterClusterServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setClusterService("cluster", service);

    assertThat(info.getClusterService("cluster"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getServerServiceReturnsNull() {
    assertThat(info.getServerService("admin"), nullValue());
  }

  @Test
  public void afterServerServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setServerService("admin", service);

    assertThat(info.getServerService("admin"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getExternalServiceReturnsNull() {
    assertThat(info.getExternalService("admin"), nullValue());
  }

  @Test
  public void afterExternalServiceDefined_nextCallReturnsIt() {
    V1Service service = new V1Service();
    info.setExternalService("admin", service);

    assertThat(info.getExternalService("admin"), sameInstance(service));
  }

  @Test
  public void whenNoneDefined_getServerPodReturnsNull() {
    assertThat(info.getServerPod("myserver"), nullValue());
  }

  @Test
  public void afterServerPodDefined_nextCallReturnsIt() {
    V1Pod pod = new V1Pod();
    info.setServerPod("myserver", pod);

    assertThat(info.getServerPod("myserver"), sameInstance(pod));
  }
}
