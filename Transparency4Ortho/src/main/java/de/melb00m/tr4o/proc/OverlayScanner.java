package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import de.melb00m.tr4o.helper.Exceptions;
import de.melb00m.tr4o.helper.FileHelper;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.compare.ObjectToStringComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
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

  public OverlayScanner() {
    this.overlayDirectories = AppConfig.getRunArguments().getOverlayPaths();
    this.customSceneryDir = AppConfig.getRunArguments().getXPlanePath().resolve("Custom Scenery");
    this.xPlaneRootDir = AppConfig.getRunArguments().getXPlanePath();
  }

  private static String getDsfFileName(final Path path) {
    return path.getFileName().toString().toLowerCase();
  }

  private static Path getSceneryDirectory(final Path dsfPath) {
    var path = dsfPath;
    while (path != null && !Files.exists(path.resolve(EARTH_NAV_DATA))) {
      path = path.getParent();
    }
    return path;
  }

  public MultiValuedMap<Path, Path> scanForOverlaysToTransform() {
    synchronized (this) {
      // get all overlay-tiles covered by Ortho
      return findMatchingOrthoTilesForOverlays(findOverlayDSFs());
    }
  }

  private Set<Path> findOverlayDSFs() {
    final var dsfPaths = new HashSet<Path>();
    overlayDirectories.stream()
        .map(baseDir -> baseDir.resolve(EARTH_NAV_DATA))
        .forEach(baseDir -> dsfPaths.addAll(getDsfFilesFromPath(baseDir)));
    LOG.debug(
        "Tiles found in the given Overlay-directories: {}",
        () ->
            dsfPaths.stream()
                .map(FileHelper::getFilenameWithoutExtension)
                .sorted(ObjectToStringComparator.INSTANCE)
                .toArray());
    LOG.info("{} tiles detected in given Overlay-directories", dsfPaths.size());
    return Collections.unmodifiableSet(dsfPaths);
  }

  private HashSetValuedHashMap<Path, Path> findMatchingOrthoTilesForOverlays(
      final Set<Path> overlayTiles) {
    // map the DSF name against the overlay tiles
    final var overlayTileNames = new HashSetValuedHashMap<String, Path>(overlayTiles.size());
    overlayTiles.forEach(tile -> overlayTileNames.put(getDsfFileName(tile), tile));

    // Read all enabled scenery-entries from scenery-pack file
    final var sceneryPacksFile = customSceneryDir.resolve("scenery_packs.ini");
    Validate.isTrue(
        Files.exists(sceneryPacksFile),
        "No scenery_packs.ini found at expected location: %s",
        sceneryPacksFile);

    // identify Ortho-tiles from the scenery_pack.ini file
    try {
      LOG.info("Identifying Ortho-Tiles (this may take a moment)");
      final var orthoDirectories =
          Files.readAllLines(sceneryPacksFile).stream()
              .map(SCENERY_PACK_ENTRY_PATTERN::matcher)
              .filter(Matcher::matches)
              .map(match -> Paths.get(match.group("scenerypath")))
              .map(path -> path.isAbsolute() ? path : xPlaneRootDir.resolve(path))
              .filter(this::isPotentialOrthoTilesDirectory)
              .collect(Collectors.toUnmodifiableSet());
      LOG.trace(
          "Directories that have been auto-detected as Ortho-directories: {}",
          () -> orthoDirectories.stream().sorted().toArray());
      LOG.info("{} Ortho-scenery directories detected in total", orthoDirectories.size());

      // Seach DSFs in ortho-directories to get covered tiles
      final var orthoDsfs =
          orthoDirectories.stream()
              .map(ortho -> ortho.resolve(EARTH_NAV_DATA))
              .map(this::getDsfFilesFromPath)
              .flatMap(Collection::stream)
              .collect(Collectors.toUnmodifiableSet());
      LOG.trace(
          "DSFs found in the Ortho-directories: {}", () -> orthoDsfs.stream().sorted().toArray());

      // Map overlay-DSFs against ortho-directories that cover that tile
      final var sceneryToOverlayMap = new HashSetValuedHashMap<Path, Path>();
      orthoDsfs.stream()
          .filter(orthoDsf -> overlayTileNames.containsKey(getDsfFileName(orthoDsf)))
          .forEach(
              dsfTile ->
                  overlayTileNames
                      .get(getDsfFileName(dsfTile))
                      .forEach(
                          ovlTile ->
                              sceneryToOverlayMap.put(getSceneryDirectory(dsfTile), ovlTile)));

      return sceneryToOverlayMap;
    } catch (IOException e) {
      throw Exceptions.uncheck(e);
    }
  }

  private Set<Path> getDsfFilesFromPath(final Path source) {
    try (final var stream = Files.walk(source)) {
      return stream
          .filter(
              path -> OVERLAY_DSF_FILENAME_PATTERN.matcher(path.getFileName().toString()).matches())
          .collect(Collectors.toUnmodifiableSet());
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
