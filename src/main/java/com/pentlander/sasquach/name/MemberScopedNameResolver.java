package com.pentlander.sasquach.name;

import com.pentlander.sasquach.InternalCompilerException;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.InvocationKind;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LiteralStruct;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.ModuleStruct;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.NamedStruct;
import com.pentlander.sasquach.ast.expression.Not;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.type.TypeParameter;
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
  private final ModuleScopedNameResolver moduleScopedNameResolver;
  /**
   * Map of type nodes to their definition sites.
   */
  private final Map<TypeNode, NamedTypeDefinition> typeAliases = new HashMap<>();
  private final Map<ForeignFieldAccess, Field> foreignFieldAccesses = new HashMap<>();
  private final Map<Identifier, ForeignFunctions> foreignFunctions = new HashMap<>();
  private final Map<Identifier, FunctionCallTarget> localFunctionCalls = new HashMap<>();
  private final Map<VarReference, ReferenceDeclaration> varReferences = new HashMap<>();
  private final Map<NamedStruct, Identifier> namedStructRefs = new HashMap<>();
  private final Deque<Map<String, LocalVariable>> localVariableStacks = new ArrayDeque<>();
  private final Deque<Loop> loopStack = new ArrayDeque<>();
  private final Map<Recur, RecurPoint> recurPoints = new HashMap<>();
  private final Map<Match, List<TypeNode>> matchTypeNodes = new HashMap<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();
  private final Visitor visitor = new Visitor();
  private final List<NameResolutionResult> resolutionResults = new ArrayList<>();

  private final List<TypeParameter> typeParameters = new ArrayList<>();

  /**
   * Set if currently resolving a named function. Used to resolve recursion.
   */
  private NamedFunction namedFunction = null;

  public MemberScopedNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this.moduleScopedNameResolver = moduleScopedNameResolver;
  }

  public NameResolutionResult resolve(Expression expression) {
    visitor.resolve(expression);
    return resolutionResult();
  }

  public NameResolutionResult resolve(LiteralStruct.Field field) {
    visitor.resolve(field.value());
    return resolutionResult();
  }

  public NameResolutionResult resolve(NamedFunction function) {
    this.namedFunction = function;
    visitor.resolveFunc(function.function());
    return resolutionResult();
  }

  private NameResolutionResult resolutionResult() {
    return new NameResolutionResult(typeAliases,
        foreignFieldAccesses,
        foreignFunctions,
        localFunctionCalls,
        varReferences,
        namedStructRefs,
        recurPoints,
        matchTypeNodes,
        errors.build()).merge(resolutionResults);
  }

  // Need to check that there isn't already a local function or module alias with this captureName
  private void addLocalVariable(LocalVariable localVariable) {
    var map = localVariableStacks.getFirst();
    var existingVar = map.put(localVariable.name(), localVariable);
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

  private class Visitor {

    public void resolve(VariableDeclaration variableDeclaration) {
      addLocalVariable(variableDeclaration);
      resolve(variableDeclaration.expression());
    }

    public void resolve(Struct struct) {
      switch (struct) {
        case ModuleStruct moduleStruct -> moduleStruct.useList().forEach(this::resolve);
        case NamedStruct namedStruct ->
            moduleScopedNameResolver.resolveVariantTypeNode(namedStruct.name())
                .ifPresent(typeNode -> namedStructRefs.put(namedStruct, typeNode.aliasId()));
        case LiteralStruct literalStruct -> literalStruct.spreads().forEach(this::resolve);
      }
      struct.functions().forEach(function -> resolveNestedFunc(function.function()));
      struct.fields().forEach(field -> resolve(field.value()));
    }

    public void resolve(ForeignFieldAccess fieldAccess) {
      getForeign(fieldAccess.classAlias()).ifPresentOrElse(clazz -> {
        try {
          var field = clazz.getField(fieldAccess.fieldName());
          foreignFieldAccesses.put(fieldAccess, field);
        } catch (NoSuchFieldException e) {
          errors.add(new NameNotFoundError(fieldAccess.id(), "foreign field"));
        }
      }, () -> errors.add(new NameNotFoundError(fieldAccess.classAlias(), "foreign class")));
    }

    public void resolve(ForeignFunctionCall foreignFunctionCall) {
      getForeign(foreignFunctionCall.classAlias()).ifPresentOrElse(clazz -> {
            var matchingForeignFunctions = new ArrayList<ForeignFunctionHandle>();
            var funcName = foreignFunctionCall.name();
            var isConstructor = funcName.equals("new");
            var lookup = MethodHandles.lookup();
            if (isConstructor) {
              for (var constructor : clazz.getConstructors()) {
                try {
                  var methodHandle = lookup.unreflectConstructor(constructor);
                  matchingForeignFunctions.add(new ForeignFunctionHandle(methodHandle,
                      InvocationKind.SPECIAL,
                      constructor));
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
              foreignFunctions.put(foreignFunctionCall.functionId(),
                  new ForeignFunctions(clazz, matchingForeignFunctions));
            }
          },
          () -> errors.add(new NameNotFoundError(foreignFunctionCall.classAlias(),
              "foreign class")));
    }

    public void resolve(LocalFunctionCall localFunctionCall) {
      // Resolve as function defined within the module
      var func = moduleScopedNameResolver.resolveFunction(localFunctionCall.name());
      var funcName = namedFunction != null ? namedFunction.name() : null;
      if (func.isPresent() && !localFunctionCall.name().equals(funcName)) {
        localFunctionCalls.put(localFunctionCall.functionId(), new QualifiedFunction(
            moduleScopedNameResolver.moduleDeclaration().id(),
            func.get().id(),
            func.get().function()));
      } else {
        // Resolve as function defined in local variables
        var localVar = resolveLocalVar(localFunctionCall);
        if (localVar.isPresent()) {
          localFunctionCalls.put(localFunctionCall.functionId(), localVar.get());
        } else {
          // Resolve as a variant constructor
          moduleScopedNameResolver.resolveVariantTypeNode(localFunctionCall.name())
              .ifPresentOrElse(typeNode -> {
                    var id = typeNode.id();
                    var variantTuple = Struct.variantTupleStruct(id.name(),
                        localFunctionCall.arguments(),
                        localFunctionCall.range());

                    localFunctionCalls.put(localFunctionCall.functionId(), new VariantStructConstructor(
                        id, variantTuple));

                    var alias = moduleScopedNameResolver.resolveTypeAlias(typeNode.aliasId().name())
                        .orElseThrow();
                    namedStructRefs.put(variantTuple, alias.id());
                  },
                  () -> errors.add(new NameNotFoundError(localFunctionCall.functionId(),
                      "function")));
        }
      }
    }

    // Inside a block you can access the variables defined before it. Code after the block should
    // not resolve variables defined inside the block.
    public void resolve(Block block) {
      pushScope();
      block.expressions().forEach(this::resolve);
      popScope();
    }

    public void resolveFunc(Function function) {
      pushScope();

      var funcSignature = function.functionSignature();
      var resolver = new TypeNameResolver(typeParameters, moduleScopedNameResolver);
      var result = resolver.resolveTypeNode(funcSignature);
      typeAliases.putAll(result.namedTypes());
      typeParameters.addAll(funcSignature.typeParameters());
      errors.addAll(result.errors());
      funcSignature.parameters().forEach(MemberScopedNameResolver.this::addLocalVariable);

      resolve(function.expression());
      popScope();
    }

    public void resolveNestedFunc(Function function) {
      var resolver = new MemberScopedNameResolver(moduleScopedNameResolver);
      resolver.typeParameters.addAll(typeParameters);
      resolver.localVariableStacks.addAll(localVariableStacks);

      resolver.visitor.resolveFunc(function);
      resolutionResults.add(resolver.resolutionResult());
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


    // Determine what a variable reference refers to. It could refer to a local variable, function
    // parameter, module, or variant singleton.
    public void resolve(VarReference varReference) {
      var name = varReference.name();
      var localVariable = resolveLocalVar(varReference);
      if (localVariable.isPresent()) {
        varReferences.put(varReference, new ReferenceDeclaration.Local(localVariable.get()));
      } else {
        var module = moduleScopedNameResolver.resolveModule(name);
        if (module.isPresent()) {
          varReferences.put(varReference, new ReferenceDeclaration.Module(module.get()));
        } else {
          var variantNode = moduleScopedNameResolver.resolveVariantTypeNode(varReference.name());
          if (variantNode.isPresent()) {
            varReferences.put(varReference,
                new ReferenceDeclaration.Singleton((VariantTypeNode.Singleton) variantNode.get()));
          } else {
            errors.add(new NameNotFoundError(varReference.id(), "variable, parameter, or module"));
          }
        }
      }
    }

    public void resolve(Loop loop) {
      pushScope();
      loopStack.addLast(loop);
      loop.varDeclarations().forEach(this::resolve);
      resolve(loop.expression());
      loopStack.removeLast();
      popScope();
    }

    public void resolve(Recur recur) {
      var loop = loopStack.getLast();
      if (loop != null) {
        recurPoints.put(recur, loop);
      } else if (namedFunction != null) {
        recurPoints.put(recur, namedFunction.function());
      } else {
        throw new IllegalStateException();
      }
      recur.arguments().forEach(this::resolve);
    }

    public void resolve(Match match) {
      resolve(match.expr());
      var branchTypeNodes = new ArrayList<TypeNode>();
      for (var branch : match.branches()) {
        pushScope();
        switch (branch.pattern()) {
          case Pattern.Singleton singleton -> {
            var nodeType = moduleScopedNameResolver.resolveVariantTypeNode(singleton.id().name());
            nodeType.ifPresentOrElse(branchTypeNodes::add,
                () -> errors.add(new NameNotFoundError(singleton.id(), "singleton variant")));
          }
          case Pattern.VariantTuple tuple -> {
            var nodeType = moduleScopedNameResolver.resolveVariantTypeNode(tuple.id().name());
            nodeType.ifPresentOrElse(branchTypeNodes::add,
                () -> errors.add(new NameNotFoundError(tuple.id(), "tuple variant")));
            tuple.bindings().forEach(MemberScopedNameResolver.this::addLocalVariable);
          }
          case Pattern.VariantStruct struct -> {
            var nodeType = moduleScopedNameResolver.resolveVariantTypeNode(struct.id().name());
            nodeType.ifPresentOrElse(branchTypeNodes::add,
                () -> errors.add(new NameNotFoundError(struct.id(), "struct variant")));
            struct.bindings().forEach(MemberScopedNameResolver.this::addLocalVariable);
          }
        }
        resolve(branch.expr());
        popScope();
      }
      matchTypeNodes.put(match, branchTypeNodes);
    }

    public void resolve(ApplyOperator applyOperator) {
      resolve(applyOperator.expression());
      resolve(applyOperator.functionCall());
    }

    public void resolve(Node node) {
      throw new IllegalStateException("Unable to handle: " + node);
    }

    public void resolve(Expression expr) {
      switch (expr) {
        case ArrayValue arrayValue -> arrayValue.expressions().forEach(this::resolve);
        case BinaryExpression binExpr -> resolve(binExpr);
        case Block block -> resolve(block);
        case FieldAccess fieldAccess -> resolve(fieldAccess.expr());
        case ForeignFieldAccess fieldAccess -> resolve(fieldAccess);
        case FunctionCall funcCall -> resolve(funcCall);
        case Function func -> resolveNestedFunc(func);
        case IfExpression ifExpr -> resolve(ifExpr);
        case PrintStatement printStatement -> resolve(printStatement.expression());
        case Struct struct -> resolve(struct);
        case Value _ -> {}
        case VariableDeclaration variableDeclaration -> resolve(variableDeclaration);
        case VarReference varReference -> resolve(varReference);
        case Recur recur -> resolve(recur);
        case Loop loop -> resolve(loop);
        case ApplyOperator applyOperator -> resolve(applyOperator);
        case Match match -> resolve(match);
        case Not not -> resolve(not.expression());
      };
    }

    public void resolve(BinaryExpression binaryExpression) {
      resolve(binaryExpression.left());
      resolve(binaryExpression.right());
    }

    public void resolve(FunctionCall functionCall) {
      functionCall.arguments().forEach(this::resolve);
      switch (functionCall) {
        case LocalFunctionCall localFuncCall -> resolve(localFuncCall);
        case MemberFunctionCall memberFuncCall -> resolve(memberFuncCall.structExpression());
        case ForeignFunctionCall foreignFunctionCall -> resolve(foreignFunctionCall);
      }
    }

    public void resolve(IfExpression ifExpression) {
      resolve(ifExpression.condition());
      resolve(ifExpression.trueExpression());
      if (ifExpression.falseExpression() != null) {
        resolve(ifExpression.falseExpression());
      }
    }
  }

  public sealed interface ReferenceDeclaration {
    String toPrettyString();

    record Local(LocalVariable localVariable) implements ReferenceDeclaration {
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

    record Singleton(VariantTypeNode.Singleton node) implements ReferenceDeclaration {
      @Override
      public String toPrettyString() {
        return node.toPrettyString();
      }
    }
  }

  /**
   * Constructs which may be invoked liked functions.
   **/
  public sealed interface FunctionCallTarget permits LocalVariable, QualifiedFunction,
      VariantStructConstructor {}

  /**
   * A named tuple variant may be invoked like a function, e.g. Foo("bar").
   **/
  public record VariantStructConstructor(Identifier id, Struct struct) implements FunctionCallTarget {}

  public record QualifiedFunction(QualifiedModuleId ownerId, Identifier id,
                                  Function function) implements FunctionCallTarget {}
}
