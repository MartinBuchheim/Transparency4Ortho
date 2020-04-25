package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.util.FileUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

public class DSFToolInterface {

  private static final Logger LOG = LogManager.getLogger(DSFToolInterface.class);
  private static final String DSFTOOL_DEFAULT_FOLDER = "bin";
  private static final Set<String> DSFTOOL_FILENAMES = Set.of("DSFTool", "DSFTool.exe");
  private static final String XPTOOLS_DOWNLOADURL_WIN =
      "http://dev.x-plane.com/download/tools/xptools_win_15-3.zip";
  private static final String XPTOOLS_DOWNLOADURL_MAC =
      "http://dev.x-plane.com/download/tools/xptools_mac_15-3.zip";
  private static final String XPTOOLS_DOWNLOADURL_LINUX =
      "http://dev.x-plane.com/download/tools/xptools_lin_15-3.zip";

  private final AtomicReference<Path> dsfExecutable = new AtomicReference<>();
  private final Optional<Path> configuredDsfPath;
  private final Path tempDir;
  private final boolean attemptAutoDownload;

  DSFToolInterface(Optional<Path> execLocation, boolean attemptAutoDownload, Path tempDir) {
    this.attemptAutoDownload = attemptAutoDownload;
    this.tempDir = tempDir;
    this.configuredDsfPath = execLocation;
  }

  Path getDSFExecutable() {
    synchronized (dsfExecutable) {
      if (dsfExecutable.get() == null) {
        dsfExecutable.set(configuredDsfPath.orElseGet(() -> getExistingOrAutoDownloadedDSFTool()));
      }
    }
    return dsfExecutable.get();
  }

  private Path getExistingOrAutoDownloadedDSFTool() {
    LOG.debug("No explicit path to DSFTool given, trying to find or download one");
    synchronized (DSFToolInterface.class) {
      try {
        final var targetFolder =
            Paths.get(System.getProperty("user.dir")).resolve(DSFTOOL_DEFAULT_FOLDER);
        MultiValuedMap<String, Path> matchingTool =
            FileUtils.searchFileNamesRecursively(targetFolder, DSFTOOL_FILENAMES);
        if (!matchingTool.isEmpty()) {
          final var dsfToolPath = matchingTool.values().iterator().next();
          LOG.debug("Using existing DSFTool found at: {}", dsfToolPath);
          return dsfToolPath;
        }
        Validate.isTrue(attemptAutoDownload, "DSFTool not found and automatic download disabled");
        return attemptAutomaticDownlad(targetFolder);

      } catch (IOException ex) {
        throw new IllegalStateException("Failed to locate DSFTool", ex);
      }
    }
  }

  private Path attemptAutomaticDownlad(final Path targetFolder) {
    try {
      final var downloadUrl =
          getDownloadUrlForOS()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No DSFTool Download-URL found for this operating system"));
      LOG.info(
          "Attempting automatic download of DSFTool from X-Plane Developer site at {}",
          downloadUrl);

      // download zipped XPTools from X-Plane developer site
      final var dlTarget =
          tempDir.resolve(FileUtils.extractFileNameFromPath(downloadUrl.getFile()));
      try (var downloadStream = Channels.newChannel(downloadUrl.openStream());
          var downloadTargetStream = new FileOutputStream(dlTarget.toFile()).getChannel()) {
        LOG.debug("Downloading XPTools to: {}...", dlTarget);
        downloadTargetStream.transferFrom(downloadStream, 0, Long.MAX_VALUE);
      }
      LOG.debug("Download finished successfully");

      // unzip DSFTool
      LOG.debug("Looking for DSFTool in zipped file at: {}", dlTarget);
      try (var zipStream = new ZipInputStream(Files.newInputStream(dlTarget))) {
        var zipContent = zipStream.getNextEntry();
        while (zipContent != null) {
          LOG.trace("Checking ZIP-Entry in archive: {}", zipContent.getName());
          final var fileName = FileUtils.extractFileNameFromPath(zipContent.getName());
          if (!zipContent.isDirectory() && DSFTOOL_FILENAMES.contains(fileName)) {
            LOG.debug("Found probable DSFTool named '{}' in archive", zipContent.getName());
            final var dsfToolPath = Files.createDirectories(targetFolder).resolve(fileName);
            LOG.debug("Extracting DSFTool to: {}", dsfToolPath);
            try (var targetStream = Files.newOutputStream(dsfToolPath)) {
              var buffer = new byte[1024];
              int len;
              while ((len = zipStream.read(buffer)) > 0) {
                targetStream.write(buffer, 0, len);
              }
              return dsfToolPath;
            }
          }
          zipContent = zipStream.getNextEntry();
        }
      }

    } catch (IOException e) {
      throw new IllegalStateException("Failed to auto-download DSFTool", e);
    }

    throw new IllegalStateException("Failed to automatically download");
  }

  private static Optional<URL> getDownloadUrlForOS() throws MalformedURLException {
    if (SystemUtils.IS_OS_WINDOWS) {
      return Optional.of(new URL(XPTOOLS_DOWNLOADURL_WIN));
    }
    if (SystemUtils.IS_OS_MAC) {
      return Optional.of(new URL(XPTOOLS_DOWNLOADURL_MAC));
    }
    if (SystemUtils.IS_OS_LINUX) {
      return Optional.of(new URL(XPTOOLS_DOWNLOADURL_LINUX));
    }
    return Optional.empty();
  }
}
