package com.pentlander.sasquach.runtime;

import static com.pentlander.sasquach.runtime.Bootstrap.bootstrapMethodTypeDesc;
import static com.pentlander.sasquach.runtime.Bootstrap.methodHandleDesc;

import com.pentlander.sasquach.runtime.StructLinker.StructInitCallSiteDesc;
import com.pentlander.sasquach.runtime.StructLinker.StructOperation;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.support.ChainedCallSite;

/**
 * Class that handle dynamic dispatch on structs via invokedynamic.
 */
public final class StructDispatch {
  private static final MethodTypeDesc MTD_BOOTSTRAP = Bootstrap.bootstrapMethodTypeDesc();

  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_MEMBER = methodHandleDesc(
      StructDispatch.class,
      "bootstrapMember",
      MTD_BOOTSTRAP);

  private static final MethodTypeDesc MTD_BOOTSTRAP_SPREAD = bootstrapMethodTypeDesc(Object[].class);
  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_SPREAD = methodHandleDesc(
      StructDispatch.class,
      "bootstrapSpread",
      MTD_BOOTSTRAP_SPREAD);

  private static final DynamicLinker DYNAMIC_LINKER;

  static {
    var linkerFactory = new DynamicLinkerFactory();
    linkerFactory.setPrioritizedLinkers(new StructLinker());
    DYNAMIC_LINKER = linkerFactory.createLinker();
  }

  private static Operation parseOperation(String name) {
    return parseOperation(StandardOperation.class, name);
  }

  private static Operation parseSasquachOperation(String name) {
    return parseOperation(StructOperation.class, name);
  }

  private static <T extends Enum<T> & Operation> Operation parseOperation(Class<T> clazz, String name) {
    var parts = name.split(":");
    if (parts.length > 3) {
      throw new IllegalArgumentException("Bad operation " + name);
    }
    Operation op = Enum.valueOf(clazz, parts[0]);
    if (parts.length > 1) {
      var namespaces = Arrays.stream(parts[1].split("\\|"))
          .map(StandardNamespace::valueOf)
          .toArray(StandardNamespace[]::new);
      op = op.withNamespaces(namespaces);
    }
    if (parts.length > 2) {
      var opName = parts[2];
      op = op.named(opName);
    }
    return op;
  }

  public static CallSite bootstrapMember(Lookup caller, String invokedName, MethodType invokedType) {
    return DYNAMIC_LINKER.link(new ChainedCallSite(new CallSiteDescriptor(caller,
        parseOperation(invokedName),
        invokedType)));
  }

  // The invoked type should be (FieldArgType0, FieldArgType1, ..., SpreadStructType0, SpreadStructType1, ...) -> NewStructType
  // This lets us avoid needing to pass in the output struct type in. We do still need to pass in
  // the names of the fields in the order they are assigned
  public static CallSite bootstrapSpread(Lookup caller, String invokedName, MethodType invokedType,
      Object... fieldNames) {
    var fieldNameList = Arrays.stream(fieldNames).map(String.class::cast).toList();
    return DYNAMIC_LINKER.link(new ChainedCallSite(new StructInitCallSiteDesc(caller,
        StructOperation.STRUCT_INIT,
        invokedType,
        fieldNameList)));
  }

}
