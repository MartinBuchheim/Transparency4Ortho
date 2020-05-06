package de.melb00m.tr4o.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.melb00m.tr4o.app.subcommands.LibraryRegeneration;
import de.melb00m.tr4o.app.subcommands.OverlayTransformation;
import de.melb00m.tr4o.exceptions.Exceptions;
import de.melb00m.tr4o.misc.Verify;
import de.melb00m.tr4o.misc.LazyAttribute;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Main entry-class for the application and the primary picocli @{@link
 * picocli.CommandLine.Command}.
 *
 * @author Martin Buchheim
 */
@CommandLine.Command(
    name = "Transparency4Ortho",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    version = "Transparency4Ortho v0.1.0-rc1",
    header = "Transparent Roads for Ortho-Covered Scenery in X-Plane 11",
    footer =
        "For information on how this application operates, and the changes it applies to your ortho-overlay tiles, "
            + "please refer to the documentation at \nhttps://github.com/melb00m/Transparency4Ortho")
public final class Transparency4Ortho implements Runnable {

  public static final Config CONFIG = ConfigFactory.defaultApplication().resolve();
  private static final Logger LOG = LogManager.getLogger(Transparency4Ortho.class);

  @CommandLine.Parameters(index = "0", arity = "1", description = "Path to X-Plane folder")
  private Path xPlanePath;

  @CommandLine.Parameters(
      index = "1",
      arity = "0..*",
      description =
          "Paths to folders containing overlays of ortho-sceneries. Can also point to a folder which contains multiple overlay-folders."
              + "If none is given, the application will scan your X-Plane folder for overlays automatically.")
  private Set<Path> overlayPath;

  @CommandLine.Option(
      names = {"-r", "--regenerateLibrary"},
      description = "Re-generates the Transparency4Ortho library-folder and then exits.")
  private boolean regenerateLibraryMode;

  @CommandLine.Option(
      names = {"-v", "--verbose"},
      description = "Show additional output.")
  private boolean debug;

  @CommandLine.Option(
      names = {"--trace"},
      description = "Show (a lot!) of additional output.")
  private boolean trace;

  @CommandLine.Option(
      names = {"-b", "--backupFolder"},
      description =
          "Alternate folder where backups of overlays will be stored. Default is <X-Plane Folder>/Transparency4Ortho/Backups.")
  private Path backupPath;

  @CommandLine.Option(
      names = {"-s", "--skipLibraryModifications"},
      description =
          "Skip automatic modifications to the Transparency4Ortho library. Can be useful if you want to apply a custom transparency mod manually.")
  private boolean skipLibraryModifications;

  @CommandLine.Option(
      names = {"-i", "--ignoreChecksumMismatch"},
      description = "Ignores checksum mismatches on the default X-Plane roads-library.")
  private boolean ignoreChecksumErrors;

  @CommandLine.Option(
      names = "--dsfTool",
      description =
          "Path to the DSFTool. If not given, an attempt is made to download it automatically from the X-Plane developer website (if necessary).")
  private Path dsfToolExecutable;

  @CommandLine.Option(
      names = "--noDownloads",
      description = "Disable automatic download attempts for retrieving the DSFTool.")
  private boolean forbidAutoDownload;

  private Level consoleLogLevel = Level.INFO;
  private LazyAttribute<Path> applicationFolder = new LazyAttribute<>(this::calcApplicationPath);

  public static void main(String[] args) {
    new CommandLine(new Transparency4Ortho()).execute(args);
  }

  public Optional<Path> getDsfToolExecutable() {
    return null != dsfToolExecutable ? Optional.of(dsfToolExecutable) : Optional.empty();
  }

  public Level getConsoleLogLevel() {
    return consoleLogLevel;
  }

  public boolean isIgnoreChecksumErrors() {
    return ignoreChecksumErrors;
  }

  public boolean isForbidAutoDownload() {
    return forbidAutoDownload;
  }

  public Optional<Path> getBackupPath() {
    return null != backupPath ? Optional.of(backupPath) : Optional.empty();
  }

  public boolean isSkipLibraryModifications() {
    return skipLibraryModifications;
  }

  public Path getApplicationFolder() {
    return applicationFolder.get();
  }

  @Override
  public void run() {
    try {
      setupLogging();
      verifyBasicParameters();
      // select mode of operation
      // if things get a little more elaborate, this could be replaced by using actual subcommands
      // of PicoCli - for now, this would probably a little bit over the top
      if (regenerateLibraryMode) {
        new LibraryRegeneration(this).run();
      } else {
        new OverlayTransformation(this).run();
      }
    } catch (IllegalArgumentException e) {
      LOG.error("ERROR: {}", e.getMessage(), e);
      LOG.info("Use --help to show usage information");
      System.exit(1);
    } catch (java.lang.Exception e) {
      LOG.error("ERROR: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  private void setupLogging() {
    if (debug) consoleLogLevel = Level.DEBUG;
    if (trace) consoleLogLevel = Level.TRACE;
    if (consoleLogLevel != Level.INFO) {
      Configurator.setRootLevel(consoleLogLevel);
      // for the console-appender, we need to modify the threshold filter to the new log-level
      final var context = LoggerContext.getContext(false);
      context.getRootLogger().getAppenders().values().stream()
          .filter(ConsoleAppender.class::isInstance)
          .map(ConsoleAppender.class::cast)
          .forEach(appender -> appender.removeFilter(appender.getFilter()));
      context.updateLoggers();
    }
  }

  private void verifyBasicParameters() {
    Verify.withErrorMessage("X-Plane path is not a valid folder: %s", this::getXPlanePath)
        .argument(Files.isDirectory(getXPlanePath()));
    getOverlayPaths()
        .ifPresent(
            overlays ->
                overlays.forEach(
                    ovl ->
                        Verify.withErrorMessage(
                                "Overlay path does not point to a valid folder: %s", ovl)
                            .argument(Files.isDirectory(ovl))));
  }

  public Path getXPlanePath() {
    return xPlanePath;
  }

  public Optional<Set<Path>> getOverlayPaths() {
    return null != overlayPath && !overlayPath.isEmpty()
        ? Optional.of(overlayPath)
        : Optional.empty();
  }

  private Path calcApplicationPath() {
    try {
      final var codeLocation =
          Transparency4Ortho.class.getProtectionDomain().getCodeSource().getLocation();
      final var path =
          codeLocation.getPath().endsWith(".jar")
              ? Path.of(codeLocation.toURI())
                  .getParent()
                  .getParent() // step out of jar and lib-folder
              : Path.of(System.getProperty("user.dir"));
      LOG.trace("Application directory set to: {}", path.toAbsolutePath());
      return path;
    } catch (URISyntaxException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  @SuppressWarnings("java:S1845")
  public Config config() {
    return CONFIG;
  }
}
