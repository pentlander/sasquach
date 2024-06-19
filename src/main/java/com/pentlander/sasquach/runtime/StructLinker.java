package com.pentlander.sasquach.runtime;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;

final class StructLinker implements GuardingDynamicLinker {
  private static final jdk.dynalink.linker.support.Lookup LOOKUP = new jdk.dynalink.linker.support.Lookup(
      MethodHandles.lookup());

  private static final MethodHandle CHECK_CLASSES = LOOKUP.findOwnStatic(
      "checkClasses",
      boolean.class,
      List.class,
      Object[].class);


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

  public static final class InitCallsiteDesc extends CallSiteDescriptor {
    private final List<String> fieldNames;

    public InitCallsiteDesc(Lookup lookup, Operation operation, MethodType methodType,
        List<String> fieldNames) {
      super(lookup, operation, methodType);
      this.fieldNames = fieldNames;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof InitCallsiteDesc other && getOperation().equals(other.getOperation())
          && getMethodType().equals(other.getMethodType()) && fieldNames.equals(other.fieldNames);
    }

    @Override
    public int hashCode() {
      return Objects.hash(getOperation(), getMethodType(), fieldNames);
    }
  }

  public enum StructOperation implements Operation {
    INIT
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

  @Override
  public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest,
      LinkerServices linkerServices) throws Exception {
    var operation = linkRequest.getCallSiteDescriptor().getOperation();
    if (operation instanceof StructOperation structOp) {
      switch (structOp) {
        case INIT -> {
          var callSiteDescriptor = (InitCallsiteDesc) linkRequest.getCallSiteDescriptor();
          var fieldNames = callSiteDescriptor.fieldNames;

          // Determine the field types for the returned struct
          var fieldTypes = new LinkedHashMap<String, ClassDesc>();
          var args = linkRequest.getArguments();
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
          var pn = callSiteLookup.lookupClass().describeConstable().orElseThrow().packageName();
          var classFileBytes = StructGenerator.generateDelegateStruct(pn, fieldTypes);
          var delegateStruct = callSiteLookup.defineHiddenClass(classFileBytes, true).lookupClass();

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
    }
    return null;
  }
}
