package com.pentlander.sasquach.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that handle dynamic dispatch on structs via invokedynamic.
 */
public final class StructDispatch {
  private static final Map<Class<?>, Class<?>> PRIMITIVE_MAP = Map.of(
      Boolean.TYPE, Boolean.class,
      Integer.TYPE, Integer.class,
      Long.TYPE, Long.class,
      Float.TYPE, Float.class,
      Double.TYPE, Double.class
  );

  /**
   * Handles dispatch on struct field access.
   */
  private static class FieldDispatcher {
    private final String fieldName;
    private final Class<?> returnType;
    private final Map<Class<?>, MethodHandle> lookupTable = new HashMap<>();

    private FieldDispatcher(String fieldName, Class<?> returnType) {
      this.fieldName = fieldName;
      this.returnType = returnType;
    }

    public Object get(StructBase struct) throws Throwable {
      var handle = lookupTable.get(struct.getClass());
      if (handle == null) {
        try {
          handle = MethodHandles.lookup().findGetter(struct.getClass(), fieldName, returnType);
        } catch (NoSuchFieldException e) {
          handle = MethodHandles.lookup().findGetter(struct.getClass(), fieldName, Object.class);
        }
        lookupTable.put(struct.getClass(), handle);
      }
      return handle.invoke(struct);
    }

    CallSite buildCallSite() throws NoSuchMethodException, IllegalAccessException {
      // Ultimately the method type looks like (Struct): FieldType. The field name is embedded in the
      // CallSite
      var mh = MethodHandles.lookup()
          .findVirtual(getClass(), "get", MethodType.methodType(Object.class, StructBase.class))
          .bindTo(this);
      return new ConstantCallSite(mh.asType(MethodType.methodType(returnType, StructBase.class)));
    }
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
        throws IllegalAccessException, NoSuchMethodException {
      for (var method : struct.getClass().getMethods()) {
        if (!method.getName().equals(methodName)) continue;

        var paramTypes = method.getParameterTypes();
        boolean matches = true;
        for (int i = 0; i < args.length; i++) {
          if (!isAssignableFrom(paramTypes[i], args[i].getClass())) {
            matches = false;
            break;
          }
        }
        if (matches) {
          return MethodHandles.lookup().unreflect(method);
        }
      }
      throw new NoSuchMethodException("No method '%s' on '%s' with params matching: %s".formatted(
          methodName,
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

    CallSite buildCallSite() throws NoSuchMethodException, IllegalAccessException {
      var mh = MethodHandles.lookup().findVirtual(getClass(),
          "invoke",
          MethodType.methodType(Object.class, StructBase.class, Object[].class)).bindTo(this)
          .asVarargsCollector(Object[].class);
      return new ConstantCallSite(mh.asType(methodType));
    }
  }

  public static CallSite bootstrapField(Lookup caller, String invokedName, MethodType invokedType)
      throws NoSuchMethodException, IllegalAccessException {
    return new FieldDispatcher(invokedName, invokedType.returnType()).buildCallSite();
  }

  public static CallSite bootstrapMethod(Lookup caller, String invokedName, MethodType invokedType)
      throws NoSuchMethodException, IllegalAccessException {
    return new MethodDispatcher(invokedName, invokedType).buildCallSite();
  }
}
