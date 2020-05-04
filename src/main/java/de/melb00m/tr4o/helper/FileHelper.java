package de.melb00m.tr4o.helper;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

public final class FileHelper {

  public static final Set<FileVisitOption> FOLLOW_SYMLINKS =
      Collections.singleton(FileVisitOption.FOLLOW_LINKS);
  private static final Logger LOG = LogManager.getLogger(FileHelper.class);

  private FileHelper() {}

  public static List<Path> getAllPathsRecursively(
      final Path source,
      final boolean includeDirectories,
      final Set<FileVisitOption> options,
      final Path... exclusions) {
    if (!Files.exists(source)) {
      return Collections.emptyList();
    }
    final var collector = new ExclusionAwareFileCollector(Set.of(exclusions), includeDirectories);
    try {
      Files.walkFileTree(source, options, Integer.MAX_VALUE, collector);
      return collector.getCollectedFiles();
    } catch (IOException ex) {
      throw Exceptions.uncheck(ex);
    }
  }

  public static void deleteRecursively(
      final Path folder, final Set<FileVisitOption> options, final Path... exclusions) {
    try {
      final var filesToDelete = getAllPathsRecursively(folder, true, options, exclusions);
      Collections.reverse(filesToDelete); // will put the nested elements first
      for (Path path : filesToDelete) {
        Files.deleteIfExists(path);
      }
    } catch (IOException ex) {
      throw Exceptions.uncheck(ex);
    }
  }

  public static void copyRecursively(
      final Path source,
      final Path target,
      final Set<FileVisitOption> options,
      final Path... exclusions) {
    try {
      for (var sourcePath : getAllPathsRecursively(source, true, options, exclusions)) {
        var copyToPath = target.resolve(source.relativize(sourcePath));
        if (copyToPath != target) {
          Files.copy(sourcePath, copyToPath);
        }
      }
    } catch (IOException ex) {
      throw Exceptions.uncheck(ex);
    }
  }

  public static MultiValuedMap<String, Path> searchFileNamesRecursively(
      final Path searchDir,
      final Collection<String> fileNames,
      final Set<FileVisitOption> options,
      final Path... exclusions) {
    final var searchNames = Set.copyOf(fileNames);
    final var matches = new HashSetValuedHashMap<String, Path>();

    getAllPathsRecursively(searchDir, false, options, exclusions).stream()
        .filter(file -> searchNames.contains(file.getFileName().toString()))
        .forEach(match -> matches.put(match.getFileName().toString(), match));
    return matches;
  }

  public static String getFilenameWithoutExtension(final Path path) {
    return removeFileExtension(path.getFileName().toString());
  }

  public static String removeFileExtension(final String path) {
    var idx = path.lastIndexOf('.');
    return idx > 0 ? path.substring(0, idx) : path;
  }

  public static String extractFileNameFromPath(final String path) {
    var idx = path.replace('\\', '/').lastIndexOf('/');
    return idx > 0 ? path.substring(idx + 1) : path;
  }

  public static void downloadFile(URL sourceUrl, Path targetFile) throws IOException {
    try (var downloadStream = Channels.newChannel(sourceUrl.openStream());
        var downloadTargetStream = new FileOutputStream(targetFile.toFile()).getChannel()) {
      LOG.trace("Downloading file from {} to {}...", sourceUrl, targetFile);
      downloadTargetStream.transferFrom(downloadStream, 0, Long.MAX_VALUE);
    }
  }

  public static Path createAutoCleanedTempDir(
      final Path baseFolder, final Optional<String> prefix) {
    try {
      if (!Files.exists(baseFolder)) {
        Files.createDirectories(baseFolder);
      }
      final var tempDir = Files.createTempDirectory(baseFolder, prefix.orElse(null));
      Runtime.getRuntime()
          .addShutdownHook(new Thread(() -> deleteRecursively(tempDir, Collections.emptySet())));
      return tempDir;
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
  }

  public static long deepCrc32(final Path source) {
    Validate.isTrue(
        Files.isReadable(source), "CRC32 not possible, location is not readable: %s", source);
    final var crc = new CRC32();
    try (final var stream = Files.walk(source)) {
      stream
          .sorted()
          .filter(Files::isRegularFile)
          .forEachOrdered(
              file -> {
                try {
                  crc.update(Files.readAllBytes(file));
                } catch (IOException e) {
                  throw Exceptions.uncheck(e);
                }
              });
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
    return crc.getValue();
  }
}
