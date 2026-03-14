package com.smousseur.orbitlab.simulation.source;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple bounded Least Recently Used (LRU) cache backed by a {@link LinkedHashMap} in
 * access-order mode.
 *
 * <p>When the cache exceeds its maximum size, the least recently accessed entry is automatically
 * evicted. This cache is not thread-safe; external synchronization is required for concurrent
 * access.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values
 */
final class LruCache<K, V> {
  private final int maxSize;
  private final LinkedHashMap<K, V> map;

  /**
   * Creates a new LRU cache with the specified maximum capacity.
   *
   * @param maxSize the maximum number of entries the cache will hold before evicting
   */
  LruCache(int maxSize) {
    this.maxSize = maxSize;
    this.map =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > LruCache.this.maxSize;
          }
        };
  }

  /**
   * Returns the value associated with the given key, or {@code null} if the key is not present.
   * Accessing an entry marks it as the most recently used.
   *
   * @param k the key to look up
   * @return the cached value, or {@code null} if absent
   */
  V get(K k) {
    return map.get(k);
  }

  /**
   * Returns {@code true} if the cache contains an entry for the given key.
   *
   * @param k the key to check
   * @return {@code true} if the key is present in the cache
   */
  boolean containsKey(K k) {
    return map.containsKey(k);
  }

  /**
   * Inserts or updates an entry in the cache. If the cache exceeds its maximum size after
   * insertion, the least recently used entry is evicted.
   *
   * @param k the key
   * @param v the value to associate with the key
   */
  void put(K k, V v) {
    map.put(k, v);
  }
}
