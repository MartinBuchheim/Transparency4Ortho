package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.util.FileUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

public class XPToolsInterface {

  private static final Logger LOG = LogManager.getLogger(XPToolsInterface.class);
  private static final Pattern DSFTOOL_VERSION_PATTERN =
      Pattern.compile("DSFTool\\s+(?<version>\\d+[.]\\d+[.]\\d+)");
  private static final String DSFTOOL_VERSION_MINIMUM = "2.1.0";
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

  XPToolsInterface(Optional<Path> execLocation, boolean attemptAutoDownload, Path tempBaseFolder) {
    this.attemptAutoDownload = attemptAutoDownload;
    this.tempDir = FileUtils.createAutoCleanedTempDir(tempBaseFolder, Optional.empty());
    this.configuredDsfPath = execLocation;
  }

  Path getDSFExecutable() {
    synchronized (dsfExecutable) {
      if (dsfExecutable.get() == null) {
        final var exec = configuredDsfPath.orElseGet(this::getBundledDsfToolOrAttemptDownload);
        Validate.isTrue(Files.isExecutable(exec), "DSFTool at '%s' is not executable", exec);
        verifyDSFToolVersion(exec);
        dsfExecutable.set(exec);
      }
    }
    return dsfExecutable.get();
  }

  private void verifyDSFToolVersion(final Path dsfExec) {
    LOG.debug("Verifying the DSFTool at '{}' meets minimum version requirement", dsfExec);
    try {
      final var process = new ProcessBuilder(dsfExec.toString(), "--version").start();
      try (var stream = process.getInputStream()) {
        final var output = new String(stream.readAllBytes());
        LOG.trace("DSFTool '--version' reports: {}", output);
        final var matcher = DSFTOOL_VERSION_PATTERN.matcher(output.trim());
        if (!matcher.find()) {
          throw new IllegalStateException(
              String.format("Failed to extract version-number from DSFTool at %s", dsfExec));
        }
        final var version = matcher.group("version");
        LOG.debug("Detected DSFTool version {}", version);
        Validate.isTrue(
            Integer.parseInt(version.replace(".", ""))
                <= Integer.parseInt(DSFTOOL_VERSION_MINIMUM.replace(".", "")),
            "DSFTool at '{}' is version {} which does not satisfy minimum version requirement of {}",
            dsfExec,
            version,
            DSFTOOL_VERSION_MINIMUM);
      }
    } catch (IOException ex) {
      throw new IllegalStateException(
          String.format("Failed to retrieve version from DSFTool at %s", dsfExec), ex);
    }
  }

  private Path getBundledDsfToolOrAttemptDownload() {
    LOG.debug("No explicit path to DSFTool given, trying to find or download one");
    synchronized (XPToolsInterface.class) {
      try {
        final var targetFolder =
            Paths.get(System.getProperty("user.dir")).resolve(DSFTOOL_DEFAULT_FOLDER);
        MultiValuedMap<String, Path> matchingTool =
            FileUtils.searchFileNamesRecursively(
                targetFolder, DSFTOOL_FILENAMES, Collections.emptySet());
        if (!matchingTool.isEmpty()) {
          final var dsfToolPath = matchingTool.values().iterator().next();
          LOG.debug("Using existing DSFTool found at: {}", dsfToolPath);
          return dsfToolPath;
        }
        Validate.isTrue(attemptAutoDownload, "DSFTool not found and automatic download disabled");
        return attemptAutomaticDownload(targetFolder)
            .orElseThrow(() -> new IllegalStateException("Automatic download of DSFTool failed"));
      } catch (IOException ex) {
        throw new IllegalStateException("Failed to locate DSFTool", ex);
      }
    }
  }

  private Optional<Path> attemptAutomaticDownload(final Path targetFolder) {
    try {
      final var downloadUrl =
          getDownloadUrlForOS()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No DSFTool Download-URL found for this operating system"));
      LOG.info(
          "Attempting automatic download of DSFTool from X-Plane Developer site at: {}",
          downloadUrl);

      // download zipped XPTools from X-Plane developer site
      final var dlTarget =
          tempDir.resolve(FileUtils.extractFileNameFromPath(downloadUrl.getFile()));
      FileUtils.downloadFile(downloadUrl, dlTarget);
      LOG.debug("Download finished successfully");

      // unzip DSFTool from XPTools
      LOG.debug("Looking for DSFTool in zipped file at: {}", dlTarget);
      try (var zipStream = new ZipInputStream(Files.newInputStream(dlTarget))) {
        var zipContent = zipStream.getNextEntry();
        while (zipContent != null) {
          LOG.trace("Checking ZIP-Entry in archive: {}", zipContent.getName());
          final var fileName = FileUtils.extractFileNameFromPath(zipContent.getName());
          if (!zipContent.isDirectory() && DSFTOOL_FILENAMES.contains(fileName)) {
            LOG.debug("Found DSFTool named '{}' in archive", zipContent.getName());
            final var dsfToolPath = Files.createDirectories(targetFolder).resolve(fileName);
            LOG.debug("Extracting DSFTool to: {}", dsfToolPath);
            try (var targetStream = Files.newOutputStream(dsfToolPath)) {
              var buffer = new byte[1024];
              int len;
              while ((len = zipStream.read(buffer)) > 0) {
                targetStream.write(buffer, 0, len);
              }
              return Optional.of(dsfToolPath);
            }
          }
          zipContent = zipStream.getNextEntry();
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to auto-download DSFTool", e);
    }
    return Optional.empty();
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
