package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.FileHelper;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class XPToolsInterface {

  private static final Logger LOG = LogManager.getLogger(XPToolsInterface.class);

  private final AtomicReference<Path> dsfExecutable = new AtomicReference<>();
  private final boolean attemptAutoDownload;

  XPToolsInterface(boolean attemptAutoDownload) {
    this.attemptAutoDownload = attemptAutoDownload;
  }

  private static URL getDownloadUrlForOS() throws MalformedURLException {
    if (SystemUtils.IS_OS_WINDOWS) {
      return new URL(AppConfig.getApplicationConfig().getString("xptools.autodownload.url.win"));
    }
    if (SystemUtils.IS_OS_MAC) {
      return new URL(AppConfig.getApplicationConfig().getString("xptools.autodownload.url.mac"));
    }
    if (SystemUtils.IS_OS_LINUX) {
      return new URL(AppConfig.getApplicationConfig().getString("xptools.autodownload.url.linux"));
    }
    throw new IllegalStateException("No URL for auto-download of XPTools for current OS available");
  }

  Path getDSFExecutable() {
    synchronized (dsfExecutable) {
      if (dsfExecutable.get() == null) {
        dsfExecutable.set(
            getFirstMatchingExecutable(
                AppConfig.getApplicationConfig().getStringList("xptools.executables.dsftool")));
      }
    }
    return dsfExecutable.get();
  }

  private Path getFirstMatchingExecutable(final Collection<String> execNames) {
    synchronized (XPToolsInterface.class) {
      final var binariesPath =
          AppConfig.getRunArguments()
              .getXpToolsPath()
              .orElseGet(
                  () -> Path.of(AppConfig.getApplicationConfig().getString("xptools.bin-folder")));
      final var executables =
          execNames.stream().map(binariesPath::resolve).collect(Collectors.toSet());

      // If an executable is already present, return it
      return executables.stream()
          .filter(Files::isExecutable)
          .findFirst()
          .orElseGet(
              () -> {
                Validate.isTrue(
                    attemptAutoDownload, "XPTools not found and automatic download disabled");
                attemptAutomaticDownload(binariesPath);
                return executables.stream()
                    .filter(Files::isExecutable)
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Failed to locate DSFTool in binaries location at: %s",
                                    binariesPath)));
              });
    }
  }

  private void attemptAutomaticDownload(final Path targetFolder) {
    try {
      final var downloadUrl = getDownloadUrlForOS();
      LOG.info(
          "Attempting automatic download of XPTools from X-Plane Developer site at: {}",
          downloadUrl);

      // download zipped XPTools from X-Plane developer site
      final var tmpPath =
          FileHelper.createAutoCleanedTempDir(
              Path.of(
                  AppConfig.getApplicationConfig().getString("xptools.autodownload.tmp-folder")),
              Optional.empty());
      final var dlTarget =
          tmpPath.resolve(FileHelper.extractFileNameFromPath(downloadUrl.getFile()));
      FileHelper.downloadFile(downloadUrl, dlTarget);
      LOG.debug("Download finished successfully");

      // Copy DSFTool and DDSTool from ZIP to target folder
      final var executablesToKeep =
          Set.copyOf(
              AppConfig.getApplicationConfig().getStringList("xptools.executables.all"));
      Files.createDirectories(targetFolder);
      try (var zipFs = FileSystems.newFileSystem(dlTarget, null)) {
        for (Path rootDirectory : zipFs.getRootDirectories()) {
          try (var stream = Files.walk(rootDirectory)) {
            for (Path exec :
                stream
                    .filter(
                        file ->
                            Files.isRegularFile(file)
                                && executablesToKeep.contains(file.getFileName().toString()))
                    .collect(Collectors.toList())) {
              Files.copy(exec, targetFolder.resolve(exec.getFileName().toString()));
            }
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to auto-download XPTools", e);
    }
  }
}
