package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Preconditions.checkNotInstanceOf;

import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.StructType.RowModifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Resolves type parameters by unifying type variables with concrete types.
 */
public class TypeUnifier {
  /**
   * Resolves the type by replacing any type variables in a parameterized type with a concrete one.
   */
  public Type resolve(Type type) {
    checkNotInstanceOf(type, NamedType.class, "named type must be resolved before unifying");

    if (type instanceof TypeNester paramType) {
      return switch (paramType) {
        case UniversalType universalType -> universalType;
        case TypeVariable typeVariable -> typeVariable.resolvedType().orElse(typeVariable);
        case FunctionType funcType -> {
          var params = funcType.parameters()
              .stream()
              .map(param -> param.mapType(this::resolve))
              .toList();
          var returnType = resolve(funcType.returnType());
          yield new FunctionType(
              params,
              funcType.typeParameters(),
              returnType);
        }
        case StructType structType -> {
          var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
          structType.memberTypes()
              .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType)));

          StructType.RowModifier rowModifier = structType.rowModifier();
          if (structType.rowModifier() instanceof RowModifier.NamedRow namedRow && namedRow.type() instanceof TypeVariable typeVar) {
            var rowStruct = typeVar.resolvedType().flatMap(TypeUtils::asStructType);
            rowStruct.ifPresent(value -> value.memberTypes()
                .forEach((name, fieldType) -> fieldTypes.put(name, resolve(fieldType))));
          }
          yield new StructType(
              structType.name(),
              structType.typeParameters(),
              fieldTypes, rowModifier);
        }
        case ResolvedModuleNamedType namedType ->
            new ResolvedModuleNamedType(
                namedType.name(),
                resolve(namedType.typeArgs()),
                resolve(namedType.type()));
        case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
            resolve(namedType.typeArgs()),
            resolve(namedType.type()));
        case ClassType classType ->
            new ClassType(classType.typeClass(), resolve(classType.typeArguments()));
        case SumType sumType -> new SumType(
            sumType.qualifiedTypeName(),
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
    checkNotInstanceOf(
        destType,
        NamedType.class,
        "named destType must be resolved before unifying");
    checkNotInstanceOf(sourceType,
        NamedType.class,
        "named sourceType must be resolved before unifying");

    if (destType instanceof ResolvedNamedType resolvedNamedType) {
      destType = resolvedNamedType.type();
    }

    if (sourceType instanceof ResolvedNamedType resolvedNamedType) {
      sourceType = resolvedNamedType.type();
    }

    if (destType instanceof TypeNester destParamType) {
      unify(destParamType, resolve(sourceType));
    } else if (sourceType instanceof TypeNester sourceParamType) {
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

  private void unify(TypeNester destType, Type sourceType) {
    switch (destType) {
      case UniversalType universalType when sourceType instanceof UniversalType -> {
        if (!universalType.isAssignableFrom(sourceType)) {
          throw new UnificationException(destType, sourceType);
        }
      }
      case UniversalType _ when sourceType instanceof ClassType classType -> {
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
        var sourceFieldTypes = new LinkedHashMap<>(sourceStructType.memberTypes());
        var destFieldTypes = new LinkedHashMap<>(destStructType.memberTypes());
        destStructType.memberTypes().forEach((fieldName, destFieldType) -> {
          var sourceFieldType = sourceFieldTypes.remove(fieldName);
          if (!sourceStructType.isRow() && sourceFieldType == null) {
            throw new UnificationException(destType, sourceType);
//          } else if (!sourceStructType.isRow()) {
          } else if (sourceFieldType != null) {
            destFieldTypes.remove(fieldName);
            unify(destFieldType, sourceFieldType);
          }
        });

        if (!sourceFieldTypes.isEmpty() && destStructType.rowModifier() instanceof StructType.RowModifier.NamedRow namedRow) {
          var typeVar = (TypeVariable) namedRow.type();
          var structType = StructType.synthetic(sourceFieldTypes);
          unifyTypeVariable(typeVar, structType);
        }
        if (!destFieldTypes.isEmpty() && sourceStructType.rowModifier() instanceof StructType.RowModifier.NamedRow namedRow) {
          var typeVar = (TypeVariable) namedRow.type();
          var structType = StructType.synthetic(destFieldTypes);
          unifyTypeVariable(typeVar, structType);
        }
      }
      case SumType destSumType when sourceType instanceof SumType sourceSumType
          && destSumType.qualifiedTypeName().equals(sourceSumType.qualifiedTypeName()) -> {
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
      case StructType destStructType when sourceType instanceof SumType sourceSumType -> {
        var matchingVariant = sourceSumType.types()
            .stream()
            .filter(t -> t.isAssignableFrom(destStructType))
            .findFirst()
            .orElseThrow(() -> new UnificationException(destType, sourceType));
        unify(destStructType, matchingVariant);
      }
      case SumType destSumType when sourceType instanceof StructType sourceStructType -> {
        var matchingVariant = destSumType.types()
            .stream()
            .filter(t -> t.isAssignableFrom(sourceStructType))
            .findFirst()
            .orElseThrow(() -> new UnificationException(destType, sourceType));
        unify(matchingVariant, sourceStructType);
      }
      case ClassType destClassType when sourceType instanceof ClassType sourceClassType -> {
        for (int i = 0; i < destClassType.typeArguments().size(); i++) {
          var destArgType = destClassType.typeArguments().get(i);
          var sourceArgType = sourceClassType.typeArguments().get(i);
          unify(destArgType, sourceArgType);
        }
      }
      case ClassType destClassType when sourceType instanceof BuiltinType -> {
        if (!destClassType.isAssignableFrom(sourceType)) {
          throw new UnificationException(destType, sourceType);
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
    @Nullable private final Type resolvedDestType;

    UnificationException(Type destType, Type sourceType, @Nullable Type resolvedDestType) {
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
