package com.pentlander.sasquach.type;

import com.pentlander.sasquach.type.MemberScopedTypeResolver.UnknownType;
import java.lang.constant.ClassDesc;

/**
 * Represents the type of an expression.
 */
public sealed interface Type permits ArrayType, BuiltinType, ClassType, UniversalType,
    ForeignFieldType, ForeignFunctionType, FuncTypeParameter, UnknownType, NamedType,
    ParameterizedType, ResolvedNamedType, SumType, TypeVariable, VariantType {
  /**
   * Name of the type.
   */
  String typeName();

  /**
   * Internal captureName for a class.
   * <p>
   * Equivalent to a fully qualified captureName with '.' replaces with '/'. Example: java/lang/String
   * </p>
   */
  String internalName();

  ClassDesc classDesc();

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
