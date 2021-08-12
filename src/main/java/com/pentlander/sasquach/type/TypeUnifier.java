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
      return switch (paramType) {
        case NamedType namedType -> unifiedTypes.getOrDefault(namedType, namedType);
        case FunctionType funcType -> {
          var paramTypes = funcType.parameterTypes().stream().map(this::resolve).toList();
          var returnType = resolve(funcType.returnType());
          yield new FunctionType(paramTypes, List.of(), returnType);
        }
        case StructType structType -> {
          var fieldTypes = new HashMap<String, Type>();
          structType.fieldTypes()
              .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType)));
          yield new StructType(fieldTypes);
        }
      };
    }
    return type;
  }

  /**
   * Unifies a the destination type with the soure type.
   */
  public Type unify(Type destType, Type sourceType) {
    if (destType instanceof ParameterizedType destParamType) {
      unify(destParamType, sourceType);
      return sourceType;
    }
    return destType;
  }

  private void unify(ParameterizedType destType, Type sourceType) {
    switch (destType) {
      case NamedType namedType -> {
        var unifiedType = unifiedTypes.get(namedType);
        if (unifiedType != null && !unifiedType.equals(sourceType)) {
          throw new UnificationException(destType, sourceType);
        }
        unifiedTypes.put(namedType, sourceType);
      }
      case FunctionType destFuncType && sourceType instanceof FunctionType sourceFuncType -> {
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
      }
      case StructType destStructType && sourceType instanceof StructType sourceStructType -> {
        for (var entry : destStructType.fieldTypes().entrySet()) {
          var destFieldType = entry.getValue();
          var sourceFieldType = sourceStructType.fieldType(entry.getKey());
          if (sourceFieldType == null) {
            throw new UnificationException(destType, sourceType);
          }
          unify(destFieldType, sourceFieldType);
        }
      }
      default -> throw new IllegalStateException("Unexpected value: " + destType);
    }
  }

  static class UnificationException extends RuntimeException {
    UnificationException(ParameterizedType destType, Type sourceType) {
      super("Failed to unify types '%s' and '%s'"
          .formatted(destType.toPrettyString(), sourceType.toPrettyString()));
    }
  }
}
