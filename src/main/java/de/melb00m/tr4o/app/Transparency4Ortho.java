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
        final var writer = new PrintWriter(System.out);
        writer.println(Transparency4Ortho.class.getPackage().getImplementationVersion());
        writer.flush();
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
            "TransparentRoads4Ortho [options] <path-to-xplane> <path-to-overlays> [<path-to-overlays> ...]",
            "",
            options,
            "");
  }

  private static Options generateCliOptions() {
    return new Options()
        .addOption(
            "dsf",
            "dsfTool",
            true,
            "Path to DSFTool Executable. If not given, an attempt is made to automatically download and store the tool from the X-Plane Developer site (unless '-n' is used).")
        .addOption(
            "b",
            "overlayBackups",
            true,
            "Folder in which the Overlay-Backups will be stored before they are modified. Defaults to '<X-Plane-Folder>/TransparentRoads4Ortho/Backups/<current-date>' in the application folder.")
        .addOption(
            "i",
            "ignoreChecksum",
            false,
            "Ignore checksum errors when copying X-Plane default libraries.")
        .addOption(
            "n", "noDownload", false, "Do not attempt to download required tools automatically.")
        .addOption("d", "debug", false, "Show debug log output")
        .addOption("dd", "trace", false, "Show trace log output ")
        .addOption("h", "help", false, "Show detailed usage information")
        .addOption("v", "version", false, "Show version");
  }
}
