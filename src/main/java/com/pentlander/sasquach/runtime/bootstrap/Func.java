package com.pentlander.sasquach.runtime.bootstrap;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public sealed interface Func {
  ClassDesc CD = Func.class.describeConstable().orElseThrow();

  record NamedFunc(Object inner) implements Func {
  }

  record AnonFunc(MethodHandle inner) implements Func {}

  static Func named(Object inner) {
    return new NamedFunc(inner);
  }

  static Func anon(MethodHandle methodHandle, Object[] captures) {
    return new AnonFunc(MethodHandles.insertArguments(methodHandle, 0, captures));
  }

  static boolean namedFuncEquals(Object funcA, Object targetA, Object funcB, Object targetB) {
    return (funcA.equals(funcB) || funcA instanceof NamedFunc nfa && nfa.inner().equals(funcB)
        || funcB instanceof NamedFunc nfb && nfb.inner().equals(funcA)) && targetA.equals(targetB);
  }
}
