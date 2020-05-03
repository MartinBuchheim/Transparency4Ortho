package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.Exceptions;
import de.melb00m.tr4o.helper.ProgressBarHelper;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.compare.ObjectToStringComparator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OverlayScanner {

  private static final Logger LOG = LogManager.getLogger(OverlayScanner.class);
  private static final String EARTH_NAV_DATA = "Earth nav data";
  private static final Pattern OVERLAY_DSF_FILENAME_PATTERN =
      Pattern.compile(
          AppConfig.getApplicationConfig().getString("overlay-scanner.regex.dsf-filename"),
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ORTHO_TEXTURE_FILENAME_PATTERN =
      Pattern.compile(
          AppConfig.getApplicationConfig().getString("overlay-scanner.regex.ortho-dds-filename"),
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SCENERY_PACK_ENTRY_PATTERN =
      Pattern.compile(
          AppConfig.getApplicationConfig().getString("overlay-scanner.regex.scenery-pack-entry"));

  private final Set<Path> overlayDirectories;
  private final Path customSceneryDir;
  private final Path xPlaneRootDir;
  private final String libraryPrefix;

  public OverlayScanner() {
    this.overlayDirectories = AppConfig.getRunArguments().getOverlayPaths();
    this.customSceneryDir = AppConfig.getRunArguments().getXPlanePath().resolve("Custom Scenery");
    this.xPlaneRootDir = AppConfig.getRunArguments().getXPlanePath();
    this.libraryPrefix =
        AppConfig.getApplicationConfig().getString("overlay-scanner.library-prefix");
  }

  private static String getDsfFileName(final Path path) {
    return path.getFileName().toString().toLowerCase();
  }

  public void scanAndProcessOverlays() {
    synchronized (this) {
      final var tilesToProcess = findMatchingOrthoTilesForOverlays(findOverlayDSFs());
      if (tilesToProcess.isEmpty()) {
        LOG.info("No tiles found that need to be processed.");
        return;
      }

      final var xpTools = new XPToolsInterface(true);
      final var backupFolder =
          xPlaneRootDir
              .resolve(AppConfig.getApplicationConfig().getString("overlay-scanner.backup-folder"))
              .resolve(new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()));
      final var transformers =
          tilesToProcess.stream()
              .map(tile -> new OverlayTileTransformer(tile, backupFolder, libraryPrefix, xpTools))
              .collect(Collectors.toList());
      final var pbb =
          AppConfig.getRunArguments().getConsoleLogLevel().isMoreSpecificThan(Level.TRACE)
              ? ProgressBarHelper.getPrecondiguredBuilder()
              : ProgressBarHelper.getPreconfiguredLoggedBuilder(LOG);
      ProgressBar.wrap(transformers.parallelStream(), pbb.setTaskName("Adapting Overlays"))
          .forEach(OverlayTileTransformer::runTransformation);

      // check which tiles have been transformed
      final var transformedTiles = transformers.stream()
              .filter(OverlayTileTransformer::isTransformed)
              .map(OverlayTileTransformer::getDsfFile)
              .sorted(ObjectToStringComparator.INSTANCE)
              .collect(Collectors.toList());
      LOG.debug("Overlays have been adapted for transparency: {}", () -> transformedTiles);
      LOG.info("{} Overlay-Tiles have been transformed for transparency", transformedTiles.size());
    }
  }

  private Set<Path> findOverlayDSFs() {
    final var dsfPaths = new HashSet<Path>();
    overlayDirectories.stream()
        .map(baseDir -> baseDir.resolve(EARTH_NAV_DATA))
        .forEach(baseDir -> dsfPaths.addAll(getDsfFilesFromPath(baseDir)));
    LOG.debug(
        "DSFs found in the given Overlay-directories: {}",
        () ->
            dsfPaths.stream()
                .map(Path::getFileName)
                .sorted(ObjectToStringComparator.INSTANCE)
                .toArray());
    LOG.info("{} tile-DSFs detected in given Overlay-directories", dsfPaths.size());
    return Collections.unmodifiableSet(dsfPaths);
  }

  private Set<Path> findMatchingOrthoTilesForOverlays(final Set<Path> overlayTiles) {
    // map the DSF name against the overlay tiles
    final var overlayTileNames = new HashSetValuedHashMap<String, Path>(overlayTiles.size());
    overlayTiles.forEach(tile -> overlayTileNames.put(getDsfFileName(tile), tile));

    // Read all enabled scenery-entries from scenery-pack file
    final var sceneryPacksFile = customSceneryDir.resolve("scenery_packs.ini");
    Validate.isTrue(
        Files.exists(sceneryPacksFile),
        "No scenery_packs.ini found at expected location: {}",
        sceneryPacksFile);

    // identify Ortho-tiles in the scenery-directories and check which ones are also contained in
    // overlays
    try {
      LOG.info("Identifying Ortho-Tiles");
      final var sceneryPaths =
          Files.readAllLines(sceneryPacksFile).stream()
              .map(SCENERY_PACK_ENTRY_PATTERN::matcher)
              .filter(Matcher::matches)
              .map(match -> Paths.get(match.group("scenerypath")))
              .map(path -> path.isAbsolute() ? path : xPlaneRootDir.resolve(path))
              .collect(Collectors.toSet());

      final var orthoDirectories =
          (AppConfig.getRunArguments().getConsoleLogLevel().isMoreSpecificThan(Level.TRACE)
                  ? ProgressBar.wrap(
                      sceneryPaths.stream(),
                      ProgressBarHelper.getPrecondiguredBuilder().setTaskName("Searching Orthos"))
                  : sceneryPaths.stream())
              .filter(this::isPotentialOrthoTilesDirectory)
              .collect(Collectors.toSet());

      LOG.debug(
          "Directories that have been detected as Ortho-directories: {}",
          () ->
              orthoDirectories.stream()
                  .map(ortho -> ortho.getFileName().toString())
                  .collect(Collectors.joining(", ")));

      final var orthoDsfs =
          orthoDirectories.stream()
              .map(ortho -> ortho.resolve(EARTH_NAV_DATA))
              .map(this::getDsfFilesFromPath)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      LOG.debug(
          "DSFs found in the Ortho-directories: {}",
          () ->
              orthoDsfs.stream()
                  .map(Path::getFileName)
                  .sorted(ObjectToStringComparator.INSTANCE)
                  .toArray());

      final var overlayTilesWithMatchingOrtho =
          orthoDsfs.stream()
              .map(OverlayScanner::getDsfFileName)
              .filter(overlayTileNames::containsKey)
              .flatMap(dsfName -> overlayTileNames.get(dsfName).stream())
              .collect(Collectors.toSet());

      LOG.debug(
          "Tiles that have matching Overlays AND Orthos: {}",
          () ->
              overlayTilesWithMatchingOrtho.stream()
                  .map(Path::getFileName)
                  .sorted(ObjectToStringComparator.INSTANCE)
                  .toArray());
      LOG.info(
          "{} tiles found that have Overlays AND Orthos present",
          overlayTilesWithMatchingOrtho::size);

      return overlayTilesWithMatchingOrtho;
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
  }

  private Set<Path> getDsfFilesFromPath(final Path source) {
    try (final var stream = Files.walk(source)) {
      return stream
          .filter(
              path -> OVERLAY_DSF_FILENAME_PATTERN.matcher(path.getFileName().toString()).matches())
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
  }

  private boolean isPotentialOrthoTilesDirectory(final Path dir) {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    if (Files.exists(dir.resolve("Transparency4Ortho.exclude"))) {
      LOG.trace("{} is NOT an Ortho-folder as it has an 'Transparency4Ortho.exclude' file", dir);
    }
    if (Files.exists(dir.resolve("Transparency4Ortho.include"))) {
      LOG.trace("{} is an Ortho-folder as it has an 'Transparency4Ortho.include' file", dir);
    }
    if (overlayDirectories.contains(dir)) {
      LOG.trace("{} is NOT an Ortho-folder as it is part of the given overlay-directories", dir);
      return false;
    }
    if (Files.exists(dir.resolve("library.txt"))) {
      LOG.trace("{} is NOT an Ortho-folder as it contains a 'library.txt' file", dir);
      return false;
    }
    if (!Files.isDirectory(dir.resolve(EARTH_NAV_DATA))) {
      LOG.trace("{} is NOT an Ortho-folder as it does not contain 'Earth nav data' folder", dir);
      return false;
    }
    // Ortho tiles never have an "apt.dat" file
    if (Files.exists(dir.resolve(EARTH_NAV_DATA).resolve("apt.dat"))) {
      LOG.trace("{} is NOT an Ortho-folder as it has an 'apt.dat' file", dir);
      return false;
    }
    // Check for well-known Ortho folder-names
    if (dir.getFileName().toString().startsWith("zOrtho4XP_")) {
      LOG.trace("{} is likely an Ortho-folder due to it's directory-name", dir);
      return true;
    }
    if (dir.getFileName().toString().startsWith("zPhotoXP_")) {
      LOG.trace("{} is likely an Ortho-folder due to it's directory-name", dir);
      return true;
    }
    // Ortho4XP.cfg is also an indicator for an Ortho-tile
    if (Files.exists(dir.resolve("Ortho4XP.cfg"))) {
      LOG.trace("{} is likely an Ortho-folder as it contains an 'Ortho4XP.cfg' file", dir);
      return true;
    }
    // This last check is a little more expensive, as we check for DDS tile-textures
    if (Files.isDirectory(dir.resolve("textures"))) {
      try (final var textureStream = Files.walk(dir.resolve("textures"), 1)) {
        final Set<Path> ddsTextures =
            textureStream
                .filter(file -> file.getFileName().toString().toLowerCase().endsWith("dds"))
                .collect(Collectors.toSet());
        if (!ddsTextures.isEmpty()
            && ddsTextures.stream()
                .allMatch(
                    dds ->
                        ORTHO_TEXTURE_FILENAME_PATTERN
                            .matcher(dds.getFileName().toString())
                            .matches())) {
          LOG.trace(
              "{} is likely an Ortho-fodler because all it's DDS-textures match the typical filename-format",
              dir);
          return true;
        }
        LOG.trace(
            "{} is likely NOT an Ortho-folder, as some of its DDS-textures do not match the typical filename-format",
            dir);
        return false;
      } catch (IOException e) {
        throw Exceptions.uncheck(e);
      }
    }
    LOG.trace(
        "{} is likely NOT an Ortho-folder, as nothing was found that would indicate it was", dir);
    return false;
  }
}
