package com.pentlander.sasquach.runtime.bootstrap;

import static java.util.Objects.requireNonNull;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import jdk.dynalink.linker.support.Lookup;

public final class SwitchBootstraps {
  private SwitchBootstraps() {
  }

  private static final Lookup LOOKUP = new Lookup(MethodHandles.lookup());

  private static final MethodTypeDesc MTD_BOOTSTRAP = MethodType.methodType(
          CallSite.class,
          List.of(MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class))
      .describeConstable()
      .orElseThrow();
  private static final DirectMethodHandleDesc MHD_BOOTSTRAP_SWITCH = MethodHandleDesc.ofMethod(Kind.STATIC,
      SwitchBootstraps.class.describeConstable().orElseThrow(),
      "bootstrapSwitch",
      MTD_BOOTSTRAP);
  private static final MethodType DO_TYPE_SWITCH_PRIVATE_TYPE = MethodType.methodType(int.class,
      Object.class,
      int.class,
      Object[].class);
  public static final MethodTypeDesc MTD_DO_TYPE_SWITCH = DO_TYPE_SWITCH_PRIVATE_TYPE.dropParameterTypes(2,
      DO_TYPE_SWITCH_PRIVATE_TYPE.parameterCount()).describeConstable().orElseThrow();

  public static final DynamicCallSiteDesc DCSD_SWITCH = DynamicCallSiteDesc.of(SwitchBootstraps.MHD_BOOTSTRAP_SWITCH,
      SwitchBootstraps.MTD_DO_TYPE_SWITCH);

  public static final MethodHandle DO_TYPE_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class,
      "doTypeSwitch",
      DO_TYPE_SWITCH_PRIVATE_TYPE);

  @SuppressWarnings("unused")
  public static CallSite bootstrapSwitch(MethodHandles.Lookup lookup, String invokedName,
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
