package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Type of a class.
 * <p>This type is used to represent foreign class types.</p>
 */
public record ClassType(Class<?> typeClass, List<Type> typeArguments) implements Type, TypeNester {
  public ClassType(Class<?> typeClass) {
    this(typeClass, List.of());
  }

  @Override
  public String typeNameStr() {
    return typeClass.getName();
  }

  @Override
  public ClassDesc classDesc() {
    return typeClass.describeConstable().orElseThrow();
  }

  @Override
  public String internalName() {
    return typeNameStr().replace(".", "/");
  }

  @Override
  public boolean isAssignableFrom(Type other) {
    if (other instanceof ClassType classType && typeClass.isAssignableFrom(classType.typeClass)) {
      var typeParams = typeClass.getTypeParameters();
      var otherTypeParams = classType.typeClass.getTypeParameters();
      if (typeParams.length == 0 && otherTypeParams.length == 0) {
        return true;
      } else if (typeParams.length != otherTypeParams.length) {
        return false;
      } else if (typeArguments.size() != classType.typeArguments.size()) {
        return false;
      }

      for (int i = 0; i < typeParams.length; i++) {
        var arg = typeArguments.get(i);
        var otherArg = classType.typeArguments.get(i);
        if (!arg.isAssignableFrom(otherArg)) {
          return false;
        }
      }
      return true;
    } else if (other instanceof BuiltinType builtinType) {
      return typeClass.isAssignableFrom(builtinType.typeClass());
    }
    return typeClass.equals(Object.class);
  }

  @Override
  public String toPrettyString() {
    var typeArgs = !typeArguments.isEmpty() ? typeArguments.stream()
        .map(Type::toPrettyString)
        .collect(Collectors.joining(", ", "[", "]")) : "";
    return typeNameStr() + typeArgs;
  }
}
