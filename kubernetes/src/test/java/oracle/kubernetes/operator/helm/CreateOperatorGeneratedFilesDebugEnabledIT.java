// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import oracle.kubernetes.operator.create.CreateOperatorGeneratedFilesDebugEnabledTestBase;
import org.junit.BeforeClass;

public class CreateOperatorGeneratedFilesDebugEnabledIT
    extends CreateOperatorGeneratedFilesDebugEnabledTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineOperatorYamlFactory(new HelmOperatorYamlFactory());
  }
}
