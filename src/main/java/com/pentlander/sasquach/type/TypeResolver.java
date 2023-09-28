package com.pentlander.sasquach.type;

import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.name.NameResolutionResult;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

public class TypeResolver {
  private final Map<QualifiedModuleId, ModuleTask> moduleTasks = new HashMap<>();
  private final Map<QualifiedModuleId, FunctionsTask> functionsTasks = new HashMap<>();

  final NameResolutionResult nameResolutionResult;

  public TypeResolver(NameResolutionResult nameResolutionResult) {
    this.nameResolutionResult = nameResolutionResult;
  }

  public TypeResolutionResult resolve(CompilationUnit compilationUnit) {
    return resolve(compilationUnit.modules().stream());
  }

  public TypeResolutionResult resolve(Collection<CompilationUnit> compilationUnits) {
    return resolve(compilationUnits.stream()
        .map(CompilationUnit::modules)
        .flatMap(Collection::stream));
  }

  private TypeResolutionResult resolve(Stream<ModuleDeclaration> modules) {
    // Ensure that all modules are loaded into the map to avoid a race with the resolution
    // inside the fork
    modules.forEach(module -> {
      var resolver = new ModuleScopedTypeResolver(nameResolutionResult,
          module,
          this::getModuleType);
      moduleTasks.put(module.id(), new ModuleTask(module, resolver));
      functionsTasks.put(module.id(), new FunctionsTask(resolver));
    });
    // Fork all the tasks so they all start running
    moduleTasks.values().forEach(RecursiveTask::fork);
    // Await all the results
    Map<Expression, Type> moduleTypes = moduleTasks.values()
        .stream()
        .collect(toMap(task -> task.moduleDeclaration.struct(), RecursiveTask::join));
    var moduleResult = TypeResolutionResult.ofTypedModules(Map.of(),
        moduleTypes,
        Map.of(),
        Map.of(),
        RangedErrorList.empty());

    functionsTasks.values().forEach(RecursiveTask::fork);
    return functionsTasks.values()
        .stream()
        .reduce(moduleResult,
            (result, task) -> result.merge(task.join()),
            TypeResolutionResult::merge);
  }

  public StructType getModuleType(QualifiedModuleId qualifiedModuleId) {
    return moduleTasks.get(qualifiedModuleId).join();
  }

  private static class ModuleTask extends RecursiveTask<StructType> {
    private final ModuleDeclaration moduleDeclaration;
    private final ModuleScopedTypeResolver moduleScopedTypeResolver;

    private ModuleTask(ModuleDeclaration moduleDeclaration,
        ModuleScopedTypeResolver moduleScopedTypeResolver) {
      this.moduleDeclaration = moduleDeclaration;
      this.moduleScopedTypeResolver = moduleScopedTypeResolver;
    }

    @Override
    protected StructType compute() {
      return moduleScopedTypeResolver.resolveModuleType();
    }
  }

  private static class FunctionsTask extends RecursiveTask<TypeResolutionResult> {
    private final ModuleScopedTypeResolver moduleScopedTypeResolver;

    private FunctionsTask(ModuleScopedTypeResolver moduleScopedTypeResolver) {
      this.moduleScopedTypeResolver = moduleScopedTypeResolver;
    }

    @Override
    protected TypeResolutionResult compute() {
      return moduleScopedTypeResolver.resolveFunctions();
    }
  }
}
