package com.pentlander.sasquach.type;

/**
 * Marker interface for types that contains other types nested within.
 * <p>If any field in the struct is a {@link Type}, it should implement this interface. </p>
 */
public sealed interface TypeNester extends Type permits ClassType, FunctionType, ParameterizedType,
    ResolvedNamedType, StructType, SumType, TypeVariable, UniversalType {}
