package de.melb00m.tr4o.app;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Class holding the configuration for this run.
 *
 * @author martin.buchheim
 */
public final class RunArguments {

  static final String DEFAULT_LIBRARY_FOLDER = "aaa_TransparentRoads4Ortho";
  static final String DEFAULT_LIBRARY_PREFIX = "transparentRoads4Ortho";

  private final Path xPlanePath;
  private final Set<Path> tilesPaths;
  private final Path overlaysPath;
  private final Optional<Path> xpToolsPath;
  private final Path libraryPath;
  private final Path backupPath;
  private final String libraryPrefix;
  private final Path temporariesBasePath = Path.of(System.getProperty("user.dir")).resolve("tmp");
  private final Path binariesPath = Path.of(System.getProperty("user.dir")).resolve("bin");
  private final Level consoleLogLevel;

  public RunArguments(
      Path xPlanePath,
      Set<Path> tilesPaths,
      Path overlaysPath,
      Optional<Path> xpToolsPath,
      Optional<Path> overlayBackupPath,
      Optional<String> libraryFolderName,
      Optional<String> libraryPrefix,
      Level consoleLogLevel) {
    this.xPlanePath = xPlanePath;
    this.tilesPaths = tilesPaths;
    this.overlaysPath = overlaysPath;
    this.xpToolsPath = xpToolsPath;
    this.consoleLogLevel = consoleLogLevel;
    this.backupPath =
        overlayBackupPath.orElseGet(
            () -> xPlanePath.resolve("TransparentRoads4Ortho").resolve("Backups"));
    this.libraryPath =
        xPlanePath
            .resolve("Custom Scenery")
            .resolve(libraryFolderName.orElse(DEFAULT_LIBRARY_FOLDER));
    this.libraryPrefix = libraryPrefix.orElse(DEFAULT_LIBRARY_PREFIX);
  }

  public Path getXPlanePath() {
    return xPlanePath;
  }

  public Set<Path> getTilesPaths() {
    return tilesPaths;
  }

  public Path getOverlaysPath() {
    return overlaysPath;
  }

  public Optional<Path> getXpToolsPath() {
    return xpToolsPath;
  }

  public Path getLibraryPath() {
    return libraryPath;
  }

  public Path getBackupPath() {
    return backupPath;
  }

  public String getLibraryPrefix() {
    return libraryPrefix;
  }

  public Path getTemporariesBasePath() {
    return temporariesBasePath;
  }

  public Path getBinariesPath() {
    return binariesPath;
  }

  public Level getConsoleLogLevel() {
    return consoleLogLevel;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
        .append("xPlanePath", xPlanePath)
        .append("tilesPaths", tilesPaths)
        .append("overlaysPath", overlaysPath)
        .append("xpToolsPath", xpToolsPath)
        .append("libraryPath", libraryPath)
        .append("backupPath", backupPath)
        .append("libraryPrefix", libraryPrefix)
        .append("temporariesBasePath", temporariesBasePath)
        .append("binariesPath", binariesPath)
        .toString();
  }
}
