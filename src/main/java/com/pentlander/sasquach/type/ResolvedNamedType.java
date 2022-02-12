package com.pentlander.sasquach.type;

public sealed interface ResolvedNamedType extends Type permits ResolvedLocalNamedType,
    ResolvedModuleNamedType {
  Type type();
}
