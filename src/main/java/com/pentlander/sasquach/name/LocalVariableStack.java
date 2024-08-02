package com.pentlander.sasquach.name;

import com.pentlander.sasquach.InternalCompilerException;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.function.Consumer;

public class LocalVariableStack {
  private final Deque<Map<String, LocalVariable>> localVariableStacks = new ArrayDeque<>();
  private final SequencedSet<LocalVariable> captures = new LinkedHashSet<>();
  private final LocalVariableStack parentStack;
  private final Consumer<RangedError> errorConsumer;

  public LocalVariableStack(LocalVariableStack parentStack, Consumer<RangedError> errorConsumer) {
    this.parentStack = parentStack;
    this.errorConsumer = errorConsumer;
  }

  public void addLocalVariable(LocalVariable localVariable) {
    var map = localVariableStacks.getFirst();
    var existingVar = map.put(localVariable.name(), localVariable);
    if (existingVar != null) {
      errorConsumer.accept(new DuplicateNameError(localVariable.id(), existingVar.id()));
      throw new InternalCompilerException(
          "Found existing variable in scope named " + existingVar.name());
    }
  }
  public Optional<LocalVariable> resolveLocalVar(VarReference varReference) {
    return resolveLocalVar(varReference.name());
  }

  public Optional<LocalVariable> resolveLocalVar(LocalFunctionCall localFunctionCall) {
    return resolveLocalVar(localFunctionCall.name());
  }

  private Optional<LocalVariable> resolveCurrentFrame(String localVarName) {
    for (var localVars : localVariableStacks) {
      var localVar = localVars.get(localVarName);
      if (localVar != null) {
        return Optional.of(localVar);
      }
    }
    return Optional.empty();
  }

  private Optional<LocalVariable> resolveLocalVar(String localVarName) {
    var currentFrameVar = resolveCurrentFrame(localVarName);
    if (currentFrameVar.isPresent()) {
      return currentFrameVar;
    }

    // Check for the variable declaration in the parent frame
    if (parentStack != null) {
      var parentVar = parentStack.resolveLocalVar(localVarName);
      // If it exists in the parent frame, add it to the set of captures for all subsequent child
      // frames until the grandchild that requested it is reached
      parentVar.ifPresent(captures::add);
      return parentVar;
    }

    return Optional.empty();
  }

  /** Returns all the variables captured from a parent stack frame. */
  public SequencedSet<LocalVariable> captures() {
    return Collections.unmodifiableSequencedSet(captures);
  }

  public void pushScope() {
    localVariableStacks.addFirst(new LinkedHashMap<>());
  }

  public void popScope() {
    localVariableStacks.removeFirst();
  }
}
