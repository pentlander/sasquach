package com.pentlander.sasquach.runtime;

import static com.pentlander.sasquach.runtime.Bootstrap.*;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

public final class FuncBootstrap {
  private static final jdk.dynalink.linker.support.Lookup LOOKUP = new jdk.dynalink.linker.support.Lookup(
      MethodHandles.lookup());

  private static final MethodHandle MH_FUNC_ANON = LOOKUP.findStatic(
      Func.class,
      "anon",
      MethodType.methodType(Func.class, MethodHandle.class, Object[].class));
  private static final MethodTypeDesc MTD_FUNC_ANON = MH_FUNC_ANON.describeConstable()
      .orElseThrow()
      .invocationType()
      .dropParameterTypes(0, 1);

  private static final MethodTypeDesc MTD_BOOTSTRAP_FUNC_INIT = bootstrapMethodTypeDesc(MethodType.class);

  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_FUNC_INIT = methodHandleDesc(
      FuncBootstrap.class,
      "bootstrapFuncInit",
      MTD_BOOTSTRAP_FUNC_INIT);

  public static DynamicCallSiteDesc bootstrapFuncInit(String funcName, MethodTypeDesc funcTypeDesc) {
    return DynamicCallSiteDesc.of(MHD_BOOTSTRAP_FUNC_INIT, funcName, MTD_FUNC_ANON, funcTypeDesc);
  }

  public static CallSite bootstrapFuncInit(Lookup lookup, String invokedName,
      MethodType invocationType, MethodType funcMethodType) {
    try {
      var anonFuncMethodHandle = lookup.findStatic(
          lookup.lookupClass(),
          invokedName,
          funcMethodType);
      return new ConstantCallSite(MethodHandles.insertArguments(
          MH_FUNC_ANON,
          0,
          anonFuncMethodHandle));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new LinkageError("Failed to link func init", e);
    }
  }
}
