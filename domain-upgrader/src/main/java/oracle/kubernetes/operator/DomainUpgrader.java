// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import oracle.kubernetes.operator.logging.CommonLoggingFacade;
import oracle.kubernetes.operator.logging.CommonLoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.SchemaConversionUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;

public class DomainUpgrader {

  private static final CommonLoggingFacade LOGGER =
          CommonLoggingFactory.getLogger("DomainUpgrader", "Operator");
  private static SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

  String inputFileName;
  String outputDir;
  String outputFileName;
  Boolean overwriteExistingFile;

  /**
   * Entry point of the DomainResourceConverter.
   * @param args The arguments for domain resource converter.
   *
   */
  public static void main(String[] args) {
    final DomainUpgrader domainUpgrader = parseCommandLine(args);

    File inputFile = new File(domainUpgrader.inputFileName);
    File outputDir = new File(domainUpgrader.outputDir);
    File outputFile = new File(domainUpgrader.outputDir + "/" + domainUpgrader.outputFileName);

    if (!inputFile.exists()) {
      throw new RuntimeException(LOGGER.formatMessage(MessageKeys.INPUT_FILE_NON_EXISTENT,
              domainUpgrader.inputFileName));
    }

    if (!outputDir.exists()) {
      throw new RuntimeException(LOGGER.formatMessage(MessageKeys.OUTPUT_FILE_NON_EXISTENT, outputDir));
    }

    if (outputFile.exists() && !domainUpgrader.overwriteExistingFile) {
      throw new RuntimeException(LOGGER.formatMessage(MessageKeys.OUTPUT_FILE_EXISTS, outputFile.getName()));
    }

    convertDomain(domainUpgrader);
  }

  private static void convertDomain(DomainUpgrader upgrader) {
    try (Writer writer = Files.newBufferedWriter(Path.of(upgrader.outputDir + "/" + upgrader.outputFileName))) {
      writer.write(schemaConversionUtils.convertDomainSchema(Files.readString(Path.of(upgrader.inputFileName))));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Constructs an instances of Domain resource converter with given arguments.
   * @param outputDir Name of the output directory.
   * @param outputFileName Name of the output file.
   * @param overwriteExistingFile Option to overwrite existing file.
   * @param inputFileName Name of the input file.
   */
  public DomainUpgrader(
          String outputDir,
          String outputFileName,
          Boolean overwriteExistingFile,
          String inputFileName) {
    this.outputDir = Optional.ofNullable(outputDir).orElse(new File(inputFileName).getParent());
    String inputFileNameBase = FilenameUtils.getBaseName(inputFileName);
    String inputFileNameExtension  = FilenameUtils.getExtension(inputFileName);
    this.outputFileName = Optional.ofNullable(outputFileName)
            .orElse(inputFileNameBase + "__converted." + inputFileNameExtension);
    this.inputFileName = inputFileName;
    this.overwriteExistingFile = overwriteExistingFile;
  }

  private static DomainUpgrader parseCommandLine(String[] args) {
    CommandLineParser parser = new DefaultParser();
    Options options = new Options();

    Option helpOpt = new Option("h", "help", false, LOGGER.formatMessage(MessageKeys.PRINT_HELP));
    options.addOption(helpOpt);

    Option outputDir = new Option("d", "outputDir", true, LOGGER.formatMessage(MessageKeys.OUTPUT_DIRECTORY));
    options.addOption(outputDir);

    Option outputFile = new Option("f", "outputFile", true, LOGGER.formatMessage(MessageKeys.OUTPUT_FILE_NAME));
    options.addOption(outputFile);

    Option overwriteExistingFile = new Option("o", "overwriteExistingFile", false,
            LOGGER.formatMessage(MessageKeys.OVERWRITE_EXISTING_OUTPUT_FILE));
    options.addOption(overwriteExistingFile);

    try {
      CommandLine cli = parser.parse(options, args);
      if (cli.hasOption("help")) {
        printHelpAndExit(options);
      }
      if (cli.getArgs().length < 1) {
        printHelpAndExit(options);
      }
      return new DomainUpgrader(cli.getOptionValue("d"), cli.getOptionValue("f"),
              cli.hasOption("o"), cli.getArgs()[0]);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static void printHelpAndExit(Options options) {
    HelpFormatter help = new HelpFormatter();
    help.printHelp(120, "Converts V8 or earlier domain custom resource yaml to V9 or a future version."
                    + "\n       java -jar domain-upgrader.jar "
                    + "<input-file> [-d <output_dir>] [-f <output_file_name>] [-o --overwriteExistingFile] "
                    + "[-h --help]",
            "", options, "");
    System.exit(1);
  }
}