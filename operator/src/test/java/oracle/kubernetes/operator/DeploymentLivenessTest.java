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
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentLivenessTest {

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
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
  }

  @Test
  void whenNoExistingLivenessFile_fileCreated() {
    DeploymentLiveness deploymentLiveness = new DeploymentLiveness(coreDelegate);
    deploymentLiveness.run();

    File aliveFile = new File(coreDelegate.probesHome, ".alive");
    assertThat(coreDelegate.probesHome, anExistingDirectory());
    assertThat(aliveFile, anExistingFile());

    assertThat(logRecords, containsFine("Liveness file created"));
    assertThat(logRecords, containsFine("Liveness file last modified time set"));
  }

  @Test
  void whenExistingLivenessFile_onlyLogLastModifiedUpdated() throws IOException {
    File aliveFile = new File(coreDelegate.probesHome, ".alive");
    assertTrue(aliveFile.createNewFile());

    DeploymentLiveness deploymentLiveness = new DeploymentLiveness(coreDelegate);
    deploymentLiveness.run();

    assertThat(coreDelegate.probesHome, anExistingDirectory());
    assertThat(aliveFile, anExistingFile());

    assertThat(logRecords, not(containsFine("Liveness file created")));
    assertThat(logRecords, containsFine("Liveness file last modified time set"));
  }

  @Test
  void whenCantCreateLivenessFile_logWarning() throws IOException {
    assertTrue(coreDelegate.probesHome.setWritable(false, false));

    DeploymentLiveness deploymentLiveness = new DeploymentLiveness(coreDelegate);
    deploymentLiveness.run();

    assertThat(coreDelegate.probesHome, anExistingDirectory());

    assertThat(logRecords, containsWarning(MessageKeys.COULD_NOT_CREATE_LIVENESS_FILE));
  }

  abstract static class CoreDelegateStub implements CoreDelegate {
    final File probesHome;

    protected CoreDelegateStub() throws IOException {
      probesHome = Files.createTempDirectory("deploymentLivenessTest").toFile();
    }

    public File getProbesHome() {
      return probesHome;
    }
  }
}
