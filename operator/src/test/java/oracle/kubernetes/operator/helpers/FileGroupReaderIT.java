// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.helpers.ConfigMapHelperTest.SCRIPT_NAMES;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.Map;
import org.junit.Test;

public class FileGroupReaderIT {

  private final FileGroupReader scriptReader = ConfigMapHelper.getScriptReader();

  @Test
  public void afterLoadScriptsFromClasspath_haveScriptNamesAsKeys() {
    Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
    assertThat(scripts.keySet(), containsInAnyOrder(SCRIPT_NAMES));
  }
}
