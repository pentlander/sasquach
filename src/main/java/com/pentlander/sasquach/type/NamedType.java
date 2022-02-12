package com.pentlander.sasquach.type;

public sealed interface NamedType extends Type permits LocalNamedType, ModuleNamedType {}
