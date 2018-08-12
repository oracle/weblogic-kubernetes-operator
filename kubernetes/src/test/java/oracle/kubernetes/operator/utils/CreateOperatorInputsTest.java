// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CreateOperatorInputsTest {
  private final String stringValue = Double.toHexString(Math.random());

  private OperatorValues operatorValues = new CreateOperatorInputs();

  @Test
  public void versionIsSettableStringValue() throws IllegalAccessException {
    operatorValues.version(stringValue);

    assertThat(getStringFieldValue("version"), equalTo(stringValue));
  }

  @Test // needed for Yaml parsing
  public void versionIsGettableStringValue() {
    operatorValues.version(stringValue);

    assertThat(operatorValues.getVersion(), equalTo(stringValue));
  }

  @Test
  public void serviceAccountIsSettableStringValue() throws IllegalAccessException {
    operatorValues.serviceAccount(stringValue);

    assertThat(getStringFieldValue("serviceAccount"), equalTo(stringValue));
  }

  @Test
  public void serviceAccountIsGettableStringValue() {
    operatorValues.serviceAccount(stringValue);

    assertThat(operatorValues.getServiceAccount(), equalTo(stringValue));
  }

  @Test
  public void namespaceIsSettableStringValue() throws IllegalAccessException {
    operatorValues.namespace(stringValue);

    assertThat(getStringFieldValue("namespace"), equalTo(stringValue));
  }

  @Test
  public void namespaceIsGettableStringValue() {
    operatorValues.namespace(stringValue);

    assertThat(operatorValues.getNamespace(), equalTo(stringValue));
  }

  private String getStringFieldValue(String fieldName) throws IllegalAccessException {
    return (String) FieldUtils.getValue(operatorValues, fieldName);
  }
}
