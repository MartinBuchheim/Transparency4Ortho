package de.melb00m.tr4o.app;

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

  private final Path xPlanePath;
  private final Set<Path> overlaysPath;
  private final Optional<Path> dsfToolExecutable;
  private final Optional<Path> backupPath;
  private final Level consoleLogLevel;
  private final boolean skipLibraryModifications;
  private final boolean ignoreChecksumErrors;
  private final boolean forbidAutoDownload;

  public RunArguments(
      Path xPlanePath,
      Set<Path> overlaysPath,
      boolean skipLibraryModifications,
      Optional<Path> dsfToolExecutable,
      Level consoleLogLevel,
      boolean ignoreChecksumErrors,
      boolean forbidAutoDownload,
      Optional<Path> backupPath) {
    this.xPlanePath = xPlanePath;
    this.overlaysPath = overlaysPath;
    this.dsfToolExecutable = dsfToolExecutable;
    this.consoleLogLevel = consoleLogLevel;
    this.ignoreChecksumErrors = ignoreChecksumErrors;
    this.forbidAutoDownload = forbidAutoDownload;
    this.backupPath = backupPath;
    this.skipLibraryModifications = skipLibraryModifications;
  }

  public Path getXPlanePath() {
    return xPlanePath;
  }

  public Set<Path> getOverlayPaths() {
    return overlaysPath;
  }

  public Optional<Path> getDsfToolExecutable() {
    return dsfToolExecutable;
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
    return backupPath;
  }

  public boolean isSkipLibraryModifications() {
    return skipLibraryModifications;
  }
}
