package com.pentlander.sasquach.name;

import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

public class ModuleResolver {
  // Map of qualified module name to resolver
  private final Map<String, ModuleScopedNameResolver> moduleScopedResolvers = new HashMap<>();
  private final Map<String, ResolutionTask> moduleScopedResolverTasks = new HashMap<>();

  public NameResolutionResult resolveCompilationUnits(Collection<CompilationUnit> compilationUnits) {
    return resolveCompilationUnits(compilationUnits.stream().map(CompilationUnit::modules)
        .flatMap(List::stream));
  }

  private NameResolutionResult resolveCompilationUnits(Stream<ModuleDeclaration> modules) {
    return modules.map(module -> {
      var resTask = new ResolutionTask(module.name(), module);
      moduleScopedResolverTasks.put(module.name(), resTask);
      return resTask.fork();
    }).reduce(
        NameResolutionResult.empty(),
        (result, task) -> result.merge(task.join()),
        NameResolutionResult::merge);
  }

  public NameResolutionResult resolveCompilationUnit(List<ModuleDeclaration> modules) {
    return resolveCompilationUnits(modules.stream());
  }

  public NameResolutionResult resolveCompilationUnit(CompilationUnit compilationUnit) {
    return resolveCompilationUnit(compilationUnit.modules());
  }

  public ModuleScopedNameResolver resolveModule(String qualifiedModuleName) {
    return Objects.requireNonNull(
        moduleScopedResolverTasks.get(qualifiedModuleName),
        qualifiedModuleName).moduleScopedNameResolver;
  }

  private class ResolutionTask extends RecursiveTask<NameResolutionResult> {
    private final String moduleName;
    private final ModuleScopedNameResolver moduleScopedNameResolver;

    private ResolutionTask(String moduleName, ModuleDeclaration module) {
      this.moduleName = moduleName;
      this.moduleScopedNameResolver = new ModuleScopedNameResolver(module, ModuleResolver.this);
    }

    @Override
    protected NameResolutionResult compute() {
       return moduleScopedNameResolver.resolve();
    }
  }
}
