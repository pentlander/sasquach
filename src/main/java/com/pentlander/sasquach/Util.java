package com.pentlander.sasquach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Util {
  private Util() {}

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
}

