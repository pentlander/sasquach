package com.pentlander.sasquach.nameres;

import java.util.List;

public record ForeignFunctions(Class<?> ownerClass, List<ForeignFunctionHandle> functions) {
  boolean isEmpty() {
    return functions().isEmpty();
  }
}
