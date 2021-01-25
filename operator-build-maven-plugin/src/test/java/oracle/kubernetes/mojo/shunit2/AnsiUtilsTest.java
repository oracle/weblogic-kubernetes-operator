// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import org.junit.jupiter.api.Test;

import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BLUE_FG;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.BOLD;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.GREEN_FG;
import static oracle.kubernetes.mojo.shunit2.AnsiUtils.Format.RED_FG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class AnsiUtilsTest {


  @Test
  public void removeAnsiEscapeCharacters() {
    assertThat(AnsiUtils.withoutAnsiEscapeChars("\u001B[1;31mASSERT:\u001B[0mIt didn't work"),
          equalTo("ASSERT:It didn't work"));
    assertThat(AnsiUtils.withoutAnsiEscapeChars("Ran \u001B[1;36m2\u001B[0m tests."),
          equalTo("Ran 2 tests."));
    assertThat(AnsiUtils.withoutAnsiEscapeChars("\u001B[1;31mFAILED\u001B[0m (\u001B[1;31mfailures=1\u001B[0m)"),
          equalTo("FAILED (failures=1)"));
  }

  @Test
  public void formatBoldTexts() {
    assertThat(AnsiUtils.createFormatter(BOLD).format("sample"),
          equalTo("\u001B[1msample\u001B[0m"));
  }

  @Test
  public void formatBoldText() {
    assertThat(AnsiUtils.createFormatter(BOLD).format("sample"), equalTo("\u001B[1msample\u001B[0m"));
  }

  @Test
  public void formatBoldRedText() {
    assertThat(AnsiUtils.createFormatter(BOLD, RED_FG).format("sample"),
          equalTo("\u001B[1;31msample\u001B[0m"));
  }

  @Test
  public void formatBlueText() {
    assertThat(AnsiUtils.createFormatter(BLUE_FG).format("sample"),
          equalTo("\u001B[34msample\u001B[0m"));
  }

  @Test
  public void formatGreenText() {
    assertThat(AnsiUtils.createFormatter(GREEN_FG).format("sample"),
          equalTo("\u001B[32msample\u001B[0m"));
  }
}