// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to manage (i.e. create and removeO the user projects directory that the generated yaml
 * files are stored in
 */
public class UserProjects {

  private Path path;

  private UserProjects() throws Exception {
    path = Files.createTempDirectory("test-user-projects");
  }

  public static UserProjects createUserProjectsDirectory() throws Exception {
    return new UserProjects();
  }

  public Path getPath() {
    return this.path;
  }

  protected void remove() throws Exception {
    final List<Path> pathsToDelete = getContents(path);
    for (Path p : pathsToDelete) {
      Files.deleteIfExists(p);
    }
  }

  public List<Path> getContents(Path path) throws Exception {
    return Files.walk(path).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
  }
}
