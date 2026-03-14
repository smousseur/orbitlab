package com.smousseur.orbitlab.simulation.source;

import java.util.LinkedHashMap;
import java.util.Map;

final class LruCache<K, V> {
  private final int maxSize;
  private final LinkedHashMap<K, V> map;

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

  V get(K k) {
    return map.get(k);
  }

  boolean containsKey(K k) {
    return map.containsKey(k);
  }

  void put(K k, V v) {
    map.put(k, v);
  }
}
