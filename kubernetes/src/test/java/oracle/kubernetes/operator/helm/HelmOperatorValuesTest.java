// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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

class HelmOperatorValuesTest {

  private final int intValue = getRandomInt();
  private final String stringValue = Integer.toString(intValue);
  private final HelmOperatorValues operatorValues = new HelmOperatorValues();

  private static int getRandomInt() {
    return (int) (1000000 * Math.random());
  }

  @Test
  void whenServiceAccountSet_createdMapContainsValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("serviceAccount", stringValue));
  }

  @Test
  void serviceAccountIsGettableStringValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutServiceAccount_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getServiceAccount(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithServiceAccount_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("serviceAccount", stringValue));

    assertThat(values.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  void whenWeblogicOperatorImageSet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("image", stringValue));
  }

  @Test
  void weblogicOperatorImageIsGettableStringValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.getWeblogicOperatorImage(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutImage_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImage(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithImage_hasSpecifiedValue() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of("image", stringValue));

    assertThat(values.getWeblogicOperatorImage(), equalTo(stringValue));
  }

  // ----- javaLoggingLevel

  @Test
  void whenJavaLoggingLevelSet_createdMapContainsValue() {
    operatorValues.javaLoggingLevel(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("javaLoggingLevel", stringValue));
  }

  @Test
  void whenJavaLoggingLevelNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("javaLoggingLevel")));
  }

  @Test
  void javaLoggingLevelIsGettableStringValue() {
    operatorValues.javaLoggingLevel(stringValue);

    assertThat(operatorValues.getJavaLoggingLevel(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutJavaLoggingLevel_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getJavaLoggingLevel(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithJavaLoggingLevel_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("javaLoggingLevel", stringValue));

    assertThat(values.getJavaLoggingLevel(), equalTo(stringValue));
  }

  // ------------ operatorNamespace

  @Test
  void whenWeblogicOperatorNamespaceSet_createdMapContainsValue() {
    operatorValues.namespace(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("operatorNamespace", stringValue));
  }

  @Test
  void weblogicOperatorNamespaceIsGettableStringValue() {
    operatorValues.namespace(stringValue);

    assertThat(operatorValues.getNamespace(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutWeblogicOperatorNamespace_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getNamespace(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithWeblogicOperatorNamespace_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("operatorNamespace", stringValue));

    assertThat(values.getNamespace(), equalTo(stringValue));
  }

  @Test
  void whenWeblogicOperatorImagePullPolicySet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImagePullPolicy("IfNotPresent");

    assertThat(operatorValues.createMap(),
        hasEntry("imagePullPolicy", "IfNotPresent"));
  }

  @Test
  void weblogicOperatorImagePullPolicyIsExpectedEnumValue() {
    operatorValues.weblogicOperatorImagePullPolicy("Always");

    assertThat(operatorValues.getWeblogicOperatorImagePullPolicy(), equalTo("Always"));
  }

  @Test
  void whenCreatedFromMapWithoutImagePullPolicy_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo("Never"));
  }

  @Test
  void whenCreatedFromMapWithImagePullPolicy_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("imagePullPolicy", "Always"));

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo("Always"));
  }

  @Test
  void whenCreatedFromMapWithExternalRestEnabled_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalRestEnabled", true));

    assertThat(values.getExternalRestEnabled(), equalTo("true"));
  }

  @Test
  void whenCreatedFromMapWithoutExternalRestEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalRestEnabled(), equalTo(""));
  }

  // --------------- remoteDebugNodePortEnabled

  @Test
  void whenRemoteDebugNodePortEnabledTrue_createdMapContainsValue() {
    operatorValues.remoteDebugNodePortEnabled("true");

    assertThat(operatorValues.createMap(), hasEntry("remoteDebugNodePortEnabled", true));
  }

  @Test
  void whenRemoteDebugNodePortEnabledFalse_createdMapContainsValue() {
    operatorValues.remoteDebugNodePortEnabled("false");

    assertThat(operatorValues.createMap(), hasEntry("remoteDebugNodePortEnabled", false));
  }

  @Test
  void whenRemoteDebugNodePortEnabledNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("remoteDebugNodePortEnabled")));
  }

  @Test
  void whenCreatedFromMapWithoutRemoteDebugNodePortEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithRemoteDebugNodePortTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("remoteDebugNodePortEnabled", true));

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo("true"));
  }

  @Test
  void whenCreatedFromMapWithRemoteDebugNodePortFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("remoteDebugNodePortEnabled", false));

    assertThat(values.getRemoteDebugNodePortEnabled(), equalTo("false"));
  }

  // --------------- suspendOnDebugStartup

  @Test
  void whenSuspendOnDebugStartupTrue_createdMapContainsValue() {
    operatorValues.suspendOnDebugStartup("true");

    assertThat(operatorValues.createMap(), hasEntry("suspendOnDebugStartup", true));
  }

  @Test
  void whenSuspendOnDebugStartupFalse_createdMapContainsValue() {
    operatorValues.suspendOnDebugStartup("false");

    assertThat(operatorValues.createMap(), hasEntry("suspendOnDebugStartup", false));
  }

  @Test
  void whenSuspendOnDebugStartupNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("suspendOnDebugStartup")));
  }

  @Test
  void whenCreatedFromMapWithoutSuspendOnDebugStartup_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getSuspendOnDebugStartup(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithSuspendOnDebugStartupTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("suspendOnDebugStartup", true));

    assertThat(values.getSuspendOnDebugStartup(), equalTo("true"));
  }

  @Test
  void whenCreatedFromMapWithSuspendOnDebugStartupFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("suspendOnDebugStartup", false));

    assertThat(values.getSuspendOnDebugStartup(), equalTo("false"));
  }

  // --------------- elkIntegrationEnabled

  @Test
  void whenElkIntegrationEnabledTrue_createdMapContainsValue() {
    operatorValues.elkIntegrationEnabled("true");

    assertThat(operatorValues.createMap(), hasEntry("elkIntegrationEnabled", true));
  }

  @Test
  void whenElkIntegrationEnabledFalse_createdMapContainsValue() {
    operatorValues.elkIntegrationEnabled("false");

    assertThat(operatorValues.createMap(), hasEntry("elkIntegrationEnabled", false));
  }

  @Test
  void whenElkIntegrationEnabledNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("elkIntegrationEnabled")));
  }

  @Test
  void whenCreatedFromMapWithoutElkIntegrationEnabled_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getElkIntegrationEnabled(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithElkIntegrationTrue_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("elkIntegrationEnabled", true));

    assertThat(values.getElkIntegrationEnabled(), equalTo("true"));
  }

  @Test
  void whenCreatedFromMapWithElkIntegrationFalse_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("elkIntegrationEnabled", false));

    assertThat(values.getElkIntegrationEnabled(), equalTo("false"));
  }

  // ----- externalRestHttpPort

  @Test
  void whenExternalRestHttpsPortSet_createdMapContainsValue() {
    operatorValues.externalRestHttpsPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("externalRestHttpsPort", intValue));
  }

  @Test
  void whenExternalRestHttpsPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("externalRestHttpsPort")));
  }

  @Test
  void externalRestHttpsPortIsGettableStringValue() {
    operatorValues.externalRestHttpsPort(stringValue);

    assertThat(operatorValues.getExternalRestHttpsPort(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutExternalRestHttpsPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalRestHttpsPort(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithExternalRestHttpsPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalRestHttpsPort", intValue));

    assertThat(values.getExternalRestHttpsPort(), equalTo(stringValue));
  }

  // ----- internalDebugHttpPort

  @Test
  void whenInternalDebugHttpPortSet_createdMapContainsValue() {
    operatorValues.internalDebugHttpPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("internalDebugHttpPort", intValue));
  }

  @Test
  void whenInternalDebugHttpPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("internalDebugHttpPort")));
  }

  @Test
  void internalDebugHttpPortIsGettableStringValue() {
    operatorValues.internalDebugHttpPort(stringValue);

    assertThat(operatorValues.getInternalDebugHttpPort(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutInternalDebugHttpPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getInternalDebugHttpPort(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithInternalDebugHttpPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("internalDebugHttpPort", intValue));

    assertThat(values.getInternalDebugHttpPort(), equalTo(stringValue));
  }

  // ----- externalDebugHttpPort

  @Test
  void whenTargetNamespacesNotDefined_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("domainNamespaces")));
  }

  @Test
  void whenSingleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.domainNamespaces(stringValue);

    assertThat(getDomainNamespaces(), hasItem(stringValue));
  }

  @SuppressWarnings("unchecked")
  private List<String> getDomainNamespaces() {
    return (List<String>) operatorValues.createMap().get("domainNamespaces");
  }

  @Test
  void whenMultipleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.domainNamespaces("aaa,bbb");

    assertThat(getDomainNamespaces(), hasItems("aaa", "bbb"));
  }

  @Test
  void whenCreatedFromMapWithoutDomainNamespaces_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getDomainNamespaces(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithSingleNamespace_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("domainNamespaces", ImmutableList.of("namespace1")));

    assertThat(values.getDomainNamespaces(), equalTo("namespace1"));
  }

  @Test
  void whenCreatedFromMapWithMultipleNamespaces_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            ImmutableMap.of("domainNamespaces", ImmutableList.of("namespace1", "namespace2")));

    assertThat(values.getDomainNamespaces(), equalTo("namespace1,namespace2"));
  }

  // ----- externalDebugHttpPort

  @Test
  void whenExternalDebugHttpPortSet_createdMapContainsValue() {
    operatorValues.externalDebugHttpPort(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("externalDebugHttpPort", intValue));
  }

  @Test
  void whenExternalDebugHttpPortNotSet_createdMapLacksValue() {
    assertThat(operatorValues.createMap(), not(hasKey("externalDebugHttpPort")));
  }

  @Test
  void externalDebugHttpPortIsGettableStringValue() {
    operatorValues.externalDebugHttpPort(stringValue);

    assertThat(operatorValues.getExternalDebugHttpPort(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMapWithoutExternalDebugHttpPort_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalDebugHttpPort(), equalTo(""));
  }

  @Test
  void whenCreatedFromMapWithExternalDebugHttpPort_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalDebugHttpPort", intValue));

    assertThat(values.getExternalDebugHttpPort(), equalTo(stringValue));
  }

  @Test
  void whenCreatedFromMap_hasSpecifiedValues() {
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
