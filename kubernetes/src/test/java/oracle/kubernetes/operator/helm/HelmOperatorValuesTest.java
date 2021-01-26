// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class HelmOperatorValuesTest {

  private final int intValue = getRandomInt();
  private final String stringValue = Integer.toString(intValue);
  private final HelmOperatorValues operatorValues = new HelmOperatorValues();

  private static int getRandomInt() {
    return (int) (1000000 * Math.random());
  }

  @Test
  public void whenServiceAccountSet_createdMapContainsValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("serviceAccount", stringValue));
  }

  @Test
  public void serviceAccountIsGettableStringValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutServiceAccount_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getServiceAccount(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithServiceAccount_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("serviceAccount", stringValue));

    assertThat(values.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  public void whenWeblogicOperatorImageSet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("image", stringValue));
  }

  @Test
  public void weblogicOperatorImageIsGettableStringValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.getWeblogicOperatorImage(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutImage_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImage(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithImage_hasSpecifiedValue() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of("image", stringValue));

    assertThat(values.getWeblogicOperatorImage(), equalTo(stringValue));
  }

  // ----- javaLoggingLevel

  @Test
  public void whenJavaLoggingLevelSet_createdMapContainsValue() {
    operatorValues.javaLoggingLevel(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("javaLoggingLevel", stringValue));
  }

  @Test
  public void whenJavaLoggingLevelNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("javaLoggingLevel")));
  }

  @Test
  public void javaLoggingLevelIsGettableStringValue() {
    operatorValues.javaLoggingLevel(stringValue);

    assertThat(operatorValues.getJavaLoggingLevel(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutJavaLoggingLevel_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getJavaLoggingLevel(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithJavaLoggingLevel_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("javaLoggingLevel", stringValue));

    assertThat(values.getJavaLoggingLevel(), equalTo(stringValue));
  }

  // ------------ operatorNamespace

  @Test
  public void whenWeblogicOperatorNamespaceSet_createdMapContainsValue() {
    operatorValues.namespace(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("operatorNamespace", stringValue));
  }

  @Test
  public void weblogicOperatorNamespaceIsGettableStringValue() {
    operatorValues.namespace(stringValue);

    assertThat(operatorValues.getNamespace(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicOperatorNamespace_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getNamespace(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicOperatorNamespace_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("operatorNamespace", stringValue));

    assertThat(values.getNamespace(), equalTo(stringValue));
  }

  @Test
  public void whenWeblogicOperatorImagePullPolicySet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImagePullPolicy(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("imagePullPolicy", stringValue));
  }

  @Test
  public void weblogicOperatorImagePullPolicyIsGettableStringValue() {
    operatorValues.weblogicOperatorImagePullPolicy(stringValue);

    assertThat(operatorValues.getWeblogicOperatorImagePullPolicy(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutImagePullPolicy_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithImagePullPolicy_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("imagePullPolicy", stringValue));

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithExternalRestEnabled_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalRestEnabled", true));

    assertThat(values.getExternalRestEnabled(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithoutExternalRestEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalRestEnabled(), equalTo(""));
  }

  // --------------- remoteDebugNodePortEnabled

  @Test
  public void whenRemoteDebugNodePortEnabledTrue_createdMapContainsValue() {
    operatorValues.remoteDebugNodePortEnabled("true");

    assertThat(operatorValues.createMap(), hasEntry("remoteDebugNodePortEnabled", true));
  }

  @Test
  public void whenRemoteDebugNodePortEnabledFalse_createdMapContainsValue() {
    operatorValues.remoteDebugNodePortEnabled("false");

    assertThat(operatorValues.createMap(), hasEntry("remoteDebugNodePortEnabled", false));
  }

  @Test
  public void whenRemoteDebugNodePortEnabledNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("remoteDebugNodePortEnabled")));
  }

  @Test
  public void whenCreatedFromMapWithoutRemoteDebugNodePortEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithRemoteDebugNodePortTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("remoteDebugNodePortEnabled", true));

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithRemoteDebugNodePortFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("remoteDebugNodePortEnabled", false));

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo("false"));
  }

  // --------------- suspendOnDebugStartup

  @Test
  public void whenSuspendOnDebugStartupTrue_createdMapContainsValue() {
    operatorValues.suspendOnDebugStartup("true");

    assertThat(operatorValues.createMap(), hasEntry("suspendOnDebugStartup", true));
  }

  @Test
  public void whenSuspendOnDebugStartupFalse_createdMapContainsValue() {
    operatorValues.suspendOnDebugStartup("false");

    assertThat(operatorValues.createMap(), hasEntry("suspendOnDebugStartup", false));
  }

  @Test
  public void whenSuspendOnDebugStartupNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("suspendOnDebugStartup")));
  }

  @Test
  public void whenCreatedFromMapWithoutSuspendOnDebugStartup_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getSuspendOnDebugStartup(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithSuspendOnDebugStartupTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("suspendOnDebugStartup", true));

    assertThat(values.getSuspendOnDebugStartup(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithSuspendOnDebugStartupFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("suspendOnDebugStartup", false));

    assertThat(values.getSuspendOnDebugStartup(), equalTo("false"));
  }

  // --------------- elkIntegrationEnabled

  @Test
  public void whenElkIntegrationEnabledTrue_createdMapContainsValue() {
    operatorValues.elkIntegrationEnabled("true");

    assertThat(operatorValues.createMap(), hasEntry("elkIntegrationEnabled", true));
  }

  @Test
  public void whenElkIntegrationEnabledFalse_createdMapContainsValue() {
    operatorValues.elkIntegrationEnabled("false");

    assertThat(operatorValues.createMap(), hasEntry("elkIntegrationEnabled", false));
  }

  @Test
  public void whenElkIntegrationEnabledNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("elkIntegrationEnabled")));
  }

  @Test
  public void whenCreatedFromMapWithoutElkIntegrationEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getElkIntegrationEnabled(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithElkIntegrationTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("elkIntegrationEnabled", true));

    assertThat(values.getElkIntegrationEnabled(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithElkIntegrationFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("elkIntegrationEnabled", false));

    assertThat(values.getElkIntegrationEnabled(), equalTo("false"));
  }

  // ----- externalRestHttpPort

  @Test
  public void whenExternalRestHttpsPortSet_createdMapContainsValue() {
    operatorValues.externalRestHttpsPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("externalRestHttpsPort", intValue));
  }

  @Test
  public void whenExternalRestHttpsPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("externalRestHttpsPort")));
  }

  @Test
  public void externalRestHttpsPortIsGettableStringValue() {
    operatorValues.externalRestHttpsPort(stringValue);

    assertThat(operatorValues.getExternalRestHttpsPort(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutExternalRestHttpsPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalRestHttpsPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithExternalRestHttpsPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalRestHttpsPort", intValue));

    assertThat(values.getExternalRestHttpsPort(), equalTo(stringValue));
  }

  // ----- internalDebugHttpPort

  @Test
  public void whenInternalDebugHttpPortSet_createdMapContainsValue() {
    operatorValues.internalDebugHttpPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("internalDebugHttpPort", intValue));
  }

  @Test
  public void whenInternalDebugHttpPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("internalDebugHttpPort")));
  }

  @Test
  public void internalDebugHttpPortIsGettableStringValue() {
    operatorValues.internalDebugHttpPort(stringValue);

    assertThat(operatorValues.getInternalDebugHttpPort(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutInternalDebugHttpPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getInternalDebugHttpPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithInternalDebugHttpPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("internalDebugHttpPort", intValue));

    assertThat(values.getInternalDebugHttpPort(), equalTo(stringValue));
  }

  // ----- externalDebugHttpPort

  @Test
  public void whenTargetNamespacesNotDefined_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("domainNamespaces")));
  }

  @Test
  public void whenSingleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.domainNamespaces(stringValue);

    assertThat(getDomainNamespaces(), hasItem(stringValue));
  }

  @SuppressWarnings("unchecked")
  private List<String> getDomainNamespaces() {
    return (List<String>) operatorValues.createMap().get("domainNamespaces");
  }

  @Test
  public void whenMultipleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.domainNamespaces("aaa,bbb");

    assertThat(getDomainNamespaces(), hasItems("aaa", "bbb"));
  }

  @Test
  public void whenCreatedFromMapWithoutDomainNamespaces_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getDomainNamespaces(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithSingleNamespace_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("domainNamespaces", ImmutableList.of("namespace1")));

    assertThat(values.getDomainNamespaces(), equalTo("namespace1"));
  }

  @Test
  public void whenCreatedFromMapWithMultipleNamespaces_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            ImmutableMap.of("domainNamespaces", ImmutableList.of("namespace1", "namespace2")));

    assertThat(values.getDomainNamespaces(), equalTo("namespace1,namespace2"));
  }

  // ----- externalDebugHttpPort

  @Test
  public void whenExternalDebugHttpPortSet_createdMapContainsValue() {
    operatorValues.externalDebugHttpPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("externalDebugHttpPort", intValue));
  }

  @Test
  public void whenExternalDebugHttpPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("externalDebugHttpPort")));
  }

  @Test
  public void externalDebugHttpPortIsGettableStringValue() {
    operatorValues.externalDebugHttpPort(stringValue);

    assertThat(operatorValues.getExternalDebugHttpPort(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutExternalDebugHttpPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalDebugHttpPort(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithExternalDebugHttpPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalDebugHttpPort", intValue));

    assertThat(values.getExternalDebugHttpPort(), equalTo(stringValue));
  }

  // --------------- dedicated

  @Test
  public void whenDedicatedTrue_createdMapContainsValue() {
    operatorValues.dedicated("true");

    assertThat(operatorValues.createMap(), hasEntry("dedicated", true));
  }

  @Test
  public void whenDedicatedFalse_createdMapContainsValue() {
    operatorValues.dedicated("false");

    assertThat(operatorValues.createMap(), hasEntry("dedicated", false));
  }

  @Test
  public void whenDedicatedNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("dedicated")));
  }

  @Test
  public void whenCreatedFromMapWithoutDedicated_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getDedicated(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithDedicatedTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("dedicated", true));

    assertThat(values.getDedicated(), equalTo("true"));
  }

  @Test
  public void whenCreatedFromMapWithDedicatedFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("dedicated", false));

    assertThat(values.getDedicated(), equalTo("false"));
  }

  @Test
  public void whenCreatedFromMap_hasSpecifiedValues() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            new ImmutableMap.Builder<String, Object>()
                .put("serviceAccount", "test-account")
                .put("image", "test-image")
                .put("javaLoggingLevel", "FINE")
                .build());

    assertThat(values.getServiceAccount(), equalTo("test-account"));
    assertThat(values.getWeblogicOperatorImage(), equalTo("test-image"));
    assertThat(values.getJavaLoggingLevel(), equalTo("FINE"));
  }
}
