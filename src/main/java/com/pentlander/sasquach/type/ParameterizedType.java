package com.pentlander.sasquach.type;

/**
 * Marker interface for types that contains other types.
 * <p>If any field in the struct is a {@link Type}, it should implement this interface. </p>
 */
public sealed interface ParameterizedType extends Type permits ClassType, FunctionType,
    ResolvedNamedType, StructType, SumType, TypeVariable, UniversalType {}
