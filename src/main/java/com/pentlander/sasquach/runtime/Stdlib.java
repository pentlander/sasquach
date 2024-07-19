package com.pentlander.sasquach.runtime;

import java.util.ArrayList;

public class Stdlib {
  public static <T> ArrayList<T> map(ArrayList<T> list) {
    return new ArrayList<>(list);
  }

  public static <T> T throwIllegalStateException(String msg) {
    throw new IllegalStateException(msg);
  }
}
