// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to manage (i.e. create and removeO the user projects directory that
 * the generated yaml files are stored in
 */
public class UserProjects {

  private Path path;

  public static UserProjects createUserProjectsDirectory() throws Exception {
    return new UserProjects();
  }

  private UserProjects() throws Exception {
    path = Files.createTempDirectory("test-user-projects");
  }

  public Path getPath() { return this.path; }

  public void remove() throws Exception {
    final List<Path> pathsToDelete = Files.walk(getPath()).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    for(Path p : pathsToDelete) {
      Files.deleteIfExists(p);
    }
  }
}
