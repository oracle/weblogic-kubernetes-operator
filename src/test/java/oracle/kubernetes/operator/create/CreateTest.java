// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.create.ExecResult;
import org.junit.After;
import org.junit.Before;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for the create-weblogic-operator.sh and create-weblogic-domain.sh tests
 */
public class CreateTest {

  private ScratchDir scratch;

  @Before
  public void setUp() throws Exception {
    this.scratch = new ScratchDir();
  }

  @After
  public void tearDown() throws Exception {
    this.scratch.remove();
  }

  protected ScratchDir scratch() { return this.scratch; }

  protected ExecResult exec(String command) throws Exception {
    Process p = Runtime.getRuntime().exec(command);
    try {
      p.waitFor();
      return new ExecResult(p.exitValue(), read(p.getInputStream()), read(p.getErrorStream()));
    } finally {
      p.destroy();
    }
  }

  public static class ScratchDir {
    private Path path;
    private Path userProjects;

    private ScratchDir() throws Exception {
      this.path = Files.createTempDirectory("CreateOperatorTest");
      this.userProjects = path().resolve("user-projects");
      Files.createDirectory(userProjects());
    }

    public Path path() { return this.path; }
    public Path userProjects() { return this.userProjects; }

    private void remove() throws Exception {
      final List<Path> pathsToDelete = Files.walk(path()).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for(Path p : pathsToDelete) {
        Files.deleteIfExists(p);
      }
    }
  }

  private static String read(InputStream is) throws Exception {
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }
}
