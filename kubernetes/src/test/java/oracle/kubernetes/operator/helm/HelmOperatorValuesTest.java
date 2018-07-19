// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static oracle.kubernetes.operator.utils.OperatorValues.EXTERNAL_REST_OPTION_CUSTOM_CERT;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.hamcrest.Matcher;
import org.junit.Test;

public class HelmOperatorValuesTest {
  @Test
  public void whenNothingSet_createsMapWithInternalCerts() {
    assertThat(
        new HelmOperatorValues().createMap(),
        both(hasKey("internalOperatorCert")).and(hasKey("internalOperatorKey")));
  }

  @Test
  public void whenValuesSet_createMapWithSetValues() {
    HelmOperatorValues operatorValues = new HelmOperatorValues();
    operatorValues.setupExternalRestCustomCert();

    assertThat(operatorValues.createMap(), hasExpectedEntries());
  }

  private Matcher<? super Map<?, ?>> hasExpectedEntries() {
    return allOf(
        hasEntry("externalRestHttpsPort", "30070"),
        hasEntry("externalRestOption", EXTERNAL_REST_OPTION_CUSTOM_CERT),
        hasKey("externalOperatorCert"),
        hasKey("externalOperatorKey"));
  }

  @Test
  public void whenCreatedFromMap_hasSpecifiedValues() {
    HelmOperatorValues values =
        new HelmOperatorValues(
            new ImmutableMap.Builder<String, Object>()
                .put("serviceAccount", "test-account")
                .put("weblogicOperatorImage", "test-image")
                .put("javaLoggingLevel", "FINE")
                .build());

    assertThat(values.getServiceAccount(), equalTo("test-account"));
    assertThat(values.getWeblogicOperatorImage(), equalTo("test-image"));
    assertThat(values.getJavaLoggingLevel(), equalTo("FINE"));
  }
}
