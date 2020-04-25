package de.melb00m.tr4o.app;

import de.melb00m.tr4o.util.FileUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class holding the configuration for this run.
 *
 * @author martin.buchheim
 */
public class TransparentRoads4OrthoConfig {

  static final String DEFAULT_LIBRARY_FOLDER = "aaa_TransparentRoads4Ortho";
  static final String DEFAULT_LIBRARY_PREFIX = "transparentRoads4Ortho";
  static final Logger LOG = LogManager.getLogger(TransparentRoads4OrthoConfig.class);

  private final DateFormat BACKUP_FOLDER_DATE = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
  private final Path xPlanePath;
  private final Set<Path> tilesPath;
  private final Path overlaysPath;
  private final Optional<Path> dsfToolPath;
  private final Path libraryPath;
  private final Path backupPath;
  private final String libraryPrefix;
  private final Path tempDirPath = Paths.get(System.getProperty("user.dir")).resolve("tmp");
  private final AtomicReference<Path> tempDir = new AtomicReference<>();

  public TransparentRoads4OrthoConfig(
      Path xPlanePath,
      Set<Path> tilesPath,
      Path overlaysPath,
      Optional<Path> dsfToolPath,
      Optional<Path> overlayBackupPath,
      Optional<String> libraryFolderName,
      Optional<String> libraryPrefix) {
    this.xPlanePath = xPlanePath;
    this.tilesPath = tilesPath;
    this.overlaysPath = overlaysPath;
    this.dsfToolPath = dsfToolPath;
    this.backupPath =
        overlayBackupPath
            .orElseGet(() -> xPlanePath.resolve("TransparentRoads4Ortho").resolve("Backups"))
            .resolve(BACKUP_FOLDER_DATE.format(new Date()));
    this.libraryPath =
        xPlanePath
            .resolve("Custom Scenery")
            .resolve(libraryFolderName.orElse(DEFAULT_LIBRARY_FOLDER));
    this.libraryPrefix = libraryPrefix.orElse(DEFAULT_LIBRARY_PREFIX);
  }

  public Path getXPlanePath() {
    return xPlanePath;
  }

  public Set<Path> getTilesPath() {
    return tilesPath;
  }

  public Path getOverlaysPath() {
    return overlaysPath;
  }

  public Optional<Path> getDsfToolPath() {
    return dsfToolPath;
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

  public Path getTempDir() {
    synchronized (tempDir) {
      if (tempDir.get() == null) {
        try {
          Files.createDirectories(tempDirPath);
          final var tempDirectory = Files.createTempDirectory(tempDirPath, null);
          tempDir.set(tempDirectory);
          Runtime.getRuntime()
              .addShutdownHook(
                  new Thread(
                      () -> {
                        try {
                          FileUtils.deleteRecursively(tempDirectory, Collections.emptySet());
                        } catch (IOException e) {
                          LOG.warn("Failed to delete temporary directory at: {}", tempDirectory);
                        }
                      }));
        } catch (IOException e) {
          throw new IllegalStateException(
              String.format("Failed to initialize temporary directory at: %s", tempDirPath), e);
        }
      }
    }
    return tempDir.get();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("xPlanePath", xPlanePath)
        .append("tilesPath", tilesPath)
        .append("overlaysPath", overlaysPath)
        .append("dsfToolPath", dsfToolPath)
        .append("libraryPath", libraryPath)
        .append("backupPath", backupPath)
        .append("libraryPrefix", libraryPrefix)
        .append("tempDirPath", tempDirPath)
        .toString();
  }
}
