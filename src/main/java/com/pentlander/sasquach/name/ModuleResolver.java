package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

public class ModuleResolver {
  // Map of qualified module captureName to resolver
  private final ConcurrentMap<QualifiedModuleName, ResolutionTask> moduleScopedResolverTasks =
      new ConcurrentHashMap<>();

  public NameResolutionResult resolveCompilationUnits(Collection<CompilationUnit> compilationUnits) {
    return resolveCompilationUnits(compilationUnits.stream().map(CompilationUnit::modules)
        .flatMap(List::stream));
  }

  // TODO There's still a race condition here, will probably need to construct a DAG of
  //      dependencies to solve for good
  private NameResolutionResult resolveCompilationUnits(Stream<ModuleDeclaration> modules) {
    // Ensure that all modules are loaded into the map to avoid a race with the resolution
    // inside the fork
    var typeDefPhaser = new Phaser();
    modules.forEach(module -> {
      var moduleNameResolver = new ModuleScopedNameResolver(module, this);
      var resTask = new ResolutionTask(module.name(), moduleNameResolver, typeDefPhaser);
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

  public ModuleScopedNameResolver resolveModule(QualifiedModuleName qualifiedModuleName) {
    var task = moduleScopedResolverTasks.get(qualifiedModuleName);
    if (task != null) {
      // Race condition here. Need to resolve all type aliases before fields and func signatures so
      // any types referred to by the field/func def are already done.
      return task.moduleScopedNameResolver();
    }
    return null;
  }


}
