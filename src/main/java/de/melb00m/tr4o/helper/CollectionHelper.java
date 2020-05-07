package de.melb00m.tr4o.helper;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tooling for collections
 *
 * @author Martin Buchheim
 */
public final class CollectionHelper {

  private CollectionHelper() {}

  /**
   * Uses the given {@code predicates} to reduce the given {@code map} to entries of which the keys
   * fulfill all predicates.
   *
   * @param map Map to filter
   * @param predicates Predicates for filtering (all must be matched)
   * @param <K> Key Type of map
   * @param <V> Value Type of map
   * @return A map with all entries that fulfill the given predicate(s)
   */
  public static <K, V> Map<K, V> filterMapByKeys(final Map<K, V> map, Predicate<K>... predicates) {
    return map.entrySet().stream()
        .filter(entry -> Arrays.stream(predicates).allMatch(pred -> pred.test(entry.getKey())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
