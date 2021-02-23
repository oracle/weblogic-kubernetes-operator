// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.helpers.ConfigMapHelperTest.SCRIPT_NAMES;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FileGroupReaderIT {

  private final FileGroupReader scriptReader = ConfigMapHelper.getScriptReader();

  @Test
  public void afterLoadScriptsFromClasspath_haveScriptNamesAsKeys() {
    Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
    assertThat(scripts.keySet(), containsInAnyOrder(SCRIPT_NAMES));
  }
}
