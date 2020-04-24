package de.melb00m.tr4o.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import lombok.val;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

public class FileUtils {

  private static final Logger LOG = LogManager.getLogger(FileUtils.class);

  private FileUtils() {}

  public static void copyRecursively(final Path from, final Path to, Path... exclusions)
      throws IOException {
    val exclusionPaths = ImmutableSet.copyOf(exclusions);
    Files.walkFileTree(
        from,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (exclusionPaths.contains(dir)) {
              LOG.trace("Directory {} is in list of exclusions, skipping", dir);
              return FileVisitResult.SKIP_SUBTREE;
            }
            val targetDir = to.resolve(from.relativize(dir));
            if (!Files.exists(targetDir)) {
              LOG.trace("Creating directory from {} to {}", dir, targetDir);
              Files.createDirectory(targetDir);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (!exclusionPaths.contains(file)) {
              val targetFile = to.resolve(from.relativize(file));
              LOG.trace("Copying file {} to {}", file, targetFile);
              Preconditions.checkState(
                  Files.notExists(targetFile),
                  "Can't copy file from {}: Another file already exists at {}",
                  file,
                  targetFile);
              Files.copy(file, targetFile);
            } else {
              LOG.trace("File {} is in list of exclusions, skipping", file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  public static Multimap<String, Path> searchFileNamesRecursively(
      final Path searchDir, Collection<String> fileNames) throws IOException {
    val searchNames = new HashSet<>(fileNames);
    val matches = HashMultimap.<String, Path>create();
    LOG.trace("Looking for file-names '{}' in folder {}...", fileNames, searchDir);
    Files.walkFileTree(
        searchDir,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            val fileName = file.getFileName().toString();
            if (searchNames.contains(fileName)) {
              LOG.trace("Found matching file at {}", file);
              matches.put(fileName, file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return matches;
  }
}
