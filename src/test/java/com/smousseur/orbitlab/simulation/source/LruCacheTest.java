package com.smousseur.orbitlab.simulation.source;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LruCacheTest {

  @Test
  void get_missingKey_returnsNull() {
    LruCache<String, Integer> cache = new LruCache<>(3);
    assertNull(cache.get("absent"));
  }

  @Test
  void put_thenGet_returnsValue() {
    LruCache<String, Integer> cache = new LruCache<>(3);
    cache.put("a", 1);
    assertEquals(1, cache.get("a"));
  }

  @Test
  void put_overwritesExistingKey() {
    LruCache<String, Integer> cache = new LruCache<>(3);
    cache.put("a", 1);
    cache.put("a", 42);
    assertEquals(42, cache.get("a"));
  }

  @Test
  void containsKey_presentKey_returnsTrue() {
    LruCache<String, Integer> cache = new LruCache<>(3);
    cache.put("x", 99);
    assertTrue(cache.containsKey("x"));
  }

  @Test
  void containsKey_absentKey_returnsFalse() {
    LruCache<String, Integer> cache = new LruCache<>(3);
    assertFalse(cache.containsKey("missing"));
  }

  @Test
  void eviction_leastRecentlyUsed_isEvicted() {
    // capacity = 2: put a, b → then put c → 'a' (LRU) is evicted
    LruCache<String, Integer> cache = new LruCache<>(2);
    cache.put("a", 1);
    cache.put("b", 2);
    cache.put("c", 3); // 'a' is the LRU entry; it should be evicted
    assertFalse(cache.containsKey("a"), "'a' should have been evicted");
    assertTrue(cache.containsKey("b"));
    assertTrue(cache.containsKey("c"));
  }

  @Test
  void accessOrder_getPromotesEntry_preventingEviction() {
    // capacity = 2: put a, b → access a (makes b LRU) → put c → 'b' is evicted
    LruCache<String, Integer> cache = new LruCache<>(2);
    cache.put("a", 1);
    cache.put("b", 2);
    cache.get("a"); // promote 'a'; now 'b' is LRU
    cache.put("c", 3); // 'b' should be evicted
    assertTrue(cache.containsKey("a"), "'a' was recently accessed and should survive");
    assertFalse(cache.containsKey("b"), "'b' is now LRU and should be evicted");
    assertTrue(cache.containsKey("c"));
  }

  @Test
  void exactlyAtCapacity_noEviction() {
    LruCache<Integer, Integer> cache = new LruCache<>(3);
    cache.put(1, 10);
    cache.put(2, 20);
    cache.put(3, 30);
    assertTrue(cache.containsKey(1));
    assertTrue(cache.containsKey(2));
    assertTrue(cache.containsKey(3));
  }

  @Test
  void singleCapacity_evictsOnSecondPut() {
    LruCache<String, Integer> cache = new LruCache<>(1);
    cache.put("a", 1);
    cache.put("b", 2);
    assertFalse(cache.containsKey("a"));
    assertTrue(cache.containsKey("b"));
    assertEquals(2, cache.get("b"));
  }
}
