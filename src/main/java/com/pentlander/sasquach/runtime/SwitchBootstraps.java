package com.pentlander.sasquach.runtime;

import static java.util.Objects.requireNonNull;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.linker.support.Lookup;

public class SwitchBootstraps {
  private SwitchBootstraps() {
  }

  private static final Lookup LOOKUP = new Lookup(MethodHandles.lookup());
  private static final MethodType DO_TYPE_SWITCH_PRIVATE_TYPE = MethodType.methodType(int.class,
      Object.class,
      int.class,
      Object[].class);
  public static final MethodType DO_TYPE_SWITCH_TYPE = DO_TYPE_SWITCH_PRIVATE_TYPE.dropParameterTypes(2,
      DO_TYPE_SWITCH_PRIVATE_TYPE.parameterCount());

  private static final MethodHandle DO_TYPE_SWITCH;

  static {
    DO_TYPE_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class,
        "doTypeSwitch",
        DO_TYPE_SWITCH_PRIVATE_TYPE);
  }

  @SuppressWarnings("unused")
  public static CallSite bootstrapSwitch(MethodHandles.Lookup lookup, String invocationName,
      MethodType invocationType, Object... labels) {
    requireNonNull(labels);
    // TODO: Verify the labels and object are sum/variant types
    MethodHandle target = MethodHandles.insertArguments(DO_TYPE_SWITCH, 2, (Object) labels);
    return new ConstantCallSite(target);
  }

  private static int doTypeSwitch(Object target, int startIndex, Object[] labels) {
    Class<?> targetClass = target.getClass();
    for (int i = startIndex; i < labels.length; i++) {
      var labelClass = (Class<?>) labels[i];
      if (labelClass.isAssignableFrom(targetClass)) {
        return i;
      }
    }

    return labels.length;
  }
}
/*
   NEW java/lang/MatchException
    DUP
    ACONST_NULL
    ACONST_NULL
    INVOKESPECIAL java/lang/MatchException.<init> (Ljava/lang/String;Ljava/lang/Throwable;)V
    ATHROW
 */
