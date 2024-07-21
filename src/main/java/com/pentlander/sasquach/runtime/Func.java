package com.pentlander.sasquach.runtime;

import static com.pentlander.sasquach.type.TypeUtils.classDesc;

import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

public sealed interface Func {
  MethodTypeDesc MTD_ANON =
      MethodTypeDesc.of(
          classDesc(Func.class),
          ConstantDescs.CD_MethodHandles_Lookup,
          ConstantDescs.CD_String,
          ConstantDescs.CD_MethodType,
          ConstantDescs.CD_Object.arrayType());

  record NamedFunc(Object inner) implements Func {}

  record AnonFunc(MethodHandle inner) implements Func {}

  static Func named(Object inner) {
    return new NamedFunc(inner);
  }

  static Func anon(Lookup lookup, String funcName, MethodType methodType, Object[] captures) {
    try {
      var methodHandle = lookup.findStatic(lookup.lookupClass(), funcName, methodType);
      return new AnonFunc(MethodHandles.insertArguments(methodHandle, 0, captures));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
