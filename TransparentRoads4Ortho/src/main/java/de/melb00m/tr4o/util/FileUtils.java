package de.melb00m.tr4o.util;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FileUtils {

  private static final Logger LOG = LogManager.getLogger(FileUtils.class);

  private FileUtils() {}

  public static List<Path> getAllPathsRecursively(
      final Path source,
      final boolean includeDirectories,
      final Set<FileVisitOption> options,
      final Path... exclusions)
      throws IOException {
    if (!Files.exists(source)) {
      return Collections.emptyList();
    }
    final var excludedSet = Set.of(exclusions);
    final var files = new ArrayList<Path>();
    Files.walkFileTree(
        source,
        options,
        Integer.MAX_VALUE,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (excludedSet.contains(dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            if (includeDirectories) {
              files.add(dir);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!excludedSet.contains(file)) {
              files.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return files;
  }

  public static void deleteRecursively(
      final Path folder, final Set<FileVisitOption> options, final Path... exclusions)
      throws IOException {
    final var filesToDelete = getAllPathsRecursively(folder, true, options, exclusions);
    Collections.reverse(filesToDelete); // will put the nested elements first
    for (Path path : filesToDelete) {
      Files.deleteIfExists(path);
    }
  }

  public static void copyRecursively(
      final Path source,
      final Path target,
      final Set<FileVisitOption> options,
      final Path... exclusions)
      throws IOException {
    for (var sourcePath : getAllPathsRecursively(source, true, options, exclusions)) {
      var copyToPath = target.resolve(source.relativize(sourcePath));
      Files.copy(sourcePath, copyToPath);
    }
  }

  public static MultiValuedMap<String, Path> searchFileNamesRecursively(
      final Path searchDir,
      final Collection<String> fileNames,
      final Set<FileVisitOption> options,
      final Path... exclusions)
      throws IOException {
    final var searchNames = Set.copyOf(fileNames);
    final var matches = new HashSetValuedHashMap<String, Path>();

    getAllPathsRecursively(searchDir, false, options, exclusions).stream()
        .filter(file -> searchNames.contains(file.getFileName().toString()))
        .forEach(match -> matches.put(match.getFileName().toString(), match));
    return matches;
  }

  public static String extractFileNameFromPath(final String path) {
    var idx = path.lastIndexOf('/');
    return idx > 0 ? path.substring(idx + 1) : path;
  }

  public static void downloadFile(URL sourceUrl, Path targetFile) throws IOException {
    try (var downloadStream = Channels.newChannel(sourceUrl.openStream());
        var downloadTargetStream = new FileOutputStream(targetFile.toFile()).getChannel()) {
      LOG.trace("Downloading file from {} to {}...", sourceUrl, targetFile);
      downloadTargetStream.transferFrom(downloadStream, 0, Long.MAX_VALUE);
    }
  }
}
