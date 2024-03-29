package com.pentlander.sasquach.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Resolves type parameters by unifying type variables with concrete types.
 */
public class TypeUnifier {
  /**
   * Resolves the type by replacing any type variables in a parameterized type with a concrete one.
   */
  public Type resolve(Type type) {
    if (type instanceof ParameterizedType paramType) {
      return switch (paramType) {
        case UniversalType universalType -> universalType;
        case TypeVariable typeVariable -> typeVariable.resolvedType().orElse(typeVariable);
        case FunctionType funcType -> {
          var paramTypes = resolve(funcType.parameterTypes());
          var returnType = resolve(funcType.returnType());
          yield new FunctionType(paramTypes, List.of(), returnType);
        }
        case StructType structType -> {
          var fieldTypes = new LinkedHashMap<String, Type>();
          structType.fieldTypes()
              .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType)));
          yield new StructType(structType.typeName(), fieldTypes);
        }
        case ResolvedModuleNamedType namedType ->
            new ResolvedModuleNamedType(namedType.moduleName(),
                namedType.name(),
                resolve(namedType.typeArgs()),
                resolve(namedType.type()));
        case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
            resolve(namedType.typeArgs()),
            resolve(namedType.type()));
        case ClassType classType ->
            new ClassType(classType.typeClass(), resolve(classType.typeArguments()));
        case SumType sumType -> new SumType(sumType.moduleName(),
            sumType.name(),
            sumType.typeParameters(),
            sumType.types().stream().map(this::resolve).map(VariantType.class::cast).toList());
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
  public void unify(Type destType, Type sourceType) {
    if (destType instanceof ResolvedNamedType resolvedNamedType) {
      destType = resolvedNamedType.type();
    } else if (destType instanceof ForeignFieldType foreignFieldType) {
      destType = foreignFieldType.type();
    }

    if (sourceType instanceof ResolvedNamedType resolvedNamedType) {
      sourceType = resolvedNamedType.type();
    } else if (sourceType instanceof ForeignFieldType foreignFieldType) {
      sourceType = foreignFieldType.type();
    }

    if (destType instanceof ParameterizedType destParamType) {
      unify(destParamType, resolve(sourceType));
    } else if (sourceType instanceof ParameterizedType sourceParamType) {
      unify(sourceParamType, resolve(destType));
    }

    var resolvedDestType = resolve(destType);
    if (!sourceType.isAssignableFrom(resolvedDestType)) {
      throw new UnificationException(destType, sourceType);
    }
  }

  private void unifyTypeVariable(TypeVariable typeVar, Type sourceType) {
    if (!typeVar.resolveType(sourceType)) {
      throw new UnificationException(typeVar, sourceType, typeVar.resolvedType().orElseThrow());
    }
  }

  private void unify(ParameterizedType destType, Type sourceType) {
    switch (destType) {
      case UniversalType universalType when sourceType instanceof UniversalType -> {
        if (!universalType.isAssignableFrom(sourceType)) {
          throw new UnificationException(destType, sourceType);
        }
      }
      case UniversalType universalType when sourceType instanceof ClassType classType -> {
        if (!classType.typeClass().equals(Object.class)) {
          throw new UnificationException(destType, sourceType);
        }
      }
      case TypeVariable typeVar -> unifyTypeVariable(typeVar, sourceType);
      case FunctionType destFuncType when sourceType instanceof FunctionType sourceFuncType -> {
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
      case StructType destStructType when sourceType instanceof StructType sourceStructType -> {
        for (var entry : destStructType.fieldTypes().entrySet()) {
          var destFieldType = entry.getValue();
          var sourceFieldType = sourceStructType.fieldType(entry.getKey());
          if (sourceFieldType == null) {
            throw new UnificationException(destType, sourceType);
          }
          unify(destFieldType, sourceFieldType);
        }
      }
      case SumType destSumType when sourceType instanceof SumType sourceSumType
          && destSumType.typeName().equals(sourceSumType.typeName()) -> {
        for (int i = 0; i < destSumType.types().size(); i++) {
          var destVariantType = destSumType.types().get(i);
          var sourceVariantType = sourceSumType.types().get(i);
          unify(destVariantType, sourceVariantType);
        }
      }
      case SumType destSumType when sourceType instanceof SingletonType sourceSingletonType ->
          destSumType.types()
              .stream()
              .filter(t -> t.isAssignableFrom(sourceSingletonType))
              .findFirst()
              .orElseThrow(() -> new UnificationException(destType, sourceType));
      case StructType destSumType when sourceType instanceof SumType sourceSumType -> {
        var matchingVariant = sourceSumType.types()
            .stream()
            .filter(t -> t.isAssignableFrom(destSumType))
            .findFirst()
            .orElseThrow(() -> new UnificationException(destType, sourceType));
        unify(destSumType, matchingVariant);
      }
      case ClassType destClassType when sourceType instanceof ClassType sourceClassType -> {
        for (int i = 0; i < destClassType.typeArguments().size(); i++) {
          var destArgType = destClassType.typeArguments().get(i);
          var sourceArgType = sourceClassType.typeArguments().get(i);
          unify(destArgType, sourceArgType);
        }
      }
      case ClassType destClassType when destClassType.typeClass().equals(Object.class) -> {
        // Pass if the dest class is Object. This handles the situation where a method accepts an
        // object without a generic param, e.g. Map::get
      }
      default -> {
        if (sourceType instanceof TypeVariable typeVar) {
          unifyTypeVariable(typeVar, destType);
        } else {
          throw new UnificationException(destType, sourceType);
        }
      }
    }
  }

  static class UnificationException extends RuntimeException {
    private final Type destType;
    private final Type sourceType;
    private final Type resolvedDestType;

    UnificationException(Type destType, Type sourceType, Type resolvedDestType) {
      super("Failed to unify types '%s' and '%s'".formatted(destType.toPrettyString(),
          sourceType.toPrettyString()));
      this.destType = destType;
      this.sourceType = sourceType;
      this.resolvedDestType = resolvedDestType;
    }

    UnificationException(Type destType, Type sourceType) {
      this(destType, sourceType, null);
    }

    public Type destType() {
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
