package com.pentlander.sasquach.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Stdlib {
  public static <T> ArrayList<T> map(ArrayList<T> list) {
    return new ArrayList<>(list);
  }

  public static <T> T throwIllegalStateException(String msg) {
    throw new IllegalStateException(msg);
  }

  public static <T> ArrayList<T> listFromArray(T[] arr) {
    return new ArrayList<>(Arrays.asList(arr));
  }

  public static <T> boolean equals(T obj, T otherObj) {
    return Objects.equals(obj, otherObj);
  }
}
