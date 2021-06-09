package com.pentlander.sasquach.ast;

import java.util.List;

public record ModuleDeclaration(String name, List<Function> functions) {
}
