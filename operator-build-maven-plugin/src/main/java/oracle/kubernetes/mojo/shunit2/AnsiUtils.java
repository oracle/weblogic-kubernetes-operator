// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities related to ANSI-formatting of strings sent to a terminal.
 */
class AnsiUtils {

  private static final Pattern ANSI_ESCAPE_CHARS = Pattern.compile("(\\x9B|\\x1B\\[)[0-?]*[ -\\/]*[@-~]");

  static String withoutAnsiEscapeChars(String input) {
    final Matcher matcher = ANSI_ESCAPE_CHARS.matcher(input);
    return matcher.replaceAll("");
  }

  public static AnsiFormatter createFormatter(Format... formats) {
    return new AnsiFormatter(formats);
  }

  static class AnsiFormatter {

    private final Format[] formats;

    AnsiFormatter(Format... formats) {
      this.formats = formats;
    }

    String format(String string) {
      return startCodes() + string + endCodes();
    }

    private String startCodes() {
      return formats.length == 0 ? "" : sequence(Arrays.stream(formats).map(Format::getFormat).toArray(String[]::new));
    }

    private String endCodes() {
      return formats.length == 0 ? "" : sequence("0");
    }

    String sequence(String... formatCodes) {
      return "\u001B[" + String.join(";", formatCodes) + "m";
    }
  }

  static enum Format {
    BOLD(1), RED_FG(31), BLUE_FG(34), GREEN_FG(32);

    private final String format;
    Format(int format) {
      this.format = Integer.toString(format);
    }

    public String getFormat() {
      return format;
    }
  }
}
