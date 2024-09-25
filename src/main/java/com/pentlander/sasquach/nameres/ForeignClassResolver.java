package com.pentlander.sasquach.nameres;

import static com.pentlander.sasquach.type.TypeUtils.classDesc;

import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.type.TypeUtils;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

public class ForeignClassResolver {
  public Optional<ForeignFunctions> resolveFuncCall(ForeignFunctionCall foreignFunctionCall, Class<?> clazz) {
      var matchingForeignFunctions = new ArrayList<ForeignFunctionHandle>();
      var funcName = foreignFunctionCall.name();
      var isConstructor = funcName.toString().equals("new");
      if (isConstructor) {
        for (var constructor : clazz.getConstructors()) {
          var paramClassDescs = Arrays.stream(constructor.getParameterTypes())
              .map(TypeUtils::classDesc)
              .toArray(ClassDesc[]::new);
          var methodHandleDesc = MethodHandleDesc.ofConstructor(classDesc(clazz), paramClassDescs);
          matchingForeignFunctions.add(new ForeignFunctionHandle(methodHandleDesc, constructor));
        }
      } else {
        for (var method : clazz.getMethods()) {
          if (method.getName().equals(funcName.toString())) {
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            boolean isInterface = method.getDeclaringClass().isInterface();
            var kind = Kind.valueOf(
                isStatic ? MethodHandleInfo.REF_invokeStatic
                    : isInterface ? MethodHandleInfo.REF_invokeInterface
                        : MethodHandleInfo.REF_invokeVirtual,
                isInterface);

            var paramClassDescs = Arrays.stream(method.getParameterTypes()).map(TypeUtils::classDesc).toArray(ClassDesc[]::new);
            var methodTypeDesc = MethodTypeDesc.of(classDesc(method.getReturnType()), paramClassDescs);
            var ownerDesc = classDesc(clazz);
            var methodHandleDesc = MethodHandleDesc.ofMethod(kind, ownerDesc, funcName.toString(),  methodTypeDesc);
            matchingForeignFunctions.add(new ForeignFunctionHandle(methodHandleDesc, method));
          }
        }
      }

      if (!matchingForeignFunctions.isEmpty()) {
        return Optional.of(new ForeignFunctions(clazz, matchingForeignFunctions));
      }
      return Optional.empty();
    }
}
