// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import org.junit.Test;

import static oracle.kubernetes.operator.helpers.ConfigMapHelperTest.SCRIPT_NAMES;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FileGroupReaderTest {

  private static InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final FileGroupReader scriptReader = ConfigMapHelper.getScriptReader();

  @Test
  public void afterLoadScriptsFromClasspath_haveScriptNamesAsKeys() {
    Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
    assertThat(scripts.keySet(), containsInAnyOrder(SCRIPT_NAMES));
  }

  @Test
  public void loadFilesFromMemory() throws IOException {
    fileSystem.defineFile("group/a.b", "1234");
    fileSystem.defineFile("group/x/c.d", "5678");

    Path p = fileSystem.getPath("group");
    Map<String, String> map = FileGroupReader.loadContents(p);

    assertThat(map, hasEntry("group/a.b", "1234"));
    assertThat(map, hasEntry("group/x/c.d", "5678"));
  }
}
