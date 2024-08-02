package com.pentlander.sasquach.runtime;

import static com.pentlander.sasquach.runtime.Bootstrap.bootstrapMethodTypeDesc;
import static com.pentlander.sasquach.runtime.Bootstrap.methodHandleDesc;

import com.pentlander.sasquach.runtime.StructLinker.StructInitCallSiteDesc;
import com.pentlander.sasquach.runtime.StructLinker.StructOperation;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.support.TypeUtilities;
import jdk.dynalink.support.ChainedCallSite;

/**
 * Class that handle dynamic dispatch on structs via invokedynamic.
 */
public final class StructDispatch {
  private static final MethodTypeDesc MTD_BOOTSTRAP = Bootstrap.bootstrapMethodTypeDesc();

  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_FIELD = methodHandleDesc(
      StructDispatch.class,
      "bootstrapField",
      MTD_BOOTSTRAP);
  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_METHOD = methodHandleDesc(
      StructDispatch.class,
      "bootstrapMethod",
      MTD_BOOTSTRAP);

  private static final MethodTypeDesc MTD_BOOTSTRAP_SPREAD = bootstrapMethodTypeDesc(Object[].class);
  public static final DirectMethodHandleDesc MHD_BOOTSTRAP_SPREAD = methodHandleDesc(
      StructDispatch.class,
      "bootstrapSpread",
      MTD_BOOTSTRAP_SPREAD);

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
        var objTo = TypeUtilities.getWrapperType(to);
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
    return DYNAMIC_LINKER.link(new ChainedCallSite(new StructInitCallSiteDesc(caller,
        StructOperation.STRUCT_INIT,
        invokedType,
        fieldNameList)));
  }

}
