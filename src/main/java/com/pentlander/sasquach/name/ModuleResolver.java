package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.CompilationUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ModuleResolver {
  // Map of qualified module name to resolver
  private final Map<String, ModuleScopedNameResolver> moduleScopedResolvers = new HashMap<>();

  public ResolutionResult resolveCompilationUnit(CompilationUnit compilationUnit) {
    ResolutionResult resolutionResult = ResolutionResult.empty();
    for (var module : compilationUnit.modules()) {
      var resolver = new ModuleScopedNameResolver(module, this);
      resolutionResult = resolutionResult.merge(resolver.resolve());
      moduleScopedResolvers.put(module.name(), resolver);
    }
    return resolutionResult;
  }

  public ModuleScopedNameResolver resolveModule(String qualifiedModuleName) {
    return Objects.requireNonNull(moduleScopedResolvers.get(qualifiedModuleName));
  }
}
