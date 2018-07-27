// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class HelmDomainValuesTest {
  private final int intValue = getRandomInt();
  private final String stringValue = Integer.toString(intValue);

  private static int getRandomInt() {
    return (int) (1000000 * Math.random());
  }

  private final HelmDomainValues domainValues = new HelmDomainValues();

  // ----- adminPort

  @Test
  public void whenAdminPortSet_createdMapContainsValue() {
    domainValues.adminPort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("adminPort", intValue));
  }

  @Test
  public void whenAdminPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("adminPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutAdminPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getAdminPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithAdminPort_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("adminPort", intValue));

    assertThat(values.getAdminPort(), equalTo(stringValue));
  }

  // ----- adminServerName

  @Test
  public void whenAdminServerNameSet_createdMapContainsValue() {
    domainValues.adminServerName(stringValue);

    assertThat(domainValues.createMap(), hasEntry("adminServerName", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutAdminServerName_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getAdminServerName(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithAdminServerName_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("adminServerName", stringValue));

    assertThat(values.getAdminServerName(), equalTo(stringValue));
  }

  // ----- domainUID

  @Test
  public void whenDomainNameSet_createdMapContainsValue() {
    domainValues.domainName(stringValue);

    assertThat(domainValues.createMap(), hasEntry("domainName", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutDomainName_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getDomainName(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithDomainName_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("domainName", stringValue));

    assertThat(values.getDomainName(), equalTo(stringValue));
  }

  // ----- domainUID

  @Test
  public void whenDomainUIDSet_createdMapContainsValue() {
    domainValues.domainUID(stringValue);

    assertThat(domainValues.createMap(), hasEntry("domainUID", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutDomainUID_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getDomainUID(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithDomainUID_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("domainUID", stringValue));

    assertThat(values.getDomainUID(), equalTo(stringValue));
  }

  // ----- clusterName

  @Test
  public void whenClusterNameSet_createdMapContainsValue() {
    domainValues.clusterName(stringValue);

    assertThat(domainValues.createMap(), hasEntry("clusterName", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutClusterName_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getClusterName(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithClusterName_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("clusterName", stringValue));

    assertThat(values.getClusterName(), equalTo(stringValue));
  }

  // ----- clusterType

  @Test
  public void whenClusterTypeSet_createdMapContainsValue() {
    domainValues.clusterType(stringValue);

    assertThat(domainValues.createMap(), hasEntry("clusterType", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutClusterType_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getClusterType(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithClusterType_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("clusterType", stringValue));

    assertThat(values.getClusterType(), equalTo(stringValue));
  }

  // ----- startupControl

  @Test
  public void whenStartupControlSet_createdMapContainsValue() {
    domainValues.startupControl(stringValue);

    assertThat(domainValues.createMap(), hasEntry("startupControl", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutStartupControl_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getStartupControl(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithStartupControl_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("startupControl", stringValue));

    assertThat(values.getStartupControl(), equalTo(stringValue));
  }

  // ----- configuredManagedServerCount

  @Test
  public void whenConfiguredManagedServerCountSet_createdMapContainsValue() {
    domainValues.configuredManagedServerCount(stringValue);

    assertThat(domainValues.createMap(), hasEntry("configuredManagedServerCount", intValue));
  }

  @Test
  public void whenConfiguredManagedServerCountNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("configuredManagedServerCount")));
  }

  @Test
  public void whenCreatedFromMapWithoutConfiguredManagedServerCount_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getConfiguredManagedServerCount(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithConfiguredManagedServerCount_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("configuredManagedServerCount", intValue));

    assertThat(values.getConfiguredManagedServerCount(), equalTo(stringValue));
  }

  // ----- initialManagedServerReplicas

  @Test
  public void whenInitialManagedServerReplicasSet_createdMapContainsValue() {
    domainValues.initialManagedServerReplicas(stringValue);

    assertThat(domainValues.createMap(), hasEntry("initialManagedServerReplicas", intValue));
  }

  @Test
  public void whenInitialManagedServerReplicasNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("initialManagedServerReplicas")));
  }

  @Test
  public void whenCreatedFromMapWithoutInitialManagedServerReplicas_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getInitialManagedServerReplicas(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithInitialManagedServerReplicas_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("initialManagedServerReplicas", intValue));

    assertThat(values.getInitialManagedServerReplicas(), equalTo(stringValue));
  }

  // ----- managedServerNameBase

  @Test
  public void whenManagedServerNameBaseSet_createdMapContainsValue() {
    domainValues.managedServerNameBase(stringValue);

    assertThat(domainValues.createMap(), hasEntry("managedServerNameBase", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutManagedServerNameBase_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getManagedServerNameBase(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithManagedServerNameBase_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("managedServerNameBase", stringValue));

    assertThat(values.getManagedServerNameBase(), equalTo(stringValue));
  }

  // ----- managedServerPort

  @Test
  public void whenManagedServerPortSet_createdMapContainsValue() {
    domainValues.managedServerPort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("managedServerPort", intValue));
  }

  @Test
  public void whenManagedServerPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("managedServerPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutManagedServerPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getManagedServerPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithManagedServerPort_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("managedServerPort", intValue));

    assertThat(values.getManagedServerPort(), equalTo(stringValue));
  }

  // ----- weblogicImage

  @Test
  public void whenWeblogicImageSet_createdMapContainsValue() {
    domainValues.weblogicImage(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicImage", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicImage_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicImage(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicImage_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("weblogicImage", stringValue));

    assertThat(values.getWeblogicImage(), equalTo(stringValue));
  }

  // ----- weblogicDomainStorageType

  @Test
  public void whenWeblogicDomainStorageTypeSet_createdMapContainsValue() {
    domainValues.weblogicDomainStorageType(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicDomainStorageType", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicDomainStorageType_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicDomainStorageType(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicDomainStorageType_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicDomainStorageType", stringValue));

    assertThat(values.getWeblogicDomainStorageType(), equalTo(stringValue));
  }

  // ----- weblogicDomainStorageNFSServer

  @Test
  public void whenWeblogicDomainStorageNFSServerSet_createdMapContainsValue() {
    domainValues.weblogicDomainStorageNFSServer(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicDomainStorageNFSServer", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicDomainStorageNFSServer_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicDomainStorageNFSServer(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicDomainStorageNFSServer_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicDomainStorageNFSServer", stringValue));

    assertThat(values.getWeblogicDomainStorageNFSServer(), equalTo(stringValue));
  }

  // ----- weblogicDomainStoragePath

  @Test
  public void whenWeblogicDomainStoragePathSet_createdMapContainsValue() {
    domainValues.weblogicDomainStoragePath(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicDomainStoragePath", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicDomainStoragePath_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicDomainStoragePath(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicDomainStoragePath_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicDomainStoragePath", stringValue));

    assertThat(values.getWeblogicDomainStoragePath(), equalTo(stringValue));
  }

  // ----- weblogicDomainStorageReclaimPolicy

  @Test
  public void whenWeblogicDomainStorageReclaimPolicySet_createdMapContainsValue() {
    domainValues.weblogicDomainStorageReclaimPolicy(stringValue);

    assertThat(
        domainValues.createMap(), hasEntry("weblogicDomainStorageReclaimPolicy", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicDomainStorageReclaimPolicy_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicDomainStorageReclaimPolicy(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicDomainStorageReclaimPolicy_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicDomainStorageReclaimPolicy", stringValue));

    assertThat(values.getWeblogicDomainStorageReclaimPolicy(), equalTo(stringValue));
  }

  // ----- weblogicDomainStorageSize

  @Test
  public void whenWeblogicDomainStorageSizeSet_createdMapContainsValue() {
    domainValues.weblogicDomainStorageSize(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicDomainStorageSize", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicDomainStorageSize_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicDomainStorageSize(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicDomainStorageSize_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicDomainStorageSize", stringValue));

    assertThat(values.getWeblogicDomainStorageSize(), equalTo(stringValue));
  }

  // --------------- productionModeEnabled

  @Test
  public void whenProductionModeEnabledTrue_createdMapContainsValue() {
    domainValues.productionModeEnabled("true");

    assertThat(domainValues.createMap(), hasEntry("productionModeEnabled", true));
  }

  @Test
  public void whenProductionModeEnabledFalse_createdMapContainsValue() {
    domainValues.productionModeEnabled("false");

    assertThat(domainValues.createMap(), hasEntry("productionModeEnabled", false));
  }

  @Test
  public void whenProductionModeEnabledNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("productionModeEnabled")));
  }

  @Test
  public void whenCreatedFromMapWithoutProductionModeEnabled_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getProductionModeEnabled(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithProductionModeEnabledTrue_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("productionModeEnabled", true));

    assertThat(values.getProductionModeEnabled(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithProductionModeEnabledFalse_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("productionModeEnabled", false));

    assertThat(values.getProductionModeEnabled(), equalTo("false"));
  }

  // ----- weblogicCredentialsSecretName

  @Test
  public void whenWeblogicCredentialsSecretNameSet_createdMapContainsValue() {
    domainValues.weblogicCredentialsSecretName(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicCredentialsSecretName", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicCredentialsSecretName_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicCredentialsSecretName(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicCredentialsSecretName_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicCredentialsSecretName", stringValue));

    assertThat(values.getWeblogicCredentialsSecretName(), equalTo(stringValue));
  }

  // ----- weblogicImagePullSecretName

  @Test
  public void whenWeblogicImagePullSecretNameSet_createdMapContainsValue() {
    domainValues.weblogicImagePullSecretName(stringValue);

    assertThat(domainValues.createMap(), hasEntry("weblogicImagePullSecretName", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicImagePullSecretName_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getWeblogicImagePullSecretName(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicImagePullSecretName_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("weblogicImagePullSecretName", stringValue));

    assertThat(values.getWeblogicImagePullSecretName(), equalTo(stringValue));
  }

  // ----- t3ChannelPort

  @Test
  public void whenT3ChannelPortSet_createdMapContainsValue() {
    domainValues.t3ChannelPort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("t3ChannelPort", intValue));
  }

  @Test
  public void whenT3ChannelPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("t3ChannelPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutT3ChannelPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getT3ChannelPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithT3ChannelPort_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("t3ChannelPort", intValue));

    assertThat(values.getT3ChannelPort(), equalTo(stringValue));
  }

  // ----- t3PublicAddress

  @Test
  public void whenT3PublicAddressSet_createdMapContainsValue() {
    domainValues.t3PublicAddress(stringValue);

    assertThat(domainValues.createMap(), hasEntry("t3PublicAddress", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutT3PublicAddress_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getT3PublicAddress(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithT3PublicAddress_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("t3PublicAddress", stringValue));

    assertThat(values.getT3PublicAddress(), equalTo(stringValue));
  }

  // --------------- exposeAdminT3Channel

  @Test
  public void whenExposeAdminT3ChannelTrue_createdMapContainsValue() {
    domainValues.exposeAdminT3Channel("true");

    assertThat(domainValues.createMap(), hasEntry("exposeAdminT3Channel", true));
  }

  @Test
  public void whenExposeAdminT3ChannelFalse_createdMapContainsValue() {
    domainValues.exposeAdminT3Channel("false");

    assertThat(domainValues.createMap(), hasEntry("exposeAdminT3Channel", false));
  }

  @Test
  public void whenExposeAdminT3ChannelNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("exposeAdminT3Channel")));
  }

  @Test
  public void whenCreatedFromMapWithoutExposeAdminT3Channel_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getExposeAdminT3Channel(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithExposeAdminT3ChannelTrue_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("exposeAdminT3Channel", true));

    assertThat(values.getExposeAdminT3Channel(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithExposeAdminT3ChannelFalse_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("exposeAdminT3Channel", false));

    assertThat(values.getExposeAdminT3Channel(), equalTo("false"));
  }

  // ----- adminNodePort

  @Test
  public void whenAdminNodePortSet_createdMapContainsValue() {
    domainValues.adminNodePort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("adminNodePort", intValue));
  }

  @Test
  public void whenAdminNodePortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("adminNodePort")));
  }

  @Test
  public void whenCreatedFromMapWithoutAdminNodePort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getAdminNodePort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithAdminNodePort_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("adminNodePort", intValue));

    assertThat(values.getAdminNodePort(), equalTo(stringValue));
  }

  // --------------- exposeAdminNodePort

  @Test
  public void whenExposeAdminNodePortTrue_createdMapContainsValue() {
    domainValues.exposeAdminNodePort("true");

    assertThat(domainValues.createMap(), hasEntry("exposeAdminNodePort", true));
  }

  @Test
  public void whenExposeAdminNodePortFalse_createdMapContainsValue() {
    domainValues.exposeAdminNodePort("false");

    assertThat(domainValues.createMap(), hasEntry("exposeAdminNodePort", false));
  }

  @Test
  public void whenExposeAdminNodePortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("exposeAdminNodePort")));
  }

  @Test
  public void whenCreatedFromMapWithoutExposeAdminNodePort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getExposeAdminNodePort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithExposeAdminNodePortTrue_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("exposeAdminNodePort", true));

    assertThat(values.getExposeAdminNodePort(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithExposeAdminNodePortFalse_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("exposeAdminNodePort", false));

    assertThat(values.getExposeAdminNodePort(), equalTo("false"));
  }

  // ----- namespace

  @Test
  public void whenNamespaceSet_createdMapContainsValue() {
    domainValues.namespace(stringValue);

    assertThat(domainValues.createMap(), hasEntry("namespace", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutNamespace_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getNamespace(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithNamespace_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("namespace", stringValue));

    assertThat(values.getNamespace(), equalTo(stringValue));
  }

  // ----- loadBalancer

  @Test
  public void whenLoadBalancerSet_createdMapContainsValue() {
    domainValues.loadBalancer(stringValue);

    assertThat(domainValues.createMap(), hasEntry("loadBalancer", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancer_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancer(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancer_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("loadBalancer", stringValue));

    assertThat(values.getLoadBalancer(), equalTo(stringValue));
  }

  // ----- loadBalancerAppPrepath

  @Test
  public void whenLoadBalancerAppPrepathSet_createdMapContainsValue() {
    domainValues.loadBalancerAppPrepath(stringValue);

    assertThat(domainValues.createMap(), hasEntry("loadBalancerAppPrepath", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancerAppPrepath_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancerAppPrepath(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerAppPrepath_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerAppPrepath", stringValue));

    assertThat(values.getLoadBalancerAppPrepath(), equalTo(stringValue));
  }

  // ----- loadBalancerVolumePath

  @Test
  public void whenLoadBalancerVolumePathSet_createdMapContainsValue() {
    domainValues.loadBalancerVolumePath(stringValue);

    assertThat(domainValues.createMap(), hasEntry("loadBalancerVolumePath", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancerVolumePath_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancerVolumePath(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerVolumePath_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerVolumePath", stringValue));

    assertThat(values.getLoadBalancerVolumePath(), equalTo(stringValue));
  }

  // --------------- loadBalancerExposeAdminPort

  @Test
  public void whenLoadBalancerExposeAdminPortTrue_createdMapContainsValue() {
    domainValues.loadBalancerExposeAdminPort("true");

    assertThat(domainValues.createMap(), hasEntry("loadBalancerExposeAdminPort", true));
  }

  @Test
  public void whenLoadBalancerExposeAdminPortFalse_createdMapContainsValue() {
    domainValues.loadBalancerExposeAdminPort("false");

    assertThat(domainValues.createMap(), hasEntry("loadBalancerExposeAdminPort", false));
  }

  @Test
  public void whenLoadBalancerExposeAdminPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("loadBalancerExposeAdminPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancerExposeAdminPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancerExposeAdminPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerExposeAdminPortTrue_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerExposeAdminPort", true));

    assertThat(values.getLoadBalancerExposeAdminPort(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerExposeAdminPortFalse_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerExposeAdminPort", false));

    assertThat(values.getLoadBalancerExposeAdminPort(), equalTo("false"));
  }

  // ----- loadBalancerWebPort

  @Test
  public void whenLoadBalancerWebPortSet_createdMapContainsValue() {
    domainValues.loadBalancerWebPort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("loadBalancerWebPort", intValue));
  }

  @Test
  public void whenLoadBalancerWebPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("loadBalancerWebPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancerWebPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancerWebPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerWebPort_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerWebPort", intValue));

    assertThat(values.getLoadBalancerWebPort(), equalTo(stringValue));
  }

  // ----- loadBalancerDashboardPort

  @Test
  public void whenLoadBalancerDashboardPortSet_createdMapContainsValue() {
    domainValues.loadBalancerDashboardPort(stringValue);

    assertThat(domainValues.createMap(), hasEntry("loadBalancerDashboardPort", intValue));
  }

  @Test
  public void whenLoadBalancerDashboardPortNotSet_createdMapLacksValue() {
    assertThat(domainValues.createMap(), not(hasKey("loadBalancerDashboardPort")));
  }

  @Test
  public void whenCreatedFromMapWithoutLoadBalancerDashboardPort_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getLoadBalancerDashboardPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithLoadBalancerDashboardPort_hasSpecifiedValue() {
    HelmDomainValues values =
        new HelmDomainValues(ImmutableMap.of("loadBalancerDashboardPort", intValue));

    assertThat(values.getLoadBalancerDashboardPort(), equalTo(stringValue));
  }

  // ----- javaOptions

  @Test
  public void whenJavaOptionsSet_createdMapContainsValue() {
    domainValues.javaOptions(stringValue);

    assertThat(domainValues.createMap(), hasEntry("javaOptions", stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutJavaOptions_hasEmptyString() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of());

    assertThat(values.getJavaOptions(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithJavaOptions_hasSpecifiedValue() {
    HelmDomainValues values = new HelmDomainValues(ImmutableMap.of("javaOptions", stringValue));

    assertThat(values.getJavaOptions(), equalTo(stringValue));
  }
}
