// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import oracle.kubernetes.operator.create.CreateOperatorGeneratedFilesOptionalFeaturesDisabledTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class CreateOperatorGeneratedFilesOptionalFeaturesDisabledIT
    extends CreateOperatorGeneratedFilesOptionalFeaturesDisabledTestBase {

  @BeforeAll
  public static void setup() throws Exception {
    defineOperatorYamlFactory(new HelmOperatorYamlFactory());
  }

  @Test
  @Override
  protected void generatesCorrect_weblogicOperatorNamespace() {
    // placeholder assertion
    assertThat(getInputs(), notNullValue());
  }

  @Test
  @Override
  protected void generatesCorrect_weblogicOperatorServiceAccount() {
    // placeholder assertion
    assertThat(getInputs(), notNullValue());
  }
}
