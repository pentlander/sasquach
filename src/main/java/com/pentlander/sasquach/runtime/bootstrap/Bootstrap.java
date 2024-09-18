package com.pentlander.sasquach.runtime.bootstrap;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

public interface Bootstrap {
  static MethodTypeDesc bootstrapMethodTypeDesc(Class<?> clazz, Class<?>... moreClasses) {
    var prefixNumClasses = clazz != null ? 4 : 3;
    var totalNumClasses = prefixNumClasses + moreClasses.length;

    var classes = new Class[totalNumClasses];
    classes[0] = Lookup.class;
    classes[1] = String.class;
    classes[2] = MethodType.class;
    if (clazz != null) {
      classes[3] = clazz;
      System.arraycopy(moreClasses, 0, classes, prefixNumClasses, moreClasses.length);
    }

    return MethodType.methodType(CallSite.class, classes).describeConstable().orElseThrow();
  }

  static MethodTypeDesc bootstrapMethodTypeDesc() {
    return bootstrapMethodTypeDesc(null);
  }

  static DirectMethodHandleDesc methodHandleDesc(Class<?> owner, String name, MethodTypeDesc lookupMethodType) {
    return MethodHandleDesc.ofMethod(
        Kind.STATIC,
        owner.describeConstable().orElseThrow(),
        name,
        lookupMethodType);
  }
}
