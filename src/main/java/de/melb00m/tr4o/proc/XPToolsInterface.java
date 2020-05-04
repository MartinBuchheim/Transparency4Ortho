package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.FileHelper;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
  private final Path temporaryFolder;
  private final Path binariesFolder;

  public XPToolsInterface() {
    this.temporaryFolder =
        FileHelper.createAutoCleanedTempDir(
            Path.of(AppConfig.getApplicationConfig().getString("xptools.tmp-folder")),
            Optional.empty());
    this.binariesFolder = Path.of(AppConfig.getApplicationConfig().getString("xptools.bin-folder"));
    // this is a hack to download the XPTools right away once this is created
    getDSFExecutable();
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

  private static String toProcessFile(final Path path) {
    return path.toAbsolutePath().toString();
  }

  private Path getDSFExecutable() {
    synchronized (dsfExecutable) {
      if (dsfExecutable.get() == null) {
        // try to fetch from run-args first
        var dsfToolExecutable = AppConfig.getRunArguments().getDsfToolExecutable();
        // if no run-arg given, try to use DSFtool in binaries folder, or attempt auto download
        if (dsfToolExecutable.isEmpty()) {
          final var dsfExecutableNames =
              AppConfig.getApplicationConfig().getStringList("xptools.executables.dsftool");
          dsfToolExecutable =
              getFirstMatchingExecutable(dsfExecutableNames)
                  .or(
                      () -> {
                        attemptAutomaticDownloadOfXpTools(binariesFolder);
                        return getFirstMatchingExecutable(dsfExecutableNames);
                      });
        }
        dsfToolExecutable.ifPresent(
            exec ->
                Validate.isTrue(Files.isExecutable(exec), "DSFTool is not executable: %s", exec));
        dsfExecutable.set(
            dsfToolExecutable.orElseThrow(
                () -> new IllegalStateException("Failed to retrieve DSFTool")));
      }
    }
    return dsfExecutable.get();
  }

  public String dsfToText(final Path dsfFile) throws IOException {
    Validate.isTrue(Files.isReadable(dsfFile), "Given DSF file is not readable: %s", dsfFile);
    final var process =
        new ProcessBuilder(
                toProcessFile(getDSFExecutable()), "--dsf2text", toProcessFile(dsfFile), "-")
            .start();
    try (final var input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      return input.lines().collect(Collectors.joining(System.lineSeparator()));
    }
  }

  public void textToDsf(final Path targetFile, final String contents) throws IOException {
    Validate.isTrue(
        !Files.exists(targetFile), "Won't override existing DSF file at: %s", targetFile);
    final var process =
        new ProcessBuilder(
                toProcessFile(getDSFExecutable()), "--text2dsf", "-", toProcessFile(targetFile))
            .start();
    try (var osw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
      osw.write(contents);
      osw.flush();
    }
  }

  private Optional<Path> getFirstMatchingExecutable(final Collection<String> execNames) {
    synchronized (XPToolsInterface.class) {
      return execNames.stream()
          .map(binariesFolder::resolve)
          .filter(Files::isExecutable)
          .findFirst();
    }
  }

  private void attemptAutomaticDownloadOfXpTools(final Path targetFolder) {
    Validate.isTrue(
        !AppConfig.getRunArguments().isForbidAutoDownload(),
        "XPTools not found and automatic download disabled");
    try {
      final var downloadUrl = getDownloadUrlForOS();
      LOG.info(
          "Attempting automatic download of XPTools from X-Plane developer site at: {}",
          downloadUrl);

      // download zipped XPTools from X-Plane developer site
      final var dlTarget =
          temporaryFolder.resolve(FileHelper.extractFileNameFromPath(downloadUrl.getFile()));
      FileHelper.downloadFile(downloadUrl, dlTarget);
      LOG.info("Download finished successfully");

      // Copy DSFTool and DDSTool from ZIP to target folder
      final var executablesToKeep =
          Set.copyOf(AppConfig.getApplicationConfig().getStringList("xptools.executables.all"));
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
      throw new IllegalStateException("Failed to auto-prepare XPTools", e);
    }
  }
}
