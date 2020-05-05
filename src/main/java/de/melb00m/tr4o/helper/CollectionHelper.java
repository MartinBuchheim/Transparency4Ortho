package de.melb00m.tr4o.helper;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CollectionHelper {

  private CollectionHelper() {}

  public static <K, V> Map<K, V> filteredMapKeys(final Map<K, V> map, Predicate<K>... filters) {
    return map.entrySet().stream()
        .filter(entry -> Arrays.stream(filters).allMatch(pred -> pred.test(entry.getKey())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
