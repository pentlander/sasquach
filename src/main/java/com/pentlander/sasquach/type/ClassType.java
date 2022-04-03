package com.pentlander.sasquach.type;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Type of a class.
 * <p>This type is used to represent foreign class types.</p>
 */
public record ClassType(Class<?> typeClass, List<Type> typeArguments) implements Type,
    ParameterizedType {
  public ClassType(Class<?> typeClass) {
    this(typeClass, List.of());
  }
  public ClassType(String typeName) {
    this(lookup(typeName));
  }

  private static Class<?> lookup(String typeName) {
    try {
      return MethodHandles.lookup().findClass(typeName.replace("/", "."));
    } catch (ClassNotFoundException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String typeName() {
    return typeClass.getName();
  }

  @Override
  public String descriptor() {
    return "L%s;".formatted(internalName());
  }

  @Override
  public String internalName() {
    return typeName().replace(".", "/");
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
    } else if (other instanceof ForeignFieldType foreignFieldType) {
      return isAssignableFrom(foreignFieldType.type());
    } else if (typeClass.equals(Object.class)) {
      if (other instanceof BuiltinType builtinType) {
        return builtinType == BuiltinType.STRING;
      }
      return true;
    }
    return false;
  }

  @Override
  public String toPrettyString() {
    var typeArgs = !typeArguments.isEmpty() ? typeArguments.stream().map(Type::toPrettyString)
        .collect(Collectors.joining(", ", "[", "]")) : "";
    return typeName() + typeArgs;
  }
}
