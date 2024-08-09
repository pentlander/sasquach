package com.pentlander.sasquach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class Util {
  private Util() {
  }

  public static <T> List<T> concat(List<T> first, List<T> second) {
    var newList = new ArrayList<T>(first.size() + second.size());
    newList.addAll(first);
    newList.addAll(second);
    return Collections.unmodifiableList(newList);
  }

  public static <T> List<T> conj(List<T> list, T value) {
    var newList = new ArrayList<T>(list.size() + 1);
    newList.addAll(list);
    newList.add(value);
    return Collections.unmodifiableList(newList);
  }

  public static <T, K, U> Collector<T, ?, LinkedHashMap<K, U>> toLinkedMap(
      Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper) {
    return Collectors.toMap(keyMapper, valueMapper, (a, b) -> {
      throw new IllegalStateException();
    }, LinkedHashMap::new);
  }

  public static <K, V> SequencedMap<K, V> seqMap() {
    return Collections.unmodifiableSequencedMap(new LinkedHashMap<>());
  }

  public static <K, V> SequencedMap<K, V> seqMap(K key, V value) {
    var map = new LinkedHashMap<K, V>();
    map.put(key, value);
    return Collections.unmodifiableSequencedMap(map);
  }

  public static <K, V> SequencedMap<K, V> unsafeSeqMap(Map<K, V> baseMap) {
    var map = new LinkedHashMap<>(baseMap);
    return Collections.unmodifiableSequencedMap(map);
  }

//  public static String toStr(Record record) throws IllegalAccessException {
//    var lookup = MethodHandles.lookup();
//    for (var recordComponent : record.getClass().getRecordComponents()) {
//      var method = recordComponent.getAccessor();
//      var handle = lookup.unreflect(method);
//      MethodHandles.
//
//    }
//
//  }
}

