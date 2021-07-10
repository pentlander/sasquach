package com.pentlander.sasquach.type;

import java.lang.invoke.MethodHandles;

/**
 * Type of a class.
 * <p>This type is used to represent foreign class types.</p>
 */
public record ClassType(Class<?> typeClass) implements Type {
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
}
