package com.pentlander.sasquach.runtime;

import java.util.ArrayList;
import java.util.List;

public class Stdlib {
  public static <T> ArrayList<T> map(ArrayList<T> list) {
    return new ArrayList<>(list);
  }

  public static <T> T throwIllegalStateException(String msg) {
    throw new IllegalStateException(msg);
  }

  public static void test() {
    List.of("foo", "bar").stream().map(str -> str + "foo").toList();
  }
}
