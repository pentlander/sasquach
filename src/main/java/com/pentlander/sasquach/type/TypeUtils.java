package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TypeUtils {
  private TypeUtils() {
  }

  /**
   * Cast a type to a struct if possible.
   *
   * @return Optional of StructType or empty if conversion not possible.
   */
  public static Optional<StructType> asStructType(Type type) {
    return switch (type) {
      case StructType structType -> Optional.of(structType);
      case ResolvedNamedType resolvedNamedType -> asStructType(resolvedNamedType.type());
      case TypeVariable typeVariable -> asStructType(typeVariable.resolvedType().orElseThrow());
      default -> Optional.empty();
    };
  }

  public static <T extends Type> Optional<T> asType(Class<T> clazz, Type type) {
    return switch (type) {
      case ResolvedNamedType resolvedNamedType -> asType(clazz, resolvedNamedType.type());
      case TypeVariable typeVariable -> asType(clazz, typeVariable.resolvedType().orElseThrow());
      default -> {
        if (clazz.isInstance(type)) {
          yield Optional.of(clazz.cast(type));
        } else {
          yield Optional.empty();
        }
      }
    };
  }

  public static Optional<FunctionType> asFunctionType(Type type) {
    return switch (type) {
      case FunctionType structType -> Optional.of(structType);
      case ResolvedNamedType resolvedNamedType -> asFunctionType(resolvedNamedType.type());
      case TypeVariable typeVariable -> asFunctionType(typeVariable.resolvedType().orElseThrow());
      default -> Optional.empty();
    };
  }

  public static String typeWithArgsToString(String typeName, Collection<Type> typeArgs) {
    var typeArgString = !typeArgs.isEmpty() ? typeArgs.stream()
        .map(Type::toPrettyString)
        .collect(Collectors.joining(", ", "[", "]")) : "";
    return typeName + typeArgString;
  }

  public static Type reify(Type type) {
    return switch (type) {
      case ResolvedNamedType resolvedNamedType -> reify(resolvedNamedType.type());
      case TypeVariable typeVariable -> reify(typeVariable.resolvedType().orElseThrow());
      case null -> null;
      default -> type;
    };
  }

  public static ClassDesc classDesc(Class<?> clazz) {
    return clazz.describeConstable().orElseThrow();
  }

  public static ClassDesc classDesc(String internalName) {
    return ClassDesc.of(internalName.replace('/', '.'));
  }
}
