package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.type.TypeParameter;

public sealed interface NamedTypeDefintion permits TypeParameter, TypeAlias {}
