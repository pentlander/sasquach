package com.pentlander.sasquach.type;

/**
 * Marker interface for types that may contain type parameters.
 */
public sealed interface ParameterizedType extends Type permits FunctionType, StructType,
    TypeParameter {}
