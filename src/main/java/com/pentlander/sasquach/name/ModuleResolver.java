package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import java.util.HashMap;
import java.util.Map;

public class ModuleResolver {
  // Map of qualified module name to resolver
  private final Map<String, ModuleScopedNameResolver> moduleScopedResolvers = new HashMap<>();

  public void resolveCompilationUnit(CompilationUnit compilationUnit) {
    for (var module : compilationUnit.modules()) {
      new ModuleScopedNameResolver(module);
    }
  }
}
