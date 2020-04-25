package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.util.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LibraryGenerator {

  private static final Logger LOG = LogManager.getLogger(LibraryGenerator.class);
  private static final String LIBRARY_TXT = "library.txt";
  private static final String EXPORT_DIRECTIVE = "EXPORT_EXCLUDE %s/%s %s";
  private static final List<String> LIB_TXT_HEADERS = List.of("A", "800", "LIBRARY", "");
  private static final Set<String> LIB_COPY_EXCLUSIONS = Set.of("library.lib", LIBRARY_TXT);

  private final String libraryPrefix;
  private final Path libraryFolder;
  private final Path libraryResourcesFolder;
  private final Path xPlaneSourceLibraryFolder;
  private final Set<String> exportedFiles;

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
    this.exportedFiles = Set.copyOf(exportedFiles);
  }

  public void validateOrCreateLibrary() {
    synchronized (this) {
      try {
        if (Files.exists(libraryFolder)) {
          validateExistingLibrary();
        } else {
          copyLibraryFolder();
          generateLibraryTxt();
        }
      } catch (IOException e) {
        throw new UndeclaredThrowableException(e);
      }
    }
  }

  private void generateLibraryTxt() throws IOException {
    var libraryTxt = libraryFolder.resolve(LIBRARY_TXT);
    LOG.info("Generating library at {}", libraryTxt);
    Validate.isTrue(
        Files.notExists(libraryTxt),
        "Can't create new library.txt at %s: It already exists",
        libraryTxt);

    // find our exported files in the folder
    final var filesToExport =
        FileUtils.searchFileNamesRecursively(libraryResourcesFolder, exportedFiles);
    exportedFiles.forEach(
        export -> {
          Validate.isTrue(
              filesToExport.containsKey(export),
              "No file named '%s' for export in library.txt found in library: {}",
              export,
              libraryResourcesFolder);
          Validate.isTrue(
              filesToExport.get(export).size() == 1,
              "More than one file named '%s' found in copied library: {}",
              export,
              libraryResourcesFolder);
        });

    // Generate the library.txt content-lines
    final var libLines = new ArrayList<>(LIB_TXT_HEADERS);
    filesToExport.entries().stream()
        .map(entry -> buildExportDirective(entry.getKey(), entry.getValue()))
        .forEach(libLines::add);

    Files.write(libraryTxt, libLines);
  }

  private void copyLibraryFolder() throws IOException {
    LOG.info("Copying X-Plane default roads-library to {}", libraryFolder);
    final var exclusions =
        LIB_COPY_EXCLUSIONS.stream()
            .map(xPlaneSourceLibraryFolder::resolve)
            .collect(Collectors.toSet());
    Validate.isTrue(
        Files.exists(xPlaneSourceLibraryFolder),
        "Can't find X-Plane default roads-library at expected location: {}",
        xPlaneSourceLibraryFolder);
    Files.createDirectories(libraryResourcesFolder);
    FileUtils.copyRecursively(
        xPlaneSourceLibraryFolder, libraryResourcesFolder, exclusions.toArray(new Path[0]));
  }

  private void validateExistingLibrary() throws IOException {
    LOG.info("Verifying that the existing library at {} can be used...", libraryFolder);
    final var libraryFile = libraryFolder.resolve(LIBRARY_TXT);
    Validate.isTrue(
        Files.isReadable(libraryFile),
        "Can't read the 'library.txt' file in the existing library: {}",
        libraryFile);
    final var containedLines = Files.readAllLines(libraryFile);
    exportedFiles.forEach(
        exp -> {
          final var expSearch = String.format(EXPORT_DIRECTIVE, libraryPrefix, exp, "");
          Validate.isTrue(
              containedLines.stream().anyMatch(line -> line.startsWith(expSearch)),
              "Could not find expected export '{}' in existing library: {}",
              expSearch,
              libraryFile);
        });
  }

  private String buildExportDirective(final String exportName, final Path fileLocation) {
    final var relativePath = libraryFolder.relativize(fileLocation).toString().replace('\\', '/');
    return String.format(EXPORT_DIRECTIVE, libraryPrefix, exportName, relativePath);
  }
}
