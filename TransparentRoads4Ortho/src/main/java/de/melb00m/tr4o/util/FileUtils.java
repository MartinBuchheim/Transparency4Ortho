package de.melb00m.tr4o.util;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class FileUtils {

  private static final Logger LOG = LogManager.getLogger(FileUtils.class);

  private FileUtils() {}

  /**
   * Copies all files and directories recursively from <code>source</code> to <code>target</code>,
   * except paths that are given as <code>exclusions</code>.
   *
   * @param source Source path
   * @param target Target path
   * @param exclusions Excluded files or directories
   * @throws IOException
   */
  public static void copyRecursively(final Path source, final Path target, final Path... exclusions)
      throws IOException {
    final var exclusionPaths = Set.copyOf(Arrays.asList(exclusions));
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (exclusionPaths.contains(dir)) {
              LOG.trace("Directory {} is in list of exclusions, skipping", dir);
              return FileVisitResult.SKIP_SUBTREE;
            }
            final var targetDir = target.resolve(source.relativize(dir));
            if (!Files.exists(targetDir)) {
              LOG.trace("Creating directory source {} target {}", dir, targetDir);
              Files.createDirectory(targetDir);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!exclusionPaths.contains(file)) {
              final var targetFile = target.resolve(source.relativize(file));
              LOG.trace("Copying file {} target {}", file, targetFile);
              Files.copy(file, targetFile);
            } else {
              LOG.trace("File {} is in list of exclusions, skipping", file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Searches the given <code>searchDir</code> for files named like one of the given <code>fileNames
   * </code> and returns their paths mapped to the filename.
   *
   * @param searchDir Directory to search in
   * @param fileNames Filenames to look for
   * @return Matching file-paths mapped against the lookup filename
   * @throws IOException
   */
  public static MultiValuedMap<String, Path> searchFileNamesRecursively(
      final Path searchDir, Collection<String> fileNames) throws IOException {
    final var searchNames = Set.copyOf(fileNames);
    final var matches = new HashSetValuedHashMap<String, Path>();
    if (!Files.exists(searchDir)) {
        LOG.trace("Skipping saerch for file-names '{}', as directory {} does not exist", fileNames, searchDir);
        return matches;
    }
    LOG.trace("Looking for file-names '{}' in folder {}...", fileNames, searchNames);
    Files.walkFileTree(
        searchDir,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            final var fileName = file.getFileName().toString();
            if (searchNames.contains(fileName)) {
              LOG.trace("Found matching file at {}", file);
              matches.put(fileName, file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return matches;
  }

  public static String extractFileNameFromPath(final String path) {
      var idx = path.lastIndexOf('/');
      return idx > 0 ? path.substring(idx + 1) : path;
  }
}
