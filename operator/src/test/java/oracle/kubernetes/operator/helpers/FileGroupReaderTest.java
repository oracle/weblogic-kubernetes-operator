// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FileGroupReaderTest {

  private static final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();

  @Test
  public void loadFilesFromMemory() throws IOException {
    fileSystem.defineFile("group/a.b", "1234");
    fileSystem.defineFile("group/x/c.d", "5678");

    Path p = fileSystem.getPath("group");
    Map<String, String> map = FileGroupReader.loadContents(p);

    assertThat(map, hasEntry("a.b", "1234"));
    assertThat(map, hasEntry("c.d", "5678"));
  }
}
