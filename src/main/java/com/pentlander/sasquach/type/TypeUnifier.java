package com.pentlander.sasquach.type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves type parameters by unifying type variables with concrete types.
 */
public class TypeUnifier {
  /**
   * Map of type variables to their resolved types.
   */
  private final Map<TypeVariable, Type> unifiedTypes = new HashMap<>();

  /**
   * Resolves the type by replacing any type variables in a parameterized type with a concrete one.
   */
  public Type resolve(Type type) {
    if (type instanceof ParameterizedType paramType) {
      return switch (paramType) {
        case TypeVariable typeVariable -> unifiedTypes.getOrDefault(typeVariable, typeVariable);
        case FunctionType funcType -> {
          var paramTypes = resolve(funcType.parameterTypes());
          var returnType = resolve(funcType.returnType());
          yield new FunctionType(paramTypes, List.of(), returnType);
        }
        case StructType structType -> {
          var fieldTypes = new HashMap<String, Type>();
          structType.fieldTypes()
              .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType)));
          yield new StructType(fieldTypes);
        }
        case ResolvedModuleNamedType namedType -> new ResolvedModuleNamedType(
            namedType.moduleName(),
            namedType.name(),
            resolve(namedType.typeArgs()),
            resolve(namedType.type()));
        case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
            resolve(namedType.typeArgs()),
            resolve(namedType.type()));
      };
    }
    return type;
  }

  private List<Type> resolve(List<Type> types) {
    return types.stream().map(this::resolve).toList();
  }

  /**
   * Unifies a the destination type with the soure type.
   */
  public Type unify(Type destType, Type sourceType) {
    if (destType instanceof ResolvedNamedType resolvedNamedType) {
      destType = resolvedNamedType.type();
    }
    if (sourceType instanceof ResolvedNamedType resolvedNamedType) {
      sourceType = resolvedNamedType.type();
    }

    if (destType instanceof ParameterizedType destParamType) {
      unify(destParamType, sourceType);
      return sourceType;
    }
    return destType;
  }

  private void unify(ParameterizedType destType, Type sourceType) {
    switch (destType) {
      case TypeVariable typeVar -> {
        var unifiedType = unifiedTypes.get(typeVar);
        if (unifiedType != null && !unifiedType.equals(sourceType)) {
          throw new UnificationException(destType, sourceType, unifiedType);
        }
        unifiedTypes.put(typeVar, sourceType);
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
      default -> throw new UnificationException(destType, sourceType);
    }
  }

  static class UnificationException extends RuntimeException {
    private final ParameterizedType destType;
    private final Type sourceType;
    private final Type resolvedDestType;

    UnificationException(ParameterizedType destType, Type sourceType, Type resolvedDestType) {
      super("Failed to unify types '%s' and '%s'"
          .formatted(destType.toPrettyString(), sourceType.toPrettyString()));
      this.destType = destType;
      this.sourceType = sourceType;
      this.resolvedDestType = resolvedDestType;
    }

    UnificationException(ParameterizedType destType, Type sourceType) {
      this(destType, sourceType, null);
    }

    public ParameterizedType destType() {
      return destType;
    }

    public Type sourceType() {
      return sourceType;
    }

    public Optional<Type> resolvedDestType() {
      return Optional.ofNullable(resolvedDestType);
    }
  }
}
