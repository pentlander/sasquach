package com.pentlander.sasquach.runtime;

import com.pentlander.sasquach.runtime.StructLinker.InitCallsiteDesc;
import com.pentlander.sasquach.runtime.StructLinker.StructOperation;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static final MethodTypeDesc MT_BOOTSTRAP = MethodType.methodType(CallSite.class,
      List.of(Lookup.class, String.class, MethodType.class)).describeConstable().orElseThrow();
  public static final DirectMethodHandleDesc MH_BOOTSTRAP_FIELD =
      MethodHandleDesc.ofMethod(
          Kind.STATIC,
          StructDispatch.class.describeConstable().orElseThrow(),
          "bootstrapField",
          MT_BOOTSTRAP);
  private static final Map<Class<?>, Class<?>> PRIMITIVE_MAP = Map.of(Boolean.TYPE,
      Boolean.class,
      Integer.TYPE,
      Integer.class,
      Long.TYPE,
      Long.class,
      Float.TYPE,
      Float.class,
      Double.TYPE,
      Double.class);

  private static final DynamicLinker DYNAMIC_LINKER;
  private static final jdk.dynalink.linker.support.Lookup LOOKUP = new jdk.dynalink.linker.support.Lookup(
      MethodHandles.lookup());

  static {
    var linkerFactory = new DynamicLinkerFactory();
    linkerFactory.setPrioritizedLinkers(new StructLinker());
    DYNAMIC_LINKER = linkerFactory.createLinker();
  }

  /**
   * Handles dispatch on struct functions.
   */
  private static class MethodDispatcher {
    private final String methodName;
    private final MethodType methodType;
    private final Map<Class<?>, MethodHandle> lookupTable = new HashMap<>();

    private MethodDispatcher(String methodName, MethodType methodType) {
      this.methodName = methodName;
      this.methodType = methodType;
    }

    public Object invoke(StructBase struct, Object... args) throws Throwable {
      var handle = lookupTable.get(struct.getClass());
      if (handle == null) {
        handle = findMethod(struct, args);
        lookupTable.put(struct.getClass(), handle);
      }
      return handle.invokeWithArguments(args);
    }

    private MethodHandle findMethod(StructBase struct, Object[] args)
        throws NoSuchMethodException {
      for (var method : struct.getClass().getMethods()) {
        if (!method.getName().equals(methodName)) {
          continue;
        }

        var paramTypes = method.getParameterTypes();
        boolean matches = true;
        for (int i = 0; i < args.length; i++) {
          if (!isAssignableFrom(paramTypes[i], args[i].getClass())) {
            matches = false;
            break;
          }
        }
        if (matches) {
          return LOOKUP.unreflect(method);
        }
      }
      throw new NoSuchMethodException("No method '%s' on '%s' with params matching: %s".formatted(methodName,
          struct.getClass().getName(),
          Arrays.toString(args)));
    }

    private static boolean isAssignableFrom(Class<?> to, Class<?> from) {
      if (to.isPrimitive()) {
        var objTo = PRIMITIVE_MAP.get(to);
        return objTo.isAssignableFrom(from);
      }
      return to.isAssignableFrom(from);
    }

    CallSite buildCallSite() {
      var mh = LOOKUP
          .findVirtual(getClass(),
              "invoke",
              MethodType.methodType(Object.class, StructBase.class, Object[].class))
          .bindTo(this)
          .asVarargsCollector(Object[].class);
      return new ConstantCallSite(mh.asType(methodType));
    }
  }

  private static Operation parseOperation(String name) {
    var parts = name.split(":");
    if (parts.length > 3) {
      throw new IllegalArgumentException("Bad operation " + name);
    }
    Operation op = StandardOperation.valueOf(parts[0]);
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

  public static CallSite bootstrapField(Lookup caller, String invokedName, MethodType invokedType) {
    return DYNAMIC_LINKER.link(new ChainedCallSite(new CallSiteDescriptor(caller,
        parseOperation(invokedName),
        invokedType)));
  }

  public static CallSite bootstrapMethod(Lookup caller, String invokedName, MethodType invokedType) {
    return new MethodDispatcher(invokedName, invokedType).buildCallSite();
  }

  // The invoked type should be (FieldArgType0, FieldArgType1, ..., SpreadStructType0, SpreadStructType1, ...) -> NewStructType
  // This lets us avoid needing to pass in the output struct type in. We do still need to pass in
  // the names of the fields in the order they are assigned
  public static CallSite bootstrapSpread(Lookup caller, String invokedName, MethodType invokedType,
      Object... fieldNames) {
    var fieldNameList = Arrays.stream(fieldNames).map(String.class::cast).toList();
    return DYNAMIC_LINKER.link(new ChainedCallSite(new InitCallsiteDesc(caller,
        StructOperation.INIT,
        invokedType,
        fieldNameList)));
  }

}
