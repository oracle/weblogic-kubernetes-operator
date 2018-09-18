// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import oracle.kubernetes.operator.create.CreateDomainGeneratedFilesVoyagerTestBase;
import org.junit.BeforeClass;

public class CreateDomainGeneratedFilesVoyagerIT extends CreateDomainGeneratedFilesVoyagerTestBase {

  @BeforeClass
  public static void setup() throws Exception {
    defineDomainYamlFactory(new HelmDomainYamlFactory());
  }
}
