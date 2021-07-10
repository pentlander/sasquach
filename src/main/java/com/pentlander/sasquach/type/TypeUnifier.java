package com.pentlander.sasquach.type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves type parameters by unifying type variables with concrete types.
 */
public class TypeUnifier {
  /**
   * Map of type variables to their resolved types.
   */
  private final Map<NamedType, Type> unifiedTypes = new HashMap<>();

  /**
   * Resolves the type by replacing any type variables in a parameterized type with a concrete one.
   */
  public Type resolve(Type type) {
    if (type instanceof ParameterizedType paramType) {
      if (paramType instanceof NamedType namedType) {
        return unifiedTypes.getOrDefault(namedType, namedType);
      } else if (paramType instanceof FunctionType funcType) {
        var paramTypes = funcType.parameterTypes().stream().map(this::resolve).toList();
        var returnType = resolve(funcType.returnType());
        return new FunctionType(paramTypes, List.of(), returnType);
      } else if (paramType instanceof StructType structType) {
        var fieldTypes = new HashMap<String, Type>();
        structType.fieldTypes()
            .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType)));
        return new StructType(fieldTypes);
      }
    }
    return type;
  }

  /**
   * Unifies a the destination type with the soure type.
   */
  public Type unify(Type destType, Type sourceType) {
    if (destType instanceof ParameterizedType destParamType) {
      unify(destParamType, sourceType);
    }
    return sourceType;
  }

  private void unify(ParameterizedType destType, Type sourceType) {
    if (destType instanceof NamedType namedType) {
      var unifiedType = unifiedTypes.get(namedType);
      if (unifiedType != null && !unifiedType.equals(sourceType)) {
        throw new UnificationException(destType, sourceType);
      }
      unifiedTypes.put(namedType, sourceType);
    } else if (destType instanceof FunctionType destFuncType
        && sourceType instanceof FunctionType sourceFuncType) {
      var paramCount = destFuncType.parameterTypes().size();
      if (paramCount != sourceFuncType.parameterTypes().size()) {
        throw new UnificationException(destType, sourceType);
      }

      for (int i = 0; i < paramCount; i++) {
        var destParamType = destFuncType.parameterTypes().get(i);
        var sourceParamType = sourceFuncType.parameterTypes().get(i);
        unify(destParamType, sourceParamType);
      }
      unify(destFuncType.returnType(), sourceFuncType.returnType());
    } else if (destType instanceof StructType destStructType
        && sourceType instanceof StructType sourceStructType) {
      for (var entry : destStructType.fieldTypes().entrySet()) {
        var destFieldType = entry.getValue();
        var sourceFieldType = sourceStructType.fieldType(entry.getKey());
        if (sourceFieldType == null) {
          throw new UnificationException(destType, sourceType);
        }
        unify(destFieldType, sourceFieldType);
      }
    }
  }

  static class UnificationException extends RuntimeException {
    UnificationException(ParameterizedType destType, Type sourceType) {
      super("Failed to unify types '%s' and '%s'"
          .formatted(destType.toPrettyString(), sourceType.toPrettyString()));
    }
  }
}
