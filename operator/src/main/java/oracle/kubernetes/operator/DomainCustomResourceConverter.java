// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.utils.DomainUpgradeUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import static oracle.kubernetes.operator.logging.MessageKeys.INPUT_FILE_NON_EXISTENT;
import static oracle.kubernetes.operator.logging.MessageKeys.OUTPUT_DIRECTORY;
import static oracle.kubernetes.operator.logging.MessageKeys.OUTPUT_FILE_NAME;
import static oracle.kubernetes.operator.logging.MessageKeys.OUTPUT_FILE_NON_EXISTENT;
import static oracle.kubernetes.operator.logging.MessageKeys.PRINT_HELP;

public class DomainCustomResourceConverter {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("DomainResourceConverter", "Operator");
  private static final DomainUpgradeUtils domainUpgradeUtils = new DomainUpgradeUtils();

  String inputFileName;
  String outputDir;
  String outputFileName;

  /**
   * Entry point of the DomainResourceConverter.
   * @param args The arguments for domain resource converter.
   *
   */
  public static void main(String[] args) {
    final DomainCustomResourceConverter converter = parseCommandLine(args);

    File inputFile = new File(converter.inputFileName);
    File outputDir = new File(converter.outputDir);

    if (!inputFile.exists()) {
      throw new RuntimeException(LOGGER.formatMessage(INPUT_FILE_NON_EXISTENT, converter.inputFileName));
    }

    if (!outputDir.exists()) {
      throw new RuntimeException(OUTPUT_FILE_NON_EXISTENT);
    }

    convertDomain(converter);
  }

  private static void convertDomain(DomainCustomResourceConverter converter) {
    try (Writer writer = Files.newBufferedWriter(Path.of(converter.outputDir + "/" + converter.outputFileName))) {
      writer.write(domainUpgradeUtils.convertDomain(Files.readString(Path.of(converter.inputFileName))));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Constructs an instances of Domain resource converter with given arguments.
   * @param outputDir Name of the output directory.
   * @param outputFileName Name of the output file.
   * @param inputFileName Name of the input file.
   */
  public DomainCustomResourceConverter(
          String outputDir,
          String outputFileName,
          String inputFileName) {
    this.outputDir = Optional.ofNullable(outputDir).orElse(new File(inputFileName).getParent());
    this.outputFileName = Optional.ofNullable(outputFileName)
            .orElse("Converted_" + new File(inputFileName).getName());
    this.inputFileName = inputFileName;
  }

  private static DomainCustomResourceConverter parseCommandLine(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();

    Option helpOpt = new Option("h", "help", false, PRINT_HELP);
    options.addOption(helpOpt);

    Option outputDir = new Option("d", "outputDir", true, OUTPUT_DIRECTORY);
    options.addOption(outputDir);

    Option outputFile = new Option("f", "outputFile", true, OUTPUT_FILE_NAME);
    options.addOption(outputFile);

    try {
      CommandLine cli = parser.parse(options, args);
      if (cli.hasOption("help")) {
        printHelpAndExit(options);
      }
      if (cli.getArgs().length < 1) {
        printHelpAndExit(options);
      }
      return new DomainCustomResourceConverter(cli.getOptionValue("d"), cli.getOptionValue("f"),
              cli.getArgs()[0]);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static void printHelpAndExit(Options options) {
    HelpFormatter help = new HelpFormatter();
    help.printHelp(120, "java -cp <operator-jar> "
                    + "oracle.kubernetes.operator.DomainCustomResourceConverter "
                    + "<input-file> [-d <output_dir>] [-f <output_file_name>] [-h --help]",
            "", options, "");
    System.exit(1);
  }
}