package de.melb00m.tr4o.proc;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import de.melb00m.tr4o.util.FileUtils;
import lombok.val;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class LibraryGenerator {

  private static final Logger LOG = LogManager.getLogger(LibraryGenerator.class);
  private static final String LIBRARY_TXT = "library.txt";
  private static final String EXPORT_DIRECTIVE = "EXPORT_EXCLUDE %s/%s %s";
  private static final String[] LIB_TXT_HEADERS = {"A", "800", "LIBRARY", ""};
  private static final ImmutableSet<String> LIB_COPY_EXCLUSIONS =
      ImmutableSet.of("library.lib", LIBRARY_TXT);

  private final String libraryPrefix;
  private final Path libraryFolder;
  private final Path libraryResourcesFolder;
  private final Path xPlaneSourceLibraryFolder;
  private final ImmutableSet<String> exportedFiles;

  public LibraryGenerator(
      String libraryPrefix,
      Path libraryFolder,
      Path libraryResourcesFolder,
      Path resourcesSourceFolder,
      Collection<String> exportedFiles) {
    this.libraryPrefix = libraryPrefix;
    this.libraryFolder = libraryFolder;
    this.libraryResourcesFolder = libraryResourcesFolder;
    this.xPlaneSourceLibraryFolder = resourcesSourceFolder;
    this.exportedFiles = ImmutableSet.copyOf(exportedFiles);
  }

  public void validateOrCreateLibrary() {
    synchronized (this) {
      try {
        if (Files.exists(libraryFolder)) {
          validateExistingLibrary();
          return;
        }

        copyLibraryFolder();
        generateLibraryTxt();

      } catch (IOException e) {
        throw new UndeclaredThrowableException(e);
      }
    }
  }

  private void generateLibraryTxt() throws IOException {
    val libraryTxt = libraryFolder.resolve(LIBRARY_TXT);
    LOG.info("Generating library at {}", libraryTxt);
    Preconditions.checkState(
        Files.notExists(libraryTxt),
        "Can't create library.txt at %s: It already exists",
        libraryTxt);

    // find our exported files in the folder
    val filesToExport = FileUtils.searchFileNamesRecursively(libraryResourcesFolder, exportedFiles);
    exportedFiles.forEach(
        export -> {
          Preconditions.checkState(
              filesToExport.containsKey(export),
              "No '%s' found in copied library for export in library.txt",
              export);
          Preconditions.checkState(
              filesToExport.get(export).size() == 1,
              "More than one '%s' found in copied library",
              export);
        });

    // Generate the library.txt content-lines
    val libLines = Arrays.stream(LIB_TXT_HEADERS).collect(Collectors.toList());
    filesToExport.entries().stream()
        .map(entry -> buildExportDirective(entry.getKey(), entry.getValue()))
        .forEach(libLines::add);

    Files.write(libraryTxt, libLines);
  }

  private void copyLibraryFolder() throws IOException {
    LOG.info("Copying X-Plane default roads-library to {}", libraryFolder);
    val exclusions =
        LIB_COPY_EXCLUSIONS.stream()
            .map(xPlaneSourceLibraryFolder::resolve)
            .collect(Collectors.toList());
    Preconditions.checkState(
        Files.exists(xPlaneSourceLibraryFolder),
        "Can't find X-Plane default roads-library at expected {}",
        xPlaneSourceLibraryFolder);
    Files.createDirectories(libraryResourcesFolder);
    FileUtils.copyRecursively(
        xPlaneSourceLibraryFolder, libraryResourcesFolder, exclusions.toArray(new Path[0]));
  }

  private void validateExistingLibrary() throws IOException {
    LOG.info("Verifying that the existing library at {} can be used...", libraryFolder);
    val libraryFile = libraryFolder.resolve(LIBRARY_TXT);
    Preconditions.checkState(
        Files.isReadable(libraryFile),
        "Can't read the 'library.txt' file in the existing library at {}",
        libraryFile);
    val containedLines = Files.readAllLines(libraryFile);
    exportedFiles.forEach(
        exp -> {
          val expSearch = String.format(EXPORT_DIRECTIVE, libraryPrefix, exp, "");
          Preconditions.checkState(
              containedLines.stream().anyMatch(line -> line.startsWith(expSearch)),
              "Could not find expected export '{}' in existing library {}",
              expSearch,
              libraryFile);
        });
  }

  private String buildExportDirective(final String exportName, final Path fileLocation) {
    val relativePath = libraryFolder.relativize(fileLocation).toString().replace('\\', '/');
    return String.format(EXPORT_DIRECTIVE, libraryPrefix, exportName, relativePath);
  }
}
