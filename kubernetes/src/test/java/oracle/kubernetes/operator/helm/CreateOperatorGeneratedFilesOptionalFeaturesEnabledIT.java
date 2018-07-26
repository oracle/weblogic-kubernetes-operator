// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import oracle.kubernetes.operator.create.CreateOperatorGeneratedFilesOptionalFeaturesEnabledTestBase;
import org.junit.BeforeClass;
import org.junit.Ignore;

@Ignore("certificate processing failing")
public class CreateOperatorGeneratedFilesOptionalFeaturesEnabledIT
    extends CreateOperatorGeneratedFilesOptionalFeaturesEnabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineOperatorYamlFactory(new HelmOperatorYamlFactory());
  }
}
