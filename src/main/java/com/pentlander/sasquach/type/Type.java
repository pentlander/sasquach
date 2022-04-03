package com.pentlander.sasquach.type;

import com.pentlander.sasquach.type.MemberScopedTypeResolver.UnknownType;

/**
 * Represents the type of an expression.
 */
public sealed interface Type permits ArrayType, BuiltinType, ClassType, ExistentialType,
    ForeignFieldType, ForeignFunctionType, FuncTypeParameter, UnknownType, NamedType,
    ParameterizedType, ResolvedNamedType, TypeVariable {
  /**
   * Name of the type.
   */
  String typeName();

  /**
   * Class of the type.
   * <p>
   * The class cannot always be resolved for a type. For instance, a function doesn't have a
   * canonical class representation, also a struct's class does not exist until bytecode generation
   * time.
   * </p>
   */
  Class<?> typeClass();

  /**
   * Descriptor string for the type.
   * <p>Example: (Ljava/lang/String;I)Z</p>
   */
  String descriptor();

  /**
   * Internal name for a class.
   * <p>
   * Equivalent to a fully qualified name with '.' replaces with '/'. Example: java/lang/String
   * </p>
   */
  String internalName();

  /**
   * Determines if this type is assignable from the other type.
   */
  default boolean isAssignableFrom(Type other) {
    return this.equals(other);
  }

  /**
   * Returns a prettified string that matches the type in source code.
   */
  default String toPrettyString() {
    return typeName();
  }
}
