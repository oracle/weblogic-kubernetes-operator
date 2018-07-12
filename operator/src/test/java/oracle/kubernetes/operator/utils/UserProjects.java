// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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

  public static UserProjects createUserProjectsDirectory() throws Exception {
    return new UserProjects();
  }

  private UserProjects() throws Exception {
    path = Files.createTempDirectory("test-user-projects");
  }

  public Path getPath() {
    return this.path;
  }

  public void remove() throws Exception {
    final List<Path> pathsToDelete = getContents(path);
    for (Path p : pathsToDelete) {
      Files.deleteIfExists(p);
    }
  }

  public List<Path> getContents(Path path) throws Exception {
    return Files.walk(path).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
  }
}
