package com.pentlander.sasquach.type;

import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.lang.constant.ClassDesc;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

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
      case TypeVariable typeVariable ->
          typeVariable.resolvedType().flatMap(TypeUtils::asStructType);
      default -> Optional.empty();
    };
  }

  public static <T extends Type> Optional<T> asType(Class<T> clazz, Type type) {
    return switch (type) {
      case ResolvedNamedType resolvedNamedType -> asType(clazz, resolvedNamedType.type());
      case TypeVariable typeVariable -> typeVariable.resolvedType().flatMap(t -> asType(clazz, t));
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

  public static String typeWithParamsToString(String typeName, Collection<TypeParameter> typeParams) {
    var typeArgString = !typeParams.isEmpty() ? typeParams.stream()
        .map(TypeParameter::toPrettyString)
        .collect(Collectors.joining(", ", "[", "]")) : "";
    return typeName + typeArgString;
  }

  public static Type reify(@Nullable Type type) {
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

  static Map<UnqualifiedTypeName, Type> typeParamsToUniversal(List<TypeParameterNode> typeAlias) {
    return typeParams(typeAlias, TypeParameterNode::toUniversal);
  }

  static Map<UnqualifiedTypeName, Type> typeParams(Collection<TypeParameterNode> typeParams,
      java.util.function.Function<TypeParameterNode, Type> paramFunc) {
    return typeParams.stream().collect(toMap(TypeParameterNode::name, paramFunc));
  }
}
