package com.pentlander.sasquach.ast;

public record CompilationUnit(ModuleDeclaration module) {
    public String getClassName() {
        return module.name();
    }
}
