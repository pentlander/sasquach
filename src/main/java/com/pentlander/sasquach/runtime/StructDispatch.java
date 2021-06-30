package com.pentlander.sasquach.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

public class StructDispatch {
  // Ultimately the method type looks like (Struct): FieldType. The field name is embedded in the
  // CallSite
  private static class Dispatcher {
    private final String fieldName;
    private final Class<?> returnType;
    private final Map<Class<?>, MethodHandle> lookupTable = new HashMap<>();

    private Dispatcher(String fieldName, Class<?> returnType) {
      this.fieldName = fieldName;
      this.returnType = returnType;
    }

    public Object get(StructBase struct) throws Throwable {
      var handle = lookupTable.get(struct.getClass());
      if (handle == null) {
        handle = MethodHandles.lookup().findGetter(struct.getClass(), fieldName, returnType);
        lookupTable.put(struct.getClass(), handle);
      }
      return handle.invoke(struct);
    }

    CallSite buildCallSite() throws NoSuchMethodException, IllegalAccessException {
      var mh = MethodHandles.lookup().findVirtual(getClass(), "get",
          MethodType.methodType(Object.class, StructBase.class)).bindTo(this);
      return new ConstantCallSite(mh.asType(MethodType.methodType(returnType, StructBase.class)));
    }
  }

  public static CallSite bootstrap(Lookup caller, String invokedName, MethodType invokedType)
      throws NoSuchMethodException, IllegalAccessException {
    var d = new Dispatcher(invokedName, invokedType.returnType());
    return d.buildCallSite();
  }
}
