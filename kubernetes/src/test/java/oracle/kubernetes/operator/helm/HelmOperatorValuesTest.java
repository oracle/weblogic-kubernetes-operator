// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class HelmOperatorValuesTest {

  private final int intValue = getRandomInt();
  private final String stringValue = Integer.toString(intValue);
  private final HelmOperatorValues operatorValues = new HelmOperatorValues();

  private static int getRandomInt() {
    return (int) (1000000 * Math.random());
  }


  enum HelmStringValue {
    SERVICE_ACCOUNT("serviceAccount", HelmOperatorValues::serviceAccount, HelmOperatorValues::getServiceAccount),
    OPERATOR_IMAGE("image", HelmOperatorValues::weblogicOperatorImage, HelmOperatorValues::getWeblogicOperatorImage),
    LOGGING_LEVEL("javaLoggingLevel", HelmOperatorValues::javaLoggingLevel, HelmOperatorValues::getJavaLoggingLevel),
    OPERATOR_NAMESPACE("operatorNamespace", HelmOperatorValues::namespace, HelmOperatorValues::getNamespace),
    IMAGE_PULL_POLICY("imagePullPolicy",
        HelmOperatorValues::weblogicOperatorImagePullPolicy,
        HelmOperatorValues::getWeblogicOperatorImagePullPolicy,
        "Never"),
    JVM_OPTIONS("jvmOptions",
        HelmOperatorValues::jvmOptions,
        HelmOperatorValues::getJvmOptions,
        "-XX:MaxRAMPercentage=70");

    private final String name;
    private final String defaultValue;
    private final BiConsumer<HelmOperatorValues,String> setter;
    private final Function<HelmOperatorValues,Object> getter;

    HelmStringValue(String name,
                    BiConsumer<HelmOperatorValues, String> setter,
                    Function<HelmOperatorValues, Object> getter,
                    String defaultValue) {
      this.name = name;
      this.setter = setter;
      this.getter = getter;
      this.defaultValue = defaultValue;
    }

    HelmStringValue(String name,
                    BiConsumer<HelmOperatorValues, String> setter,
                    Function<HelmOperatorValues, Object> getter) {
      this(name, setter, getter, "");
    }
  }

  @ParameterizedTest
  @EnumSource(HelmStringValue.class)
  void whenValueWithoutDefaultNotSet_createdMapLacksValue(HelmStringValue value) {
    Assume.assumeThat(value.defaultValue, is(emptyString()));

    assertThat(operatorValues.createMap(), not(hasKey(value.name)));
  }

  @ParameterizedTest
  @EnumSource(HelmStringValue.class)
  void whenValueSet_createdMapContainsValue(HelmStringValue value) {
    value.setter.accept(operatorValues, stringValue);

    assertThat(operatorValues.createMap(), hasEntry(value.name, stringValue));
  }

  @ParameterizedTest
  @EnumSource(HelmStringValue.class)
  void valueIsGettableStringValue(HelmStringValue value) {
    value.setter.accept(operatorValues, stringValue);

    assertThat(value.getter.apply(operatorValues), equalTo(stringValue));
  }

  @ParameterizedTest
  @EnumSource(HelmStringValue.class)
  void whenCreatedFromMapWithoutValue_hasDefaultValue(HelmStringValue value) {
    final HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(value.getter.apply(values), equalTo(value.defaultValue));
  }

  @ParameterizedTest
  @EnumSource(HelmStringValue.class)
  void whenCreatedFromMapWithValue_hasSpecifiedValue(HelmStringValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of(value.name, stringValue));

    assertThat(value.getter.apply(values), equalTo(stringValue));
  }


  enum HelmBooleanValue {
    EXTERNAL_REST_ENABLED("externalRestEnabled",
        HelmOperatorValues::externalRestEnabled, HelmOperatorValues::getExternalRestEnabled),
    REMOTE_DEBUG_PORT_ENABLED("remoteDebugNodePortEnabled",
        HelmOperatorValues::remoteDebugNodePortEnabled, HelmOperatorValues::getRemoteDebugNodePortEnabled),
    SUSPEND_ON_DEBUG_STARTUP("suspendOnDebugStartup",
        HelmOperatorValues::suspendOnDebugStartup, HelmOperatorValues::getSuspendOnDebugStartup),
    ELK_INTEGRATION_ENABLED("elkIntegrationEnabled",
        HelmOperatorValues::elkIntegrationEnabled, HelmOperatorValues::getElkIntegrationEnabled);

    private final String name;
    private final BiConsumer<HelmOperatorValues,String> setter;
    private final Function<HelmOperatorValues,Object> getter;

    HelmBooleanValue(String name,
                     BiConsumer<HelmOperatorValues, String> setter,
                     Function<HelmOperatorValues, Object> getter) {
      this.name = name;
      this.setter = setter;
      this.getter = getter;
    }
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenBooleanValueNotSet_createdMapLacksValue(HelmBooleanValue value) {
    assertThat(operatorValues.createMap(), not(hasKey(value.name)));
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenBooleanValueSetTrue_createdMapContainsValue(HelmBooleanValue value) {
    value.setter.accept(operatorValues, "true");

    assertThat(operatorValues.createMap(), hasEntry(value.name, true));
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenBooleanValueSetFalse_createdMapContainsValue(HelmBooleanValue value) {
    value.setter.accept(operatorValues, "false");

    assertThat(operatorValues.createMap(), hasEntry(value.name, false));
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenCreateFromMapWithoutValue_valueIsEmptyString(HelmBooleanValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(value.getter.apply(values), equalTo(""));
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenCreateFromMapWithTrueValue_valueIsTrue(HelmBooleanValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of(value.name, true));

    assertThat(value.getter.apply(values), equalTo("true"));
  }

  @ParameterizedTest
  @EnumSource(HelmBooleanValue.class)
  void whenCreateFromMapWithFalseValue_valueIsFalse(HelmBooleanValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of(value.name, false));

    assertThat(value.getter.apply(values), equalTo("false"));
  }

  enum HelmIntegerValue {
    EXTERNAL_REST_PORT("externalRestHttpsPort",
        HelmOperatorValues::externalRestHttpsPort,
        HelmOperatorValues::getExternalRestHttpsPort),
    INTERNAL_DEBUG_HTTP_PORT("internalDebugHttpPort",
        HelmOperatorValues::internalDebugHttpPort,
        HelmOperatorValues::getInternalDebugHttpPort),
    EXTERNAL_DEBUG_HTTP_PORT("externalDebugHttpPort",
        HelmOperatorValues::externalDebugHttpPort,
        HelmOperatorValues::getExternalDebugHttpPort);

    private final String name;
    private final BiConsumer<HelmOperatorValues,String> setter;
    private final Function<HelmOperatorValues,String> getter;
    private final String defaultValue;

    HelmIntegerValue(String name, BiConsumer<HelmOperatorValues, String> setter,
                     Function<HelmOperatorValues, String> getter) {
      this(name, setter, getter, "");
    }

    HelmIntegerValue(String name, BiConsumer<HelmOperatorValues, String> setter,
                     Function<HelmOperatorValues, String> getter,
                     String defaultValue) {
      this.name = name;
      this.setter = setter;
      this.getter = getter;
      this.defaultValue = defaultValue;
    }
  }

  @ParameterizedTest
  @EnumSource(HelmIntegerValue.class)
  void whenValueWithoutDefaultNotSet_createdMapLacksIntegerValue(HelmIntegerValue value) {
    Assume.assumeThat(value.defaultValue, is(emptyString()));

    assertThat(new HelmOperatorValues().createMap(), not(hasKey(value.name)));
  }

  @ParameterizedTest
  @EnumSource(HelmIntegerValue.class)
  void whenValueSet_createdMapContainsIntegerValue(HelmIntegerValue value) {
    value.setter.accept(operatorValues, stringValue);

    assertThat(operatorValues.createMap(), hasEntry(value.name, intValue));
  }

  @ParameterizedTest
  @EnumSource(HelmIntegerValue.class)
  void valueIsGettableIntegerStringValue(HelmIntegerValue value) {
    value.setter.accept(operatorValues, stringValue);

    assertThat(value.getter.apply(operatorValues), equalTo(stringValue));
  }

  @ParameterizedTest
  @EnumSource(HelmIntegerValue.class)
  void whenCreatedFromMapWithoutValue_hasDefaultIntegerStringValue(HelmIntegerValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of());

    assertThat(value.getter.apply(values), equalTo(value.defaultValue));
  }

  @ParameterizedTest
  @EnumSource(HelmIntegerValue.class)
  void whenCreatedFromMapWithValue_hasSpecifiedIntegerValue(HelmIntegerValue value) {
    HelmOperatorValues values = new HelmOperatorValues(ImmutableMap.of(value.name, intValue));

    assertThat(value.getter.apply(values), equalTo(stringValue));
  }


  // ----- domain namespaces (list-valued)

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
