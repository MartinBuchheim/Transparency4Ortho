package de.melb00m.tr4o.tiles;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.misc.Verify;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Class representing a tile
 *
 * @author Martin Buchheim
 */
public class Tile implements Comparable<Tile> {

  private static final Pattern TILE_PATTERN =
      Pattern.compile(Transparency4Ortho.CONFIG.getString("overlay-scanner.tiles.input-pattern"));

  private final String tile;
  private final int latitude;
  private final int longitude;

  /**
   *
   * @param tile Tile in DSF-file format (e.g. {@code +50-103})
   */
  public Tile(final String tile) {
    var matcher = TILE_PATTERN.matcher(tile);
    Verify.withErrorMessage("Invalid tile format: %s", tile).argument(matcher.matches());
    this.tile = tile;
    this.latitude = Integer.parseInt(matcher.group("lat"));
    this.longitude = Integer.parseInt(matcher.group("lon"));
  }

  public int getLatitude() {
    return latitude;
  }

  public int getLongitude() {
    return longitude;
  }

  @Override
  public int hashCode() {
    return Objects.hash(tile);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Tile tile1 = (Tile) o;
    return tile.equals(tile1.tile);
  }

  @Override
  public String toString() {
    return tile;
  }

  @Override
  public int compareTo(final Tile o) {
    return tile.compareTo(o.tile);
  }
}
