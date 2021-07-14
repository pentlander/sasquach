package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class NameResolver {
  private final CompilationUnit compilationUnit;
  private final Map<String, Identifier> localIdentifiers = new HashMap<>();

  public NameResolver(CompilationUnit compilationUnit) {
    this.compilationUnit = compilationUnit;
  }

  void n(Node node) {
    if (node instanceof Function f) {
      f.parameters()
    }
  }

  static class ModuleNameResolver {
    private final ModuleDeclaration moduleDeclaration;
    private final Map<String, Use> useAliases = new HashMap<>();
    private final Map<VarReference, Identifier> varRefMap = new HashMap<>();

    ModuleNameResolver(ModuleDeclaration moduleDeclaration) {
      this.moduleDeclaration = moduleDeclaration;
    }

    void resolve(Node node, Deque<LocalVariable> localVariableStack) {
      if (node instanceof LocalVariable localVar) {
        localVariableStack.addFirst(localVar);
        if (localVar instanceof FunctionParameter funcParam) {
          resolve(funcParam.typeNode(), localVariableStack);
        }
      } else if (node instanceof Use use) {
        useAliases.put(use.alias().name(), use);
      } else if (node instanceof VarReference varRef) {
        getUse(varRef.name()).ifPresent(use -> varRefMap.put(varRef, use.alias()));
      } else if (node instanceof Function func) {
        var stack = new ArrayDeque<LocalVariable>();
        func.parameters().forEach(param -> resolve(param, stack));
      }
    }

    private Optional<Use> getUse(String name) {
      return Optional.ofNullable(useAliases.get(name));
    }
  }
}
