package com.pentlander.sasquach.runtime.bootstrap;

import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.bootstrap.Func.AnonFunc;
import com.pentlander.sasquach.runtime.bootstrap.Func.NamedFunc;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingTypeConverterFactory;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Guards;
import org.jspecify.annotations.Nullable;

final class StructLinker implements GuardingDynamicLinker, GuardingTypeConverterFactory {
  private static final jdk.dynalink.linker.support.Lookup LOOKUP = new jdk.dynalink.linker.support.Lookup(
      MethodHandles.lookup());

  private static final MethodHandle CHECK_CLASSES = LOOKUP.findOwnStatic(
      "checkClasses",
      boolean.class,
      List.class,
      Object[].class);

  private static final MethodHandle NAMED_FUNC_EQUALS = LOOKUP.findStatic(
      Func.class,
      "namedFuncEquals",
      MethodType.methodType(boolean.class, Object.class, Object.class, Object.class, Object.class));

  private static final MethodHandle MH_NAMED_FUNC = LOOKUP.findStatic(
      Func.class,
      "named",
      MethodType.methodType(Func.class, Object.class));

  @Override
  public GuardedInvocation convertToType(Class<?> sourceType, Class<?> targetType,
      Supplier<Lookup> lookupSupplier) {
    if (sourceType.equals(Object.class) && targetType.equals(Func.class)) {
      return new GuardedInvocation(MH_NAMED_FUNC);
    }
    return null;
  }


  record IdxMethodHandle(int idx, MethodHandle methodHandle) {}

  private static IdxMethodHandle findStructFieldHandle(String fieldName,
      List<Class<?>> structClasses) {
    for (int i = 0; i < structClasses.size(); i++) {
      var structClass = structClasses.get(i);
      for (var field : structClass.getDeclaredFields()) {
        if (field.getName().equals(fieldName)) {
          return new IdxMethodHandle(i, LOOKUP.unreflectGetter(field));
        }
      }
    }
    throw new RuntimeException("Unable to find matching field: " + fieldName);
  }

  public static MethodHandle spreadHandle(MethodType invokedType, Object[] fieldNameObjs) {
    var targetClass = invokedType.returnType();
    var targetConstructor = targetClass.getConstructors()[0];
    var targetFieldNames = Arrays.stream(targetClass.getDeclaredFields())
        .map(Field::getName)
        .toList();
    var fieldNames = Arrays.stream(fieldNameObjs).map(String.class::cast).toList();

    // Need to take the constructor for the target class transform it such that it accesses the
    // fields and objects in the spread to match the signature
    // How do I map the fields to the correct order on the constructor?
    // Need to create a method type with the same number of args as the constructor where all of the
    // fields supplied are as-is but all of the spreads coming from objects are just of the type of
    // the object they're coming from
    var reorder = new int[targetConstructor.getParameterCount()];
    var fieldHandles = new MethodHandle[targetConstructor.getParameterCount()];
    var params = invokedType.parameterList();
    var structParams = params.subList(fieldNames.size(), params.size());
    for (int i = 0; i < targetConstructor.getParameterCount(); i++) {
      var targetFieldName = targetFieldNames.get(i);
      var paramIdx = fieldNames.indexOf(targetFieldName);
      if (paramIdx != -1) {
        reorder[i] = paramIdx;
        fieldHandles[i] = null;
      } else {
        var idxFieldHandle = findStructFieldHandle(targetFieldName, structParams);
        reorder[i] = idxFieldHandle.idx() + fieldNames.size();
        fieldHandles[i] = idxFieldHandle.methodHandle();
      }
    }

    var constructorHandle = LOOKUP.unreflectConstructor(targetConstructor);
    var filteredHandle = MethodHandles.filterArguments(constructorHandle, 0, fieldHandles);
    return MethodHandles.permuteArguments(filteredHandle, invokedType, reorder);
  }

  public static final class StructCallSiteDesc extends CallSiteDescriptor {
    private final Object[] values;

    public StructCallSiteDesc(Lookup lookup, Operation operation, MethodType methodType,
        Object[] values) {
      super(lookup, operation, methodType);
      this.values = values;
    }

    @Override
    public boolean equals(Object other) {
      if (!super.equals(other)) {
        return false;
      }
      return other instanceof StructCallSiteDesc o && Arrays.equals(values, o.values);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }

    @Override
    protected CallSiteDescriptor changeMethodTypeInternal(MethodType newMethodType) {
      return new StructCallSiteDesc(getLookupPrivileged(), getOperation(), newMethodType, values);
    }

    public List<String> fieldNames() {
      return Arrays.stream(values).map(String.class::cast).toList();
    }
  }

  public enum StructOperation implements Operation {
    STRUCT_INIT
  }

  @SuppressWarnings("unused")
  private static boolean checkClasses(List<Class<?>>classes, Object[] args) {
    for (int i = 0; i < classes.size(); i++) {
      if (!classes.get(i).isInstance(args[i])) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest,
      LinkerServices linkerServices) throws Exception {
    var structLinkReq = switch (linkRequest) {
      case StructLinkRequest structLinkRequest -> structLinkRequest;
      case null, default ->  StructLinkRequest.from(linkRequest, true);
    };
    if (!structLinkReq.shouldHandle()) return null;

    var baseOperation = structLinkReq.baseOperation();
    if (structLinkReq.baseOperation() instanceof StructOperation structOp) {
      //noinspection SwitchStatementWithTooFewBranches
      switch (structOp) {
        case STRUCT_INIT -> {
          var callSiteDescriptor = (StructCallSiteDesc) structLinkReq.getCallSiteDescriptor();
          var fieldNames = callSiteDescriptor.fieldNames();

          // Determine the field types for the returned struct
          var fieldTypes = new LinkedHashMap<String, ClassDesc>();
          var args = structLinkReq.getArguments();
          var argClasses = new ArrayList<Class<?>>();
          for (int i = 0; i < args.length; i++) {
            var argClass = args[i].getClass();
            argClasses.add(argClass);
            if (i < fieldNames.size()) {
              fieldTypes.putIfAbsent(fieldNames.get(i), argClass.describeConstable().orElseThrow());
            } else if (args[i] instanceof StructBase struct) {
              for (var field : struct.getClass().getDeclaredFields()) {
                fieldTypes.putIfAbsent(field.getName(),
                    field.getType().describeConstable().orElseThrow());
              }
            } else {
              throw new IllegalArgumentException(
                  "Tried to spread object that is not a struct: " + args[i]);
            }
          }

          // Generate and define the class with the field types from the previous section
          var callSiteLookup = callSiteDescriptor.getLookup();
          var packageName = callSiteLookup.lookupClass().getPackageName();
          var classFileBytes = StructGenerator.generateDelegateStruct(packageName, fieldTypes);
          var delegateStruct = callSiteLookup.defineHiddenClass(
              classFileBytes,
              true,
              ClassOption.NESTMATE).lookupClass();

          // Create a method handle that invokes the constructor of the returned struct
          var handle = spreadHandle(MethodType.methodType(
                  delegateStruct,
                  argClasses), fieldNames.toArray())
              .asType(callSiteDescriptor.getMethodType());

          // Create a guard that checks all the spread structs match the types from the
          // previous invocation
          var structClasses = argClasses.subList(fieldNames.size(), args.length);
          var checkClasses = CHECK_CLASSES.bindTo(structClasses).asCollector(Object[].class, structClasses.size());
          var guard = MethodHandles.dropArguments(checkClasses, 0, argClasses.subList(0, fieldNames.size()));

          return new GuardedInvocation(handle, guard);
        }
      }
    } else if (baseOperation instanceof StandardOperation op && op == StandardOperation.CALL) {
      var callSiteDesc = structLinkReq.getCallSiteDescriptor();
      var args = structLinkReq.getArguments();
      var func = (Func) args[0];
      return switch (func) {
        case AnonFunc(var inner) -> new GuardedInvocation(
            MethodHandles.dropArguments(inner, 0, Func.class, Object.class),
            Guards.getIdentityGuard(func));
        case NamedFunc(var inner) -> {
          args[0] = inner;
          var guardedInvocation = linkerServices.getGuardedInvocation(StructLinkRequest.from(structLinkReq.replaceArguments(
              callSiteDesc,
              args), false));

          yield new GuardedInvocation(
              guardedInvocation.getInvocation(),
              MethodHandles.insertArguments(NAMED_FUNC_EQUALS, 0, func, args[1]),
              guardedInvocation.getSwitchPoints(),
              guardedInvocation.getException());
        }
      };
    }
    return null;
  }
}
