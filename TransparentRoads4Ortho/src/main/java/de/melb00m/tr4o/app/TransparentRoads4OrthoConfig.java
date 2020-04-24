package de.melb00m.tr4o.app;

import lombok.Getter;
import lombok.Value;
import lombok.experimental.PackagePrivate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

@Value
public class TransparentRoads4OrthoConfig {

  static final String DEFAULT_LIBRARY_FOLDER = "aaa_TransparentRoads4Ortho";
  static final String DEFAULT_LIBRARY_PREFIX = "transparentRoads4Ortho";
  static final Logger LOG = LogManager.getLogger(TransparentRoads4OrthoConfig.class);

  DateFormat BACKUP_FOLDER_DATE = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
  Path xPlanePath;
  Path tilesPath;
  Path overlaysPath;
  Optional<Path> dsfToolPath;
  @PackagePrivate Optional<Path> _overlayBackupPath;
  @PackagePrivate Optional<String> _libraryFolderName;
  @PackagePrivate Optional<String> _libraryPrefix;

  @Getter(lazy = true)
  Path libraryPath = calcLibraryPath();

  @Getter(lazy = true)
  Path backupPath = calcBackupPath();

  @Getter(lazy = true)
  String libraryPrefix = calcLibraryPrefix();

  private Path calcBackupPath() {
    return _overlayBackupPath
        .orElseGet(() -> xPlanePath.resolve("TransparentRoads4Ortho").resolve("Backups"))
        .resolve(BACKUP_FOLDER_DATE.format(new Date()));
  }

  private Path calcLibraryPath() {
    return xPlanePath
        .resolve("Custom Scenery")
        .resolve(_libraryFolderName.orElse(DEFAULT_LIBRARY_FOLDER));
  }

  private String calcLibraryPrefix() {
    return _libraryPrefix.orElse(DEFAULT_LIBRARY_PREFIX);
  }
}
