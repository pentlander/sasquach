package com.pentlander.sasquach;

public final class Preconditions {
  private Preconditions() {
  }


  public static void checkArgument(boolean expr, String errorTemplate, Object... args) {
    if (!expr) {
      throw new IllegalArgumentException(errorTemplate.formatted(args));
    }

  }

  public static <T> void checkNotInstanceOf(T obj, Class<? extends T> clazz, String msg) {
    if (clazz.isInstance(obj)) {
      throw new IllegalArgumentException("%s: '%s' is instance of '%s'".formatted(
          msg,
          obj,
          clazz.getName()));
    }
  }
}
