package de.melb00m.tr4o.helper;

import de.melb00m.tr4o.exceptions.Exceptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Various helpers for file-based operations that make file handling possible in lambdas due to not
 * declaring any {@link IOException}s.
 *
 * @author Martin Buchheim
 */
public final class FileHelper {

  public static final Set<FileVisitOption> FOLLOW_SYMLINKS =
      Collections.singleton(FileVisitOption.FOLLOW_LINKS);
  private static final Logger LOG = LogManager.getLogger(FileHelper.class);

  private FileHelper() {}

  public static void copyRecursively(
      final Path source, final Path target, final Path... exclusions) {
    final var exclusionSet = Set.of(exclusions);
    try (var stream = Files.walk(source)) {
      for (final var fileToCopy : stream.filter(Files::isRegularFile).collect(Collectors.toSet())) {
        if (exclusionSet.contains(fileToCopy)) {
          LOG.trace("Skipping copy of file as it is excluded: {}", fileToCopy);
          continue;
        }
        final var targetPath = target.resolve(source.relativize(fileToCopy));
        if (Files.exists(targetPath)) {
          LOG.warn(
              "File will {} not be copied to {}, as a file with that name already exists",
              fileToCopy,
              targetPath);
          continue;
        }
        Files.createDirectories(targetPath.getParent());
        Files.copy(fileToCopy, targetPath);
      }
    } catch (IOException ex) {
      throw Exceptions.unrecoverable(ex);
    }
  }

  public static String removeFileExtension(final String path) {
    var idx = path.lastIndexOf('.');
    return idx > 0 ? path.substring(0, idx) : path;
  }

  public static void deleteRecursively(final Path path) {
    if (!Files.exists(path)) {
      return;
    }
    FileHelper.walk(path, 1).filter(inner -> inner != path).forEach(inner -> {
      if (Files.isDirectory(inner)) {
        deleteRecursively(inner);
      } else {
        deleteIfExists(inner);
      }
    });
    deleteIfExists(path);
  }

  private static void deleteIfExists(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  public static String deepMD5Hash(final Path source) {
    try (final var stream = Files.walk(source)) {
      var digest = MessageDigest.getInstance("MD5");
      stream.filter(Files::isRegularFile).forEachOrdered(file -> digest.update(readAllBytes(file)));
      return OutputHelper.bytesToHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  public static byte[] readAllBytes(final Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  public static Stream<Path> walk(final Path path, int maxDepth) {
    try {
      return Files.walk(path, maxDepth);
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  public static Stream<Path> walk(final Path path) {
    try {
      return Files.walk(path);
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }
}
