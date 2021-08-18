package com.pentlander.sasquach.name;

import com.pentlander.sasquach.Error;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModuleResolver {
  // Map of qualified module name to resolver
  private final Map<String, ModuleScopedNameResolver> moduleScopedResolvers = new HashMap<>();

  public List<Error> resolveCompilationUnit(CompilationUnit compilationUnit) {
    List<Error> errors = new ArrayList<>();
    for (var module : compilationUnit.modules()) {
      var resolver = new ModuleScopedNameResolver(module, this);
      errors.addAll(resolver.resolve().errors());
      moduleScopedResolvers.put(module.name(), resolver);
    }
    return errors;
  }

  public ModuleScopedNameResolver resolveModule(String qualifiedModuleName) {
    return Objects.requireNonNull(moduleScopedResolvers.get(qualifiedModuleName));
  }
}
