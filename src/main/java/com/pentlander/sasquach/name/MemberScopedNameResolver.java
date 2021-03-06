package com.pentlander.sasquach.name;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.InternalCompilerException;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.InvocationKind;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.ExpressionVisitor;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.type.Type;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
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

// do a bfs to get all the function/field names of module

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
  private final Map<TypeNode, NamedTypeDefinition> typeAliases = new HashMap<>();
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses = new HashMap<>();
  private final Map<Identifier, ForeignFunctions> foreignFunctions = new HashMap<>();
  private final Map<Identifier, FunctionCallTarget> localFunctionCalls = new HashMap<>();
  private final Map<VarReference, LocalVariable> localVarReferences = new LinkedHashMap<>();
  private final Map<VarReference, ModuleDeclaration> moduleReferences = new HashMap<>();
  private final Map<LocalVariable, Integer> localVariableIndex = new HashMap<>();
  private final Deque<Map<String, LocalVariable>> localVariableStacks = new ArrayDeque<>();
  private final Deque<Loop> loopStack = new ArrayDeque<>();
  private final Map<Recur, RecurPoint> recurPoints = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();
  private final Visitor visitor = new Visitor();
  private final List<NameResolutionResult> resolutionResults = new ArrayList<>();

  private NamedFunction namedFunction = null;

  public MemberScopedNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  public NameResolutionResult resolve(Expression expression) {
    visitor.visit(expression);
    return resolutionResult();
  }

  public NameResolutionResult resolve(NamedFunction function) {
    this.namedFunction = function;
    visitor.visit(function);
    return resolutionResult();
  }

  private NameResolutionResult resolutionResult() {
    var varReferences = new HashMap<VarReference, ReferenceDeclaration>();
    localVarReferences.forEach((varRef, localVar) -> varReferences.put(varRef,
        new ReferenceDeclaration.Local(localVar,
            requireNonNull(localVariableIndex.get(localVar)))));
    moduleReferences.forEach((varRef, mod) -> varReferences.put(varRef,
        new ReferenceDeclaration.Module(mod)));

    return new NameResolutionResult(typeAliases, foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        localVariableIndex, recurPoints, errors.build()).merge(resolutionResults);
  }

  // Need to check that there isn't already a local function or module alias with this name
  private void addLocalVariable(LocalVariable localVariable) {
    var map = localVariableStacks.getFirst();
    var existingVar = map.put(localVariable.name(), localVariable);
    localVariableIndex.put(localVariable, localVariableIndex.size());
    if (existingVar != null) {
//      moduleScopedNameResolver.moduleDeclaration().name()
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
            var matchingForeignFunctions = new ArrayList<ForeignFunctionHandle>();
            var funcName = foreignFunctionCall.name();
            var isConstructor = funcName.equals("new");
            var lookup = MethodHandles.lookup();
            if (isConstructor) {
              for (var constructor : clazz.getConstructors()) {
                try {
                  var methodHandle = lookup.unreflectConstructor(constructor);
                    matchingForeignFunctions.add(new ForeignFunctionHandle(methodHandle,
                        InvocationKind.SPECIAL, constructor));
                } catch (IllegalAccessException e) {
                  // Ignore inaccessible constructors
                }
              }
            } else {
              for (var method : clazz.getMethods()) {
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                boolean isInterface = method.getDeclaringClass().isInterface();
                try {
                  var methodHandle = lookup.unreflect(method);
                  if (method.getName().equals(funcName)) {
                    var invocationKind = isStatic ? InvocationKind.STATIC
                        : isInterface ? InvocationKind.INTERFACE : InvocationKind.VIRTUAL;
                    matchingForeignFunctions.add(new ForeignFunctionHandle(methodHandle,
                        invocationKind,
                        method));
                  }
                } catch (IllegalAccessException e) {
                  // Ignore inaccessible methods
                }
              }
            }
            if (!matchingForeignFunctions.isEmpty()) {
              foreignFunctions.put(
                  foreignFunctionCall.functionId(),
                  new ForeignFunctions(clazz, matchingForeignFunctions));
            }
          },
          () -> errors.add(new NameNotFoundError(
              foreignFunctionCall.classAlias(),
              "foreign class")));
      return null;
    }

    @Override
    public Void visit(LocalFunctionCall localFunctionCall) {
      var func = moduleScopedNameResolver.resolveFunction(localFunctionCall.name());
      if (func.isPresent() && !localFunctionCall.name().equals(namedFunction.name())) {
        localFunctionCalls.put(
            localFunctionCall.functionId(),
            new QualifiedFunction(moduleScopedNameResolver.moduleDeclaration().id(),
                func.get().id(), func.get().function()));
      } else {
        var localVar = resolveLocalVar(localFunctionCall);
        if (localVar.isPresent()) {
          localFunctionCalls.put(localFunctionCall.functionId(), localVar.get());
        } else {
          errors.add(new NameNotFoundError(localFunctionCall.functionId(), "function"));
        }
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
      if (namedFunction != null && namedFunction.function().equals(function)) {
        pushScope();
        visit(function.functionSignature());
        visit(function.expression());
        popScope();
      } else {
        var resolver = new MemberScopedNameResolver(moduleScopedNameResolver);
        resolver.pushScope();
        resolver.visitor.visit(function.functionSignature());
        resolver.visitor.visit(function.expression());
        resolutionResults.add(resolver.resolutionResult());
        resolver.popScope();
      }
      return null;
    }

    private void visit(FunctionSignature funcSignature) {
      var resolver = new TypeNameResolver(moduleScopedNameResolver);
      var result = resolver.resolveTypeNode(funcSignature);
      typeAliases.putAll(result.namedTypes());
      errors.addAll(result.errors());
      funcSignature.parameters().forEach(MemberScopedNameResolver.this::addLocalVariable);
    }

    private Optional<LocalVariable> resolveLocalVar(VarReference varReference) {
      return resolveLocalVar(varReference.name());
    }

    private Optional<LocalVariable> resolveLocalVar(LocalFunctionCall localFunctionCall) {
      return resolveLocalVar(localFunctionCall.name());
    }

    private Optional<LocalVariable> resolveLocalVar(String localVarName) {
      for (var localVars : localVariableStacks) {
        var localVar = localVars.get(localVarName);
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
    public Void visit(Loop loop) {
      pushScope();
      loopStack.addLast(loop);
      loop.varDeclarations().forEach(this::visit);
      visit(loop.expression());
      loopStack.removeLast();
      popScope();
      return null;
    }

    @Override
    public Void visit(Recur recur) {
      var loop = loopStack.getLast();
      if (loop != null) {
        recurPoints.put(recur, loop);
      } else if (namedFunction != null) {
        recurPoints.put(recur, namedFunction.function());
      } else {
        throw new IllegalStateException();
      }
      recur.arguments().forEach(this::visit);
      return null;
    }

    @Override
    public Void visit(ApplyOperator applyOperator) {
      visit(applyOperator.expression());
      visit(applyOperator.functionCall());
      return null;
    }

    @Override
    public Void visit(Node node) {
      throw new IllegalStateException("Unable to handle: " + node);
    }
  }

  public sealed interface ReferenceDeclaration {
    String toPrettyString();

    record Local(LocalVariable localVariable, int index) implements ReferenceDeclaration {
      @Override
      public String toPrettyString() {
        return localVariable.toPrettyString();
      }
    }
    record Module(ModuleDeclaration moduleDeclaration) implements ReferenceDeclaration {
      @Override
      public String toPrettyString() {
        return moduleDeclaration.toPrettyString();
      }
    }
  }

  public sealed interface FunctionCallTarget permits QualifiedFunction, LocalVariable {}

  public record QualifiedFunction(QualifiedIdentifier ownerId,
                                  Identifier id, Function function) implements FunctionCallTarget {
    public QualifiedFunction(QualifiedIdentifier ownerId, NamedFunction namedFunction) {
      this(ownerId, namedFunction.id(), namedFunction.function());
    }
  }
}
