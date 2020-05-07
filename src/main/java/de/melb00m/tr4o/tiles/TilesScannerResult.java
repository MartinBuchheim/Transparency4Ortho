package de.melb00m.tr4o.tiles;

import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.misc.LazyAttribute;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.collections4.multimap.UnmodifiableMultiValuedMap;

import java.nio.file.Path;
import java.util.Collection;
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

  private final MultiValuedMap<Path, Path> orthoFolderToDsfMap;
  private final LazyAttribute<MultiValuedMap<Tile, Path>> tileToOrthoDsfMap;

  TilesScannerResult(final MultiValuedMap<Path, Path> orthoFolderToDsfMap) {
    this.orthoFolderToDsfMap = MultiMapUtils.unmodifiableMultiValuedMap(orthoFolderToDsfMap);
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
  public MultiValuedMap<Path, Path> getOrthoFolderToDsfMap() {
    return orthoFolderToDsfMap;
  }
}
