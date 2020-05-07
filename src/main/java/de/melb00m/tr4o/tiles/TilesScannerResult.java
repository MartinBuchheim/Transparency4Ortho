package de.melb00m.tr4o.tiles;

import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.misc.LazyAttribute;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.collections4.multimap.UnmodifiableMultiValuedMap;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Holds the result of ortho-scenery scan performed by {@link TilesScanner#scanForOrthoScenery()}.
 *
 * <p>This class allows structured access to the scanning result.
 *
 * @see TilesScanner#scanForOrthoScenery()
 * @author Martin Buchheim
 */
public class TilesScannerResult {

  private final Map<Path, Path> orthoFolderToDsfMap;
  private final LazyAttribute<MultiValuedMap<Tile, Path>> tileToOrthoDsfMap;

  TilesScannerResult(final Map<Path, Path> orthoFolderToDsfMap) {
    this.orthoFolderToDsfMap = Collections.unmodifiableMap(orthoFolderToDsfMap);
    this.tileToOrthoDsfMap =
        new LazyAttribute<>(() -> extractTileMap(this.orthoFolderToDsfMap.values()));
  }

  private static MultiValuedMap<Tile, Path> extractTileMap(final Collection<Path> paths) {
    final var map = new HashSetValuedHashMap<Tile, Path>();
    paths.forEach(path -> map.put(new Tile(extractTileNameFromDsfFile(path)), path));
    return UnmodifiableMultiValuedMap.unmodifiableMultiValuedMap(map);
  }

  private static String extractTileNameFromDsfFile(final Path path) {
    return FileHelper.removeFileExtension(path.getFileName().toString());
  }

  /**
   * Return all ortho-covered tiles found in the scanner-results
   *
   * @return Ortho covered tiles
   */
  public Set<Tile> getOrthoCoveredTiles() {
    return tileToOrthoDsfMap.get().keySet();
  }

  /** @return Found ortho-DSF files mapped against the ortho-directory */
  public Map<Path, Path> getOrthoFolderToDsfMap() {
    return orthoFolderToDsfMap;
  }
}
