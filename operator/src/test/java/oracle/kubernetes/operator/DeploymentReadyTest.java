// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentReadyTest {

  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final CoreDelegateStub coreDelegate = createStrictStub(CoreDelegateStub.class);

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectAllLogMessages(logRecords)
            .withLogLevel(Level.FINE));
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    // delete probesHome dir (java requires a dir be empty before deletion)
    Files.walk(coreDelegate.probesHome.toPath())
        .sorted(Comparator.reverseOrder()) // so files delete before dirs
        .map(Path::toFile)
        .forEach(File::delete);
  }

  @Test
  void whenNoExistingReadyFile_fileCreated() throws IOException {
    DeploymentReady deploymentReady = new DeploymentReady(coreDelegate);
    deploymentReady.create();

    File readyFile = new File(coreDelegate.probesHome, ".ready");
    assertThat(coreDelegate.probesHome, anExistingDirectory());
    assertThat(readyFile, anExistingFile());

    assertThat(logRecords, containsFine("Readiness file created"));
  }

  @Test
  void whenExistingReadyFile_noLog() throws IOException {
    File readyFile = new File(coreDelegate.probesHome, ".ready");
    assertTrue(readyFile.createNewFile());

    DeploymentReady deploymentReady = new DeploymentReady(coreDelegate);
    deploymentReady.create();

    assertThat(coreDelegate.probesHome, anExistingDirectory());
    assertThat(readyFile, anExistingFile());

    assertThat(logRecords, not(containsFine("Readiness file created")));
  }

  @Test
  void whenCantCreateReadyFile_throw() {
    assertTrue(coreDelegate.probesHome.setWritable(false, false));

    DeploymentReady deploymentReady = new DeploymentReady(coreDelegate);
    assertThrows(IOException.class, deploymentReady::create);

    assertThat(coreDelegate.probesHome, anExistingDirectory());
  }

  abstract static class CoreDelegateStub implements CoreDelegate {
    final File probesHome;

    protected CoreDelegateStub() throws IOException {
      probesHome = Files.createTempDirectory("deploymentReadyTest").toFile();
    }

    public File getProbesHome() {
      return probesHome;
    }
  }
}
