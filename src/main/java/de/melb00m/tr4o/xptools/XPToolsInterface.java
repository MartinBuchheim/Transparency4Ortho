package de.melb00m.tr4o.xptools;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.misc.Verify;
import org.apache.commons.lang3.SystemUtils;
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
import java.util.stream.Collectors;

/**
 * Interface-class for accessing functionalities of the DSFTool from the X-Plane developer toolkit.
 *
 * <p>Allows easy usage of DSFTool functionality using the {@link #dsfToText(Path)} and {@link
 * #textToDsf(Path, String)} methods.
 *
 * <p>Required the DSFTool-binary for the current operating system to be present, obviously. If the
 * binary-location is not provided by the user, an attempt to download the tool automatically from
 * the X-Plane developer website is made the first time the tool is required.
 *
 * @author Martin Buchheim
 */
public class XPToolsInterface {

  private static final Logger LOG = LogManager.getLogger(XPToolsInterface.class);

  private final Transparency4Ortho command;
  private final Path dsfExecutable;
  private final Path temporaryFolder;
  private final Path binariesFolder;

  public XPToolsInterface(final Transparency4Ortho command) {
    this.command = command;
    this.temporaryFolder =
        FileHelper.createAutoCleanedTempDir(
            command
                .getApplicationFolder()
                .resolve(command.config().getString("xptools.tmp-folder")),
            Optional.empty());
    this.binariesFolder =
        command.getApplicationFolder().resolve(command.config().getString("xptools.bin-folder"));
    this.dsfExecutable = prepareDsfExecutable();
  }

  private Path prepareDsfExecutable() {
    synchronized (XPToolsInterface.class) {
      // try to fetch from run-args first
      var dsfToolExecutable = command.getDsfToolExecutable();
      // if no run-arg given, try to use DSFtool in binaries folder, or attempt auto download
      if (dsfToolExecutable.isEmpty()) {
        final var dsfExecutableNames =
            command.config().getStringList("xptools.executables.dsftool");
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
              Verify.withErrorMessage("DSFTool is not executable: %s", exec)
                  .state(Files.isExecutable(exec)));
      return dsfToolExecutable.orElseThrow(
          () -> new IllegalStateException("Failed to retrieve DSFTool"));
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
    Verify.withErrorMessage("XPTools not found and automatic download disabled")
        .state(!command.isForbidAutoDownload());
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
          Set.copyOf(command.config().getStringList("xptools.executables.all"));
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

  private URL getDownloadUrlForOS() throws MalformedURLException {
    if (SystemUtils.IS_OS_WINDOWS) {
      return new URL(command.config().getString("xptools.autodownload.url.win"));
    }
    if (SystemUtils.IS_OS_MAC) {
      return new URL(command.config().getString("xptools.autodownload.url.mac"));
    }
    if (SystemUtils.IS_OS_LINUX) {
      return new URL(command.config().getString("xptools.autodownload.url.linux"));
    }
    throw new IllegalStateException("No URL for auto-download of XPTools for current OS available");
  }

  /**
   * Converts the file at the given {@code dsfFile}-path to a textural representation using DSFTool
   * and returns it.
   *
   * @param dsfFile Path to DSF-file
   * @return Textual representation of the file
   * @throws IOException I/O errors
   */
  public String dsfToText(final Path dsfFile) throws IOException {
    Verify.withErrorMessage("Given DSF file is not readable: %s,", dsfFile)
        .argument(Files.isReadable(dsfFile));
    final var process =
        new ProcessBuilder(toProcessFile(dsfExecutable), "--dsf2text", toProcessFile(dsfFile), "-")
            .start();
    try (final var input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      return input.lines().collect(Collectors.joining(System.lineSeparator()));
    }
  }

  private static String toProcessFile(final Path path) {
    return path.toAbsolutePath().toString();
  }

  /**
   * Converts the given {@code contents} to DSF using DSFTool and stores it at the path given in
   * {@code targetFile}.
   *
   * <p>If a file at the given target-path already exists, an {@link IllegalArgumentException} is
   * triggered.
   *
   * @param targetFile Target-Path for DSF-file
   * @param contents Textual representation to be converted to DSF
   * @throws IOException I/O errors
   */
  public void textToDsf(final Path targetFile, final String contents) throws IOException {
    Verify.withErrorMessage("Won't override existing file at: %s", targetFile)
        .argument(!Files.exists(targetFile));
    final var process =
        new ProcessBuilder(
                toProcessFile(dsfExecutable), "--text2dsf", "-", toProcessFile(targetFile))
            .start();
    try (var osw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
      osw.write(contents);
      osw.flush();
    }
  }
}
