package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.name.QualifiedModuleName;
import java.util.Objects;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveTask;

class ResolutionTask extends RecursiveTask<NameResolutionResult> {
  private final QualifiedModuleName moduleName;
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  private final Phaser phaser;
  private final int arrivalPhase;

  ResolutionTask(
      QualifiedModuleName moduleName,
      ModuleScopedNameResolver moduleScopedNameResolver,
      Phaser phaser
  ) {
    this.moduleName = moduleName;
    this.moduleScopedNameResolver = moduleScopedNameResolver;
    this.phaser = phaser;
    this.arrivalPhase = phaser.register();
  }

  @Override
  protected NameResolutionResult compute() {
    moduleScopedNameResolver.resolveTypeDefs();
    phaser.arriveAndAwaitAdvance();
    return moduleScopedNameResolver.resolveBody();
  }

  public ModuleScopedNameResolver moduleScopedNameResolver() {
    return moduleScopedNameResolver;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof ResolutionTask task && moduleName.equals(task.moduleName));
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleName);
  }
}
