package com.pentlander.sasquach.ast;

public sealed interface StructName extends Name permits QualifiedModuleName, QualifiedStructName,
    UnqualifiedStructName {
}
