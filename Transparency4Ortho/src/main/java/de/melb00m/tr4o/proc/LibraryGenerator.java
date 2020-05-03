package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.FileHelper;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LibraryGenerator {

  private static final Logger LOG = LogManager.getLogger(LibraryGenerator.class);
  private static final String EXPORT_DIRECTIVE = "EXPORT_EXCLUDE %s/%s %s";
  private static final List<String> LIB_TXT_HEADERS = List.of("A", "800", "LIBRARY", "");

  private final String libraryPrefix;
  private final Path libraryFolder;
  private final Path libraryDefinitionFile;
  private final Path roadLibraryTargetFolder;
  private final Path roadsLibrarySourceFolder;
  private final Set<Path> roadsLibraryExcludes;
  private final Set<String> roadsLibraryExportDefinitions;

  public LibraryGenerator() {
    final var xplanePath = AppConfig.getRunArguments().getXPlanePath();
    this.libraryPrefix = AppConfig.getApplicationConfig().getString("libgen.library.prefix");
    this.libraryFolder =
        xplanePath.resolve(AppConfig.getApplicationConfig().getString("libgen.library.folder"));
    this.libraryDefinitionFile =
        xplanePath.resolve(
            AppConfig.getApplicationConfig().getString("libgen.library.definition-file"));
    this.roadLibraryTargetFolder =
        xplanePath.resolve(
            AppConfig.getApplicationConfig().getString("libgen.resources.roads.target"));
    this.roadsLibrarySourceFolder =
        xplanePath.resolve(
            AppConfig.getApplicationConfig().getString("libgen.resources.roads.source"));
    this.roadsLibraryExcludes =
        AppConfig.getApplicationConfig()
            .getStringList("libgen.resources.roads.duplication.ignore-files").stream()
            .map(xplanePath::resolve)
            .collect(Collectors.toSet());
    this.roadsLibraryExportDefinitions =
        Set.copyOf(
            AppConfig.getApplicationConfig().getStringList("libgen.resources.roads.exports"));
  }

  public void validateOrCreateLibrary() {
    synchronized (this) {
      try {
        if (Files.exists(libraryDefinitionFile)) {
          validateExistingLibrary();
        } else {
          copyLibraryFolder();
          generateLibraryTxt();
        }
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format("There was a problem initializing the library at: %s", libraryFolder), e);
      }
    }
  }

  private void generateLibraryTxt() throws IOException {
    LOG.info("Generating library at {}", libraryDefinitionFile);
    Validate.isTrue(
        Files.notExists(libraryDefinitionFile),
        "Can't create new library.txt at %s: It already exists",
            libraryDefinitionFile);

    // find our exported files in the folder
    final var filesToExport =
        FileHelper.searchFileNamesRecursively(
            roadLibraryTargetFolder, roadsLibraryExportDefinitions, Collections.emptySet());
    roadsLibraryExportDefinitions.forEach(
        export -> {
          Validate.isTrue(
              filesToExport.containsKey(export),
              "No file named '%s' for export in library.txt found in library: {}",
              export,
              roadLibraryTargetFolder);
          Validate.isTrue(
              filesToExport.get(export).size() == 1,
              "More than one file named '%s' found in copied library: {}",
              export,
              roadLibraryTargetFolder);
        });

    // Generate the library.txt content-lines
    final var libLines = new ArrayList<>(LIB_TXT_HEADERS);
    filesToExport.entries().stream()
        .map(entry -> buildExportDirective(entry.getKey(), entry.getValue()))
        .forEach(libLines::add);

    Files.write(libraryDefinitionFile, libLines);
  }

  private void copyLibraryFolder() throws IOException {
    LOG.info("Copying X-Plane default roads-library to {}", libraryFolder);
    Validate.isTrue(
        Files.exists(roadsLibrarySourceFolder),
        "Can't find X-Plane default roads-library at expected location: {}",
        roadsLibrarySourceFolder);
    Files.createDirectories(roadLibraryTargetFolder);
    FileHelper.copyRecursively(
        roadsLibrarySourceFolder,
        roadLibraryTargetFolder,
        Collections.emptySet(),
        roadsLibraryExcludes.toArray(new Path[0]));
  }

  private void validateExistingLibrary() throws IOException {
    LOG.info("Verifying that the existing library at {} can be used...", libraryFolder);
    Validate.isTrue(
        Files.isReadable(libraryDefinitionFile),
        "Can't read the 'library.txt' file in the existing library: {}",
            libraryDefinitionFile);
    final var containedLines = Files.readAllLines(libraryDefinitionFile);
    roadsLibraryExportDefinitions.forEach(
        exp -> {
          final var expSearch = String.format(EXPORT_DIRECTIVE, libraryPrefix, exp, "");
          Validate.isTrue(
              containedLines.stream().anyMatch(line -> line.startsWith(expSearch)),
              "Could not find expected export '{}' in existing library: {}",
              expSearch,
                  libraryDefinitionFile);
        });
  }

  private String buildExportDirective(final String exportName, final Path fileLocation) {
    final var relativePath = libraryFolder.relativize(fileLocation).toString().replace('\\', '/');
    return String.format(EXPORT_DIRECTIVE, libraryPrefix, exportName, relativePath);
  }
}
