package de.melb00m.tr4o.app;

import de.melb00m.tr4o.proc.RunProcessor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main App class for TransparentRoads4Ortho.
 *
 * <p>Contains the main-method that is called upon application startup, and reads/validates the
 * incoming run-arguments before transferring them to a {@link RunConfiguration}-instance.
 *
 * @author martin.buchheim
 */
public class Transparency4Ortho {

  private static final Logger LOG = LogManager.getLogger(Transparency4Ortho.class);

  public static void main(String[] args) {
    final var options = generateCliOptions();
    try {
      final var line = new DefaultParser().parse(options, args);

      if (line.hasOption("h")) {
        printHelp(options);
        System.exit(0);
      }

      if (line.hasOption("version")) {
        // TODO: connect with release management
        throw new UnsupportedOperationException("Versioning currently not enabled");
      }

      final var config = readAndVerifyArguments(line);
      configureLoggingOutput(config.getConsoleLogLevel());

      new RunProcessor(config).startProcessing();

    } catch (IllegalArgumentException | ParseException ex) {
      stopWithError(ex, true);
    } catch (Exception ex) {
      stopWithError(ex, false);
    }
  }

  private static void stopWithError(final Exception fail, final boolean printUsageReminder) {
    LOG.error("FAILURE: {}", fail.getMessage(), fail);
    if (printUsageReminder) LOG.info("Use --help to show usage information");
    System.exit(1);
  }

  private static void configureLoggingOutput(final Level level) {
    if (level != Level.INFO) {
      Configurator.setRootLevel(level);
      // for the console-appender, we need to modify the threshold filter to the new log-level
      final var context = LoggerContext.getContext(false);
      context.getRootLogger().getAppenders().values().stream()
          .filter(ConsoleAppender.class::isInstance)
          .map(ConsoleAppender.class::cast)
          .forEach(appender -> appender.removeFilter(appender.getFilter()));
      context.updateLoggers();
    }
  }

  private static RunConfiguration readAndVerifyArguments(CommandLine line) {
    final var args = line.getArgList();
    Validate.isTrue(args.size() >= 3, "Expecting at least 3 path-parameters with application call");

    final var xPlanePath = Paths.get(args.get(0));
    final var overlayPath = Paths.get(args.get(1));
    final var tilesPath =
        args.subList(2, args.size()).stream()
            .map(Paths::get)
            .collect(Collectors.toUnmodifiableSet());
    final var dsfToolExec = getOptionalPath(line, "dx");

    Validate.isTrue(
        Files.isReadable(xPlanePath), "X-Plane location '%s' is not readable", xPlanePath);
    Validate.isTrue(
        Files.exists(overlayPath) && Files.exists(overlayPath.resolve("Earth nav data")),
        "Overlay path '%s' does not contain an expected 'Earth nav data' folder",
        overlayPath);
    tilesPath.forEach(
        tile ->
            Validate.isTrue(Files.isReadable(tile), "Tile directory '%s' is not readable", tile));
    dsfToolExec.ifPresent(
        dx -> Validate.isTrue(Files.isExecutable(dx), "DSFTool at '%s' is not executable", dx));

    var logLevel = Level.INFO;
    if (line.hasOption("d")) logLevel = Level.DEBUG;
    if (line.hasOption("dd")) logLevel = Level.TRACE;

    return new RunConfiguration(
        xPlanePath,
        tilesPath,
        overlayPath,
        getOptionalPath(line, "dx"),
        getOptionalPath(line, "b"),
        Optional.ofNullable(line.getOptionValue("lf")),
        Optional.ofNullable(line.getOptionValue("lp")),
        logLevel);
  }

  private static Optional<Path> getOptionalPath(CommandLine line, String config) {
    return line.hasOption(config)
        ? Optional.of(Paths.get(line.getOptionValue(config)))
        : Optional.empty();
  }

  private static void printHelp(Options options) {
    new HelpFormatter()
        .printHelp(
            120,
            "TransparentRoads4Ortho [options] <path-to-xplane> <path-to-overlays> <path-to-tiles> [<path-to-tiles> ...]",
            "",
            options,
            "");
  }

  private static Options generateCliOptions() {
    return new Options()
        .addOption(
            "dx",
            "DSFToolExecutable",
            true,
            "Path to DSFTool Executable. If not given, an attempt is made to automatically download and store the tool from the X-Plane Developer site.")
        .addOption(
            "b",
            "OverlayBackupPath",
            true,
            "Folder in which the Overlay-Backups will be stored before they are modified. Defaults to '<X-Plane-Folder>/TransparentRoads4Ortho/Backups/<current-date>' in the application folder.")
        .addOption(
            "lf",
            "LibraryFolderName",
            true,
            String.format(
                "Name of the library folder that will be created under '<X-Plane>/Custom Scenery'. Defaults to '%s'.",
                RunConfiguration.DEFAULT_LIBRARY_FOLDER))
        .addOption(
            "lp",
            "LibraryPrefix",
            true,
            String.format(
                "Prefix used in Overlay-DSF for the TransparentRoads4Ortho-library. Defaults to '%s'",
                RunConfiguration.DEFAULT_LIBRARY_PREFIX))
        .addOption("d", "debug", false, "Show debug log output")
        .addOption("dd", "trace", false, "Show trace log output ")
        .addOption("h", "help", false, "Show detailed usage information");
  }
}
