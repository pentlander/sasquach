package com.pentlander.sasquach.runtime.bootstrap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public sealed interface Func {

  record NamedFunc(Object inner) implements Func {}

  record AnonFunc(MethodHandle inner) implements Func {}

  static Func named(Object inner) {
    return new NamedFunc(inner);
  }

  static Func anon(MethodHandle methodHandle, Object[] captures) {
    return new AnonFunc(MethodHandles.insertArguments(methodHandle, 0, captures));
  }
}
