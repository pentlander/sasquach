package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

public class ModuleResolver {
  // Map of qualified module name to resolver
  private final ConcurrentMap<String, ResolutionTask> moduleScopedResolverTasks =
      new ConcurrentHashMap<>();

  public NameResolutionResult resolveCompilationUnits(Collection<CompilationUnit> compilationUnits) {
    return resolveCompilationUnits(compilationUnits.stream().map(CompilationUnit::modules)
        .flatMap(List::stream));
  }

  private NameResolutionResult resolveCompilationUnits(Stream<ModuleDeclaration> modules) {
    // Ensure that all modules are loaded into the map to avoid a race with the resolution
    // inside the fork
    modules.forEach(module -> {
      var resTask = new ResolutionTask(module.name(), module);
      moduleScopedResolverTasks.put(module.name(), resTask);
    });
    // Fork all the tasks so they all start running
    moduleScopedResolverTasks.values().forEach(RecursiveTask::fork);
    // Merge all the results together
    return moduleScopedResolverTasks.values().stream().reduce(
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
    var task =  moduleScopedResolverTasks.get(qualifiedModuleName);
    return task != null ? task.moduleScopedNameResolver : null;
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

    @Override
    public boolean equals(Object o) {
      return o != null && (this == o || (o instanceof ResolutionTask task && Objects.equals(moduleName,
          task.moduleName)));
    }

    @Override
    public int hashCode() {
      return Objects.hash(moduleName);
    }
  }


}
