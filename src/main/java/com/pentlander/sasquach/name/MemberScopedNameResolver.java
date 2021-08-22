package com.pentlander.sasquach.name;

import static java.util.Objects.*;

import com.pentlander.sasquach.InternalCompilerException;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.ExpressionVisitor;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
use std/io/File,
use foreign java/nio/Paths,

// do a bfs to get all the funciton/field names of module

let foo = "a"
//Local var
let bar = foo
// Module import
let f = File.new()
// Foreign function
let f1 = Paths#get("foo")
// Foreign field
System#out
// Local module func
localFunction()
 */
public class MemberScopedNameResolver {
  // Map of import alias names to resolved foreign classes
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses = new HashMap<>();
  private final Map<ForeignFunctionCall, List<Executable>> foreignFunctions = new HashMap<>();
  private final Map<LocalFunctionCall, QualifiedFunction> localFunctionCalls = new HashMap<LocalFunctionCall, QualifiedFunction>();
  private final Map<VarReference, LocalVariable> localVarReferences = new LinkedHashMap<>();
  private final Map<VarReference, ModuleDeclaration> moduleReferences = new HashMap<>();
  private final Map<LocalVariable, Integer> localVariableIndex = new HashMap<>();
  private final Deque<Map<String, LocalVariable>> localVariableStacks = new ArrayDeque<>();
  private final List<RangedError> errors = new ArrayList<>();
  private final Visitor visitor = new Visitor();


  public MemberScopedNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  public ResolutionResult resolve(Expression expression) {
    visitor.visit(expression);
    return resolutionResult();
  }

  public ResolutionResult resolve(Function function) {
    pushScope();
    function.parameters().forEach(this::addLocalVariable);
    visitor.visit(function.expression());
    return resolutionResult();
  }

  private ResolutionResult resolutionResult() {
    var varReferences = new HashMap<VarReference, ReferenceDeclaration>();
    localVarReferences.forEach((varRef, localVar) -> varReferences.put(varRef,
        new ReferenceDeclaration.Local(localVar,
            requireNonNull(localVariableIndex.get(localVar)))));
    moduleReferences.forEach((varRef, mod) -> varReferences.put(varRef,
        new ReferenceDeclaration.Module(mod)));

    return new ResolutionResult(
        foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        localVariableIndex,
        Map.of(),
        errors);
  }

  // Need to check that there isn't already a local function or module alias with this name
  private void addLocalVariable(LocalVariable localVariable) {
    var map = localVariableStacks.getFirst();
    var existingVar = map.put(localVariable.name(), localVariable);
    localVariableIndex.put(localVariable, localVariableIndex.size());
    if (existingVar != null) {
      errors.add(new DuplicateNameError(localVariable.id(), existingVar.id()));
      throw new InternalCompilerException(
          "Found existing variable in scope named " + existingVar.name());
    }
  }

  private void pushScope() {
    localVariableStacks.addFirst(new LinkedHashMap<>());
  }

  private void popScope() {
    localVariableStacks.removeFirst();
  }

  private Optional<Class<?>> getForeign(Identifier id) {
    return moduleScopedNameResolver.resolveForeignClass(id.name());
  }

  private class Visitor implements ExpressionVisitor<Void> {
    @Override
    public Void defaultValue() {
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      addLocalVariable(variableDeclaration);
      return visit(variableDeclaration.expression());
    }

    @Override
    public Void visit(ForeignFieldAccess fieldAccess) {
      getForeign(fieldAccess.classAlias()).ifPresentOrElse(clazz -> {
        try {
          var field = clazz.getField(fieldAccess.fieldName());
          foreignFieldAccesses.put(fieldAccess, field);
        } catch (NoSuchFieldException e) {
          errors.add(new NameNotFoundError(fieldAccess.id(), "foreign field"));
        }
      }, () -> errors.add(new NameNotFoundError(fieldAccess.classAlias(), "foreign class")));
      return null;
    }

    @Override
    public Void visit(ForeignFunctionCall foreignFunctionCall) {
      getForeign(foreignFunctionCall.classAlias()).ifPresentOrElse(
          clazz -> {
            var matchingExecutables = new ArrayList<Executable>();
            var funcName = foreignFunctionCall.name();
            var isConstructor = funcName.equals("new");
            var executables = isConstructor ? clazz.getConstructors() : clazz.getMethods();
            for (var executable : executables) {
              var paramCount = executable.getParameterCount();
              paramCount += isConstructor || Modifier.isStatic(executable.getModifiers()) ? 0 : 1;
              if (paramCount == foreignFunctionCall.argumentCount() && (
                  isConstructor || funcName.equals(executable.getName()))) {
                matchingExecutables.add(executable);
              }
            }
            if (matchingExecutables.isEmpty()) {
              errors.add(new NameNotFoundError(
                  foreignFunctionCall.functionId(),
                  "foreign function"));
            }
            foreignFunctions.put(foreignFunctionCall, matchingExecutables);
          },
          () -> errors.add(new NameNotFoundError(
              foreignFunctionCall.classAlias(),
              "foreign class")));
      return null;
    }

    @Override
    public Void visit(LocalFunctionCall localFunctionCall) {
      var func = moduleScopedNameResolver.resolveFunction(localFunctionCall.name());
      if (func.isPresent()) {
        localFunctionCalls.put(
            localFunctionCall,
            new QualifiedFunction(moduleScopedNameResolver.moduleDeclaration().id(), func.get()));
      } else {
        errors.add(new NameNotFoundError(localFunctionCall.functionId(), "function"));
      }
      return null;
    }

    // Inside a block you can access the variables defined before it. Code after the block should
    // not resolve variables defined inside the block.
    @Override
    public Void visit(Block block) {
      pushScope();
      block.expressions().forEach(this::visit);
      popScope();
      return null;
    }

    @Override
    public Void visit(Function function) {
      pushScope();
      visit(function.functionSignature());
      return visit(function.expression());
    }

    private void visit(FunctionSignature funcSignature) {
      funcSignature.parameters().forEach(MemberScopedNameResolver.this::addLocalVariable);
    }

    private Optional<LocalVariable> resolveLocalVar(VarReference varReference) {
      for (var localVars : localVariableStacks) {
        var localVar = localVars.get(varReference.name());
        if (localVar != null) {
          return Optional.of(localVar);
        }
      }
      return Optional.empty();
    }

    @Override
    public Void visit(VarReference varReference) {
      var name = varReference.name();
      // Could refer to a local variable, function parameter, or module
      var localVariable = resolveLocalVar(varReference);
      if (localVariable.isPresent()) {
        localVarReferences.put(varReference, localVariable.get());
      } else {
        var module = moduleScopedNameResolver.resolveModule(name);
        if (module.isPresent()) {
          moduleReferences.put(varReference, module.get());
        } else {
          errors.add(new NameNotFoundError(varReference.id(), "variable, parameter, or module"));
        }
      }
      return null;
    }

    @Override
    public Void visit(Node node) {
      switch (node) {
        case FunctionSignature functionSignature-> {
          System.out.println("Visisted func sig: " + functionSignature.toPrettyString());
        }
        case FunctionParameter functionParameter -> System.out.println();
        case default -> throw new IllegalStateException("Unable to handle: " + node);
      }
      return null;
    }
  }

  public sealed interface ReferenceDeclaration {
    record Local(LocalVariable localVariable, int index) implements ReferenceDeclaration {}
    record Module(ModuleDeclaration moduleDeclaration) implements ReferenceDeclaration {}
  }

  public record QualifiedFunction(QualifiedIdentifier ownerId, Function function) {}
}
