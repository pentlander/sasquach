package com.pentlander.sasquach.type;

public sealed interface VariantType extends Type permits SingletonType, StructType {}
