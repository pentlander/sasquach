package com.pentlander.sasquach.type;

import java.util.Optional;

public final class TypeUtils {
  private TypeUtils() {}

  /**
   * Cast a type to a struct if possible.
   *
   * @return Optional of StructType or empty if conversion not possible.
   */
  public static Optional<StructType> asStructType(Type type) {
    return switch (type) {
      case StructType structType -> Optional.of(structType);
      case ResolvedNamedType resolvedNamedType -> asStructType(resolvedNamedType.type());
      case default -> Optional.empty();
    };
  }
}
