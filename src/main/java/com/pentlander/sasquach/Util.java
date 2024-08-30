package com.pentlander.sasquach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class Util {
  private static final SequencedMap<?, ?> EMPTY_SEQ_MAP = Collections.unmodifiableSequencedMap(new LinkedHashMap<>());
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

  @SuppressWarnings("unchecked")
  public static <K, V> SequencedMap<K, V> seqMap() {
    return (SequencedMap<K, V>) EMPTY_SEQ_MAP;
  }

  public static <K, V> SequencedMap<K, V> seqMap(K key, V value) {
    var map = new LinkedHashMap<K, V>();
    map.put(key, value);
    return Collections.unmodifiableSequencedMap(map);
  }

  public static <K, V> SequencedMap<K, V> seqMap(K key1, V value1, K key2, V value2) {
    var map = new LinkedHashMap<K, V>();
    map.put(key1, value1);
    map.put(key2, value2);
    return Collections.unmodifiableSequencedMap(map);
  }

  public static <T, K, U> Collector<T, ?, SequencedMap<K, U>> toSeqMap(
      Function<T, K> keyMapper, Function<T, U> valueMapper) {
    return Collectors.toMap(keyMapper, valueMapper, (val1, _) -> val1, LinkedHashMap::new);
  }

  public static <T, U> List<U> tupleFields(List<T> expressions, BiFunction<String, T, U> mapper) {
    var fields = new ArrayList<U>();
    for (int i = 0; i < expressions.size(); i++) {
      var expr = expressions.get(i);
      fields.add(mapper.apply("_" + i, expr));
    }
    return fields;
  }

  public static <T, U> @Nullable U mapNonNull(@Nullable T obj, Function<T, U> mapper) {
    return obj != null ? mapper.apply(obj) : null;
  }

  public static <T> List<T> listOfSize(int size, Function<Integer, T> init) {
    var list = new ArrayList<T>(size);
    for (int i = 0; i < size; i++) {
      list.add(init.apply(i));
    }
    return Collections.unmodifiableList(list);
  }
}

