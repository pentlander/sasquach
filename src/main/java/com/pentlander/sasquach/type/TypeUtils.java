package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TypeUtils {
  private TypeUtils() {
  }

  public static String internalName(Class<?> clazz) {
    return org.objectweb.asm.Type.getInternalName(clazz);
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
      case default -> Optional.empty();
    };
  }

  public static Optional<FunctionType> asFunctionType(Type type) {
    return switch (type) {
      case FunctionType structType -> Optional.of(structType);
      case ResolvedNamedType resolvedNamedType -> asFunctionType(resolvedNamedType.type());
      case default -> Optional.empty();
    };
  }

  public static String typeWithArgsToString(String typeName, Collection<Type> typeArgs) {
    var typeArgString = !typeArgs.isEmpty() ? typeArgs.stream()
        .map(Type::toPrettyString)
        .collect(Collectors.joining(", ", "[", "]")) : "";
    return typeName + typeArgString;
  }

  public static void t() {
    var list = new ArrayList<String>();
    list.add("test");

    var iter = list.iterator();
    var item = iter.next();
    System.out.println(item);
  }

  public static ClassDesc classDesc(Class<?> clazz) {
    return clazz.describeConstable().orElseThrow();
  }

  public static ClassDesc classDesc(String internalName) {
    return ClassDesc.of(internalName.replace('/', '.'));
  }
}
