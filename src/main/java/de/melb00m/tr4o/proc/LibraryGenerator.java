package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.Exceptions;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
  private final Set<Path> modifyUncommentRoadFiles;

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
    this.modifyUncommentRoadFiles =
        AppConfig.getApplicationConfig()
            .getStringList("libgen.modifications.roads.uncomment.target-files").stream()
            .map(xplanePath::resolve)
            .collect(Collectors.toUnmodifiableSet());
  }

  public boolean validateOrCreateLibrary() {
    synchronized (this) {
      try {
        if (Files.exists(libraryDefinitionFile)) {
          validateExistingLibrary();
          return false;
        }
        createLibrary();
        return true;
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format("There was a problem initializing the library at: %s", libraryFolder), e);
      }
    }
  }

  public void regenerateLibrary() {
    LOG.info("Regenerating library at: {}", libraryFolder);
    try {
      if (Files.exists(libraryFolder)) {
        LOG.debug("Deleting existing library at: {}", libraryFolder);
        FileHelper.deleteRecursively(libraryFolder, Collections.emptySet());
      }
      createLibrary();
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Failed to regenerate library at: %s", libraryFolder), e);
    }
  }

  private void createLibrary() throws IOException {
    Validate.isTrue(
        Files.exists(roadsLibrarySourceFolder),
        "Can't find X-Plane default roads-library at expected location: %s",
        roadsLibrarySourceFolder);
    validateRoadsLibraryChecksum();
    copyLibraryFolder();
    applyLibraryModifications();
    generateLibraryTxt();
  }

  private void applyLibraryModifications() {
    if (AppConfig.getRunArguments().isSkipLibraryModifications()) {
      LOG.info("Skipping automatic library modifications");
      return;
    }
    LOG.info("Applying modifications for transparent roads");
    final var uncommentStartingWith =
        AppConfig.getApplicationConfig()
            .getStringList("libgen.modifications.roads.uncomment.lines-starting-with").stream()
            .collect(Collectors.toUnmodifiableSet());
    final var groupPattern =
        Pattern.compile(
            AppConfig.getApplicationConfig()
                .getString("libgen.modifications.roads.uncomment.groups-regex"));
    final var uncommentEnabledGroups =
        AppConfig.getApplicationConfig()
            .getStringList("libgen.modifications.roads.uncomment.groups-enabled").stream()
            .collect(Collectors.toUnmodifiableSet());
    try {
      for (final var fileToModify : modifyUncommentRoadFiles) {
        if (!Files.isWritable(fileToModify)) {
          throw new IllegalStateException(
              String.format("File not writeable for modification: %s", fileToModify));
        }
        final var lines = Files.readAllLines(fileToModify);
        final var newLines = new ArrayList<String>(lines.size());
        final var groups = new ArrayList<String>();
        var uncommentEnabledBlock = false;
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
          final var line = lines.get(lineNo);
          var matcher = groupPattern.matcher(line);
          if (matcher.matches()) {
            final var groupName = matcher.group("groupName");
            groups.add(groupName);
            uncommentEnabledBlock = uncommentEnabledGroups.contains(groupName);
          }
          final var uncomment =
              uncommentEnabledBlock && uncommentStartingWith.stream().anyMatch(line::startsWith);
          final var newLine = uncomment ? String.format("#(Transparency4Ortho) %s", line) : line;
          if (uncomment) {
            LOG.trace(
                "Line {} in {} changed from '{}' to '{}'", lineNo + 1, fileToModify, line, newLine);
          }
          newLines.add(newLine);
        }
        LOG.trace("Groups identified in file {}: {}", fileToModify, groups);
        if (!Objects.equals(lines, newLines)) {
          Files.write(fileToModify, newLines);
          LOG.debug("Modifications saved in {}", fileToModify);
        }
      }
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
  }

  private void generateLibraryTxt() throws IOException {
    LOG.info("Generating library at {}", libraryDefinitionFile);
    Validate.isTrue(
        Files.notExists(libraryDefinitionFile),
        "Can't create new library.txt at %s: it already exists",
        libraryDefinitionFile);

    // find our exported files in the folder
    final var filesToExport =
        FileHelper.searchFileNamesRecursively(
            roadLibraryTargetFolder, roadsLibraryExportDefinitions, Collections.emptySet());
    roadsLibraryExportDefinitions.forEach(
        export -> {
          Validate.isTrue(
              filesToExport.containsKey(export),
              "No file named '%s' for export in library.txt found in library: %s",
              export,
              roadLibraryTargetFolder);
          Validate.isTrue(
              filesToExport.get(export).size() == 1,
              "More than one file named '%s' found in copied library: %s",
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
    Files.createDirectories(roadLibraryTargetFolder);
    FileHelper.copyRecursively(
        roadsLibrarySourceFolder,
        roadLibraryTargetFolder,
        Collections.emptySet(),
        roadsLibraryExcludes.toArray(new Path[0]));
  }

  private void validateRoadsLibraryChecksum() {
    final var crcSource = FileHelper.deepCrc32(roadsLibrarySourceFolder);
    final var crcExpected =
        AppConfig.getApplicationConfig().getLong("libgen.resources.roads.checksum");
    if (!Objects.equals(crcSource, crcExpected)) {
      LOG.debug(
          "X-Plane roads library has checksum of {}, but {} is expected", crcSource, crcExpected);
      LOG.warn(
          "The standard X-Plane roads library at '{}' seems to have been modified.",
          roadsLibrarySourceFolder);
      LOG.warn(
          "If you have made changes to this library, it is recommended to revert to the original state before proceeding.");
      Validate.isTrue(
          AppConfig.getRunArguments().isIgnoreChecksumErrors(),
          "Aborting. Use '-i' parameter if you are really sure you want to skip this error.");
    }
  }

  private void validateExistingLibrary() throws IOException {
    LOG.info("Verifying that the existing library at {} can be used", libraryFolder);
    Validate.isTrue(
        Files.isReadable(libraryDefinitionFile),
        "Can't read the 'library.txt' file in the existing library: %s",
        libraryDefinitionFile);
    final var containedLines = Files.readAllLines(libraryDefinitionFile);
    roadsLibraryExportDefinitions.forEach(
        exp -> {
          final var expSearch = String.format(EXPORT_DIRECTIVE, libraryPrefix, exp, "");
          Validate.isTrue(
              containedLines.stream().anyMatch(line -> line.startsWith(expSearch)),
              "Could not find expected export '%s' in existing library: %s",
              expSearch,
              libraryDefinitionFile);
        });
  }

  private String buildExportDirective(final String exportName, final Path fileLocation) {
    final var relativePath = libraryFolder.relativize(fileLocation).toString().replace('\\', '/');
    return String.format(EXPORT_DIRECTIVE, libraryPrefix, exportName, relativePath);
  }

  public Path getLibraryFolder() {
    return libraryFolder;
  }
}
