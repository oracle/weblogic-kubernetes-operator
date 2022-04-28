// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.common.utils.CommonTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_UPGRADE_SUCCESS;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class DomainUpgraderTest {
  public static final String DOMAIN_V8_AUX_IMAGE30_YAML = "aux-image-30-sample.yaml";
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private PrintStream console;
  private ByteArrayOutputStream bytes;

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(CommonTestUtils.silenceLogger().collectLogMessages(logRecords, DOMAIN_UPGRADE_SUCCESS));
    mementos.add(StaticStubSupport.install(DomainUpgrader.class, "exitCall", new ExitIntConsumer()));
    bytes   = new ByteArrayOutputStream();
    console = System.out;
    System.setOut(new PrintStream(bytes));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
    System.setSecurityManager(null);
    System.setOut(console);
  }

  @Test
  void whenNoInputFileProvided_domainUpgraderExitsWithErrorStatus() {
    ExitException thrown = Assertions.assertThrows(ExitException.class, () -> {
      DomainUpgrader.main();
    });
    assertThat(thrown.status, is(1));
  }

  @Test
  void withExistingFileOverwriteOption_domainUpgradeIsSuccessful() throws URISyntaxException {
    Path path = Paths.get(getClass().getClassLoader().getResource(DOMAIN_V8_AUX_IMAGE30_YAML).toURI());
    DomainUpgrader.main(path.toString(), "-o", "-f converted.yaml");
    assertThat(logRecords, containsInfo(DOMAIN_UPGRADE_SUCCESS));
  }

  @Test
  void withNoExistingFileOverwriteOption_domainUpgradeThrowsUpgradeException() throws URISyntaxException {
    Path path = Paths.get(getClass().getClassLoader().getResource(DOMAIN_V8_AUX_IMAGE30_YAML).toURI());

    DomainUpgrader.DomainUpgraderException thrown =
            Assertions.assertThrows(DomainUpgrader.DomainUpgraderException.class, () -> {
              DomainUpgrader.main(path.toString(), "-f converted.yaml");
            });
    assertThat(thrown.getMessage().contains("already exists"), is(true));
  }

  protected static class ExitException extends SecurityException {
    public final int status;

    public ExitException(int status) {
      super("There is no escape!");
      this.status = status;
    }
  }

  protected static class ExitIntConsumer implements IntConsumer {
    @Override
    public void accept(int status) {
      throw new ExitException(status);
    }
  }
}
