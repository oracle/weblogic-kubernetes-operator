// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.utils.OperatorValues.EXTERNAL_REST_OPTION_CUSTOM_CERT;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;

public class HelmOperatorValuesTest {
  private final int intValue = getRandomInt();
  private final String stringValue = Integer.toString(intValue);

  private static int getRandomInt() {
    return (int) (1000000 * Math.random());
  }

  private final HelmOperatorValues operatorValues = new HelmOperatorValues();

  @Test
  public void whenServiceAccountSet_createdMapContainsValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("operatorServiceAccount", stringValue));
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
        new HelmOperatorValues(ImmutableMap.of("operatorServiceAccount", stringValue));

    assertThat(values.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  public void whenOperatorImageSet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("operatorImage", stringValue));
  }

  @Test
  public void OperatorImageIsGettableStringValue() {
    operatorValues.weblogicOperatorImage(stringValue);

    assertThat(operatorValues.getWeblogicOperatorImage(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutOperatorImage_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImage(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithOperatorImage_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("operatorImage", stringValue));

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
  public void JavaLoggingLevelIsGettableStringValue() {
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
  public void WeblogicOperatorNamespaceIsGettableStringValue() {
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
  public void whenNothingSet_createsMapWithInternalCerts() {
    assertThat(
        operatorValues.createMap(),
        both(hasKey("internalOperatorCert")).and(hasKey("internalOperatorKey")));
  }

  @Test
  public void whenWeblogicOperatorImagePullPolicySet_createdMapContainsValue() {
    operatorValues.weblogicOperatorImagePullPolicy(stringValue);

    assertThat(operatorValues.createMap(), hasEntry("operatorImagePullPolicy", stringValue));
  }

  @Test
  public void WeblogicOperatorImagePullPolicyIsGettableStringValue() {
    operatorValues.weblogicOperatorImagePullPolicy(stringValue);

    assertThat(operatorValues.getWeblogicOperatorImagePullPolicy(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithoutWeblogicOperatorImagePullPolicy_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithWeblogicOperatorImagePullPolicy_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("operatorImagePullPolicy", stringValue));

    assertThat(values.getWeblogicOperatorImagePullPolicy(), equalTo(stringValue));
  }

  @Test
  public void whenCreatedFromMapWithExternalRestOption_hasSpecifiedValue() {
    String option = EXTERNAL_REST_OPTION_CUSTOM_CERT;
    HelmOperatorValues values =
        new HelmOperatorValues(ImmutableMap.of("externalRestOption", option));

    assertThat(values.getExternalRestOption(), equalTo(option));
  }

  @Test
  public void whenCreatedFromMapWithoutExternalRestOption_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getExternalRestOption(), equalTo(""));
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
  public void InternalDebugHttpPortIsGettableStringValue() {
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
    assertThat(operatorValues.createMap(), not(hasKey("domainsNamespaces")));
  }

  @Test
  public void whenSingleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.targetNamespaces(stringValue);

    assertThat(getDomainsNamespaces(), hasItem(stringValue));
  }

  @SuppressWarnings("unchecked")
  private List<String> getDomainsNamespaces() {
    return (List<String>) operatorValues.createMap().get("domainsNamespaces");
  }

  @Test
  public void whenMultipleTargetNamespaceDefined_createdMapContainsValue() {
    operatorValues.targetNamespaces("aaa,bbb");

    assertThat(getDomainsNamespaces(), hasItems("aaa", "bbb"));
  }

  @Test
  public void whenCreatedFromMapWithoutDomainsNamespaces_hasEmptyString() {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(values.getTargetNamespaces(), equalTo(""));
  }

  @Test
  public void whenCreatedFromMapWithSingleNamespace_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            ImmutableMap.of("domainsNamespaces", ImmutableList.of("namespace1")));

    assertThat(values.getTargetNamespaces(), equalTo("namespace1"));
  }

  @Test
  public void whenCreatedFromMapWithMultipleNamespaces_hasSpecifiedValue() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            ImmutableMap.of("domainsNamespaces", ImmutableList.of("namespace1", "namespace2")));

    assertThat(values.getTargetNamespaces(), equalTo("namespace1,namespace2"));
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
  public void ExternalDebugHttpPortIsGettableStringValue() {
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

  @Test
  public void whenCreatedFromMap_hasSpecifiedValues() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            new ImmutableMap.Builder<String, Object>()
                .put("operatorServiceAccount", "test-account")
                .put("operatorImage", "test-image")
                .put("javaLoggingLevel", "FINE")
                .build());

    assertThat(values.getServiceAccount(), equalTo("test-account"));
    assertThat(values.getWeblogicOperatorImage(), equalTo("test-image"));
    assertThat(values.getJavaLoggingLevel(), equalTo("FINE"));
  }
}
