package de.melb00m.tr4o.app;

import de.melb00m.tr4o.proc.RunProcessor;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;

/**
 * Main App class for TransparentRoads4Ortho.
 *
 * <p>Contains the main-method that is called upon application startup, and reads/validates the
 * incoming run-arguments before transferring them to a {@link RunArguments}-instance.
 *
 * @author martin.buchheim
 */
public class Transparency4Ortho {

  private static final Logger LOG = LogManager.getLogger(Transparency4Ortho.class);

  @SuppressWarnings("java:S106")
  public static void main(String[] args) {
    final var options = generateCliOptions();
    try {
      final var line = new DefaultParser().parse(options, args);

      if (line.hasOption("h")) {
        printHelp(options);
        System.exit(0);
      }

      if (line.hasOption("v")) {
        new PrintWriter(System.out).println(Transparency4Ortho.class.getPackage().getImplementationVersion());
        System.exit(0);
      }

      AppConfig.initialize(line);
      new RunProcessor().startProcessing();

    } catch (IllegalArgumentException | ParseException ex) {
      stopWithError(ex, true);
    } catch (Exception ex) {
      stopWithError(ex, false);
    }
  }

  private static void stopWithError(final Exception fail, final boolean printUsageReminder) {
    LOG.error(fail.getMessage(), fail);
    if (printUsageReminder) {
      LOG.info("Use --help to show usage information");
    }
    System.exit(1);
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
                RunArguments.DEFAULT_LIBRARY_FOLDER))
        .addOption(
            "lp",
            "LibraryPrefix",
            true,
            String.format(
                "Prefix used in Overlay-DSF for the TransparentRoads4Ortho-library. Defaults to '%s'",
                RunArguments.DEFAULT_LIBRARY_PREFIX))
        .addOption("d", "debug", false, "Show debug log output")
        .addOption("dd", "trace", false, "Show trace log output ")
        .addOption("h", "help", false, "Show detailed usage information")
        .addOption("v", "version", false, "Show version");
  }
}
