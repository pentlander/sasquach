package com.pentlander.sasquach.name;

import static java.util.function.Predicate.*;

import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.SumTypeNode.VariantTypeNode;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.ast.UnqualifiedStructName;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LiteralStruct;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
  private final NameResolutionDataBuilder nameData = NameResolutionDataBuilder.builder();
  private final Deque<Loop> loopStack = new ArrayDeque<>();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();
  private final List<NameResolutionResult> resolutionResults = new ArrayList<>();

  private final List<TypeParameter> typeParameters = new ArrayList<>();

  private final LocalVariableStack localVarStack;

  /**
   * Set if currently resolving a named function. Used to resolve recursion.
   */
  @Nullable
  private NamedFunction namedFunction = null;

  public MemberScopedNameResolver(ModuleScopedNameResolver moduleScopedNameResolver) {
    this(moduleScopedNameResolver, null);
  }

  private MemberScopedNameResolver(ModuleScopedNameResolver moduleScopedNameResolver,
      LocalVariableStack parentVariableStack) {
    this.moduleScopedNameResolver = moduleScopedNameResolver;
    this.localVarStack = new LocalVariableStack(parentVariableStack, errors::add);
  }

  public NameResolutionResult resolve(LiteralStruct.Field field) {
    resolve(field.value());
    return resolutionResult();
  }

  public NameResolutionResult resolve(NamedFunction function) {
    this.namedFunction = function;
    resolveFunc(function.function());
    return resolutionResult();
  }

  private NameResolutionResult resolutionResult() {
    return new NameResolutionResult(nameData.build(), errors.build()).merge(resolutionResults);
  }

  // Need to check that there isn't already a local function or module alias with this captureName
  private void addLocalVariable(LocalVariable localVariable) {
    localVarStack.addLocalVariable(localVariable);
  }

  private void pushScope() {
    localVarStack.pushScope();
  }

  private void popScope() {
    localVarStack.popScope();
  }

  private Optional<Class<?>> getForeign(Id id) {
    return moduleScopedNameResolver.resolveForeignClass(id.name());
  }

  private void resolve(VariableDeclaration variableDeclaration) {
    addLocalVariable(variableDeclaration);
    resolve(variableDeclaration.expression());
  }

  private void resolve(Struct struct) {
    switch (struct) {
      case ModuleStruct moduleStruct -> moduleStruct.useList().forEach(this::resolve);
      case NamedStruct namedStruct ->
          moduleScopedNameResolver.resolveVariantTypeNode(namedStruct.name().toString())
              .ifPresent(typeNode -> nameData.addNamedStructTypes(namedStruct, typeNode.aliasId()));
      case LiteralStruct literalStruct -> literalStruct.spreads().forEach(this::resolve);
    }
    struct.functions().forEach(function -> resolveNestedFunc(function.function()));
    struct.fields().forEach(field -> resolve(field.value()));
  }

  private void resolve(ForeignFieldAccess fieldAccess) {
    getForeign(fieldAccess.classAlias()).ifPresentOrElse(clazz -> {
      try {
        var field = clazz.getField(fieldAccess.fieldName());
        nameData.addForeignFieldAccesses(fieldAccess, field);
      } catch (NoSuchFieldException e) {
        errors.add(new NameNotFoundError(fieldAccess.id(), "foreign field"));
      }
    }, () -> errors.add(new NameNotFoundError(fieldAccess.classAlias(), "foreign class")));
  }

  private void resolve(ForeignFunctionCall foreignFunctionCall) {
    getForeign(foreignFunctionCall.classAlias())
        .flatMap(clazz -> new ForeignClassResolver().resolveFuncCall(foreignFunctionCall, clazz))
        .ifPresentOrElse(foreignFunctions -> nameData.addForeignFunctions(
                foreignFunctionCall.functionId(),
                foreignFunctions),
            () -> errors.add(new NameNotFoundError(
                foreignFunctionCall.classAlias(),
                "foreign class")));
  }

  private void resolve(LocalFunctionCall localFunctionCall) {
    // Resolve as function defined within the module
    var func = moduleScopedNameResolver.resolveFunction(localFunctionCall.name());
    var funcId = localFunctionCall.functionId();
    var funcName = namedFunction != null ? namedFunction.name() : null;
    if (func.isPresent() && !localFunctionCall.name().equals(funcName)) {
      nameData.addLocalFunctionCalls(funcId, new QualifiedFunction(
          moduleScopedNameResolver.moduleDeclaration().id(),
          func.get().id(),
          func.get().function()
      ));
    } else {
      // Resolve as function defined in local variables
      var localVar = localVarStack.resolveLocalVar(localFunctionCall);
      if (localVar.isPresent()) {
        nameData.addLocalFunctionCalls(funcId, localVar.get());
      } else {
        // Resolve as a variant constructor
        moduleScopedNameResolver.resolveVariantTypeNode(localFunctionCall.name())
            .ifPresentOrElse(typeNode -> {
              var id = typeNode.id();
              var variantTuple = Struct.variantTupleStruct(new UnqualifiedStructName(id.name()),
                  localFunctionCall.arguments(),
                  localFunctionCall.range());

              nameData.addLocalFunctionCalls(
                  funcId,
                  new VariantStructConstructor(id, variantTuple));

              var alias = moduleScopedNameResolver.resolveTypeAlias(typeNode.aliasId().name())
                  .orElseThrow();
              nameData.addNamedStructTypes(variantTuple, alias.id());
            }, () -> errors.add(new NameNotFoundError(localFunctionCall.functionId(), "function")));
      }
    }
  }

  // Inside a block you can access the variables defined before it. Code after the block should
  // not resolve variables defined inside the block.
  private void resolve(Block block) {
    pushScope();
    block.expressions().forEach(this::resolve);
    popScope();
  }

  private void resolveFunc(Function function) {
    pushScope();

    var funcSignature = function.functionSignature();
    var resolver = new TypeNameResolver(typeParameters, moduleScopedNameResolver);
    var result = resolver.resolveTypeNode(funcSignature);
    nameData.addTypeAliases(result.namedTypes().entrySet());
    typeParameters.addAll(funcSignature.typeParameters());
    errors.addAll(result.errors());
    funcSignature.parameters().forEach(MemberScopedNameResolver.this::addLocalVariable);

    resolve(function.expression());
    popScope();
  }

  private void resolveNestedFunc(Function function) {
    var resolver = new MemberScopedNameResolver(moduleScopedNameResolver, localVarStack);
    resolver.typeParameters.addAll(typeParameters);

    resolver.resolveFunc(function);

    nameData.addFuncCaptures(function, resolver.localVarStack.captures());
    resolutionResults.add(resolver.resolutionResult());
  }

  // Determine what a variable reference refers to. It could refer to a local variable, function
  // parameter, module, or variant singleton.
  private void resolve(VarReference varReference) {
    var name = varReference.name();
    var localVariable = localVarStack.resolveLocalVar(varReference);
    if (localVariable.isPresent()) {
      nameData.addVarReferences(varReference, new ReferenceDeclaration.Local(localVariable.get()));
    } else {
      var module = moduleScopedNameResolver.resolveModule(name);
      if (module.isPresent()) {
        nameData.addVarReferences(varReference, new ReferenceDeclaration.Module(module.get()));
      } else {
        var variantNode = moduleScopedNameResolver.resolveVariantTypeNode(varReference.name());
        if (variantNode.isPresent()) {
          nameData.addVarReferences(varReference,
              new ReferenceDeclaration.Singleton((VariantTypeNode.Singleton) variantNode.get()));
        } else {
          errors.add(new NameNotFoundError(varReference.id(), "variable, parameter, or module"));
        }
      }
    }
  }

  private void resolve(Loop loop) {
    pushScope();
    loopStack.addLast(loop);
    loop.varDeclarations().forEach(this::resolve);
    resolve(loop.expression());
    loopStack.removeLast();
    popScope();
  }

  private void resolve(Recur recur) {
    var loop = loopStack.getLast();
    if (loop != null) {
      nameData.addRecurPoints(recur, loop);
    } else if (namedFunction != null) {
      nameData.addRecurPoints(recur, namedFunction.function());
    } else {
      throw new IllegalStateException();
    }
    recur.arguments().forEach(this::resolve);
  }

  private void resolve(Match match) {
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
    nameData.addMatchTypeNodes(match, branchTypeNodes);
  }

  private void resolve(ApplyOperator applyOperator) {
    resolve(applyOperator.expression());
    resolve(applyOperator.functionCall());
  }

  void resolve(Node node) {
    throw new IllegalStateException("Unable to handle: " + node);
  }

  private void resolve(Expression expr) {
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
      case Value _ -> {
      }
      case VariableDeclaration variableDeclaration -> resolve(variableDeclaration);
      case VarReference varReference -> resolve(varReference);
      case Recur recur -> resolve(recur);
      case Loop loop -> resolve(loop);
      case ApplyOperator applyOperator -> resolve(applyOperator);
      case Match match -> resolve(match);
      case Not not -> resolve(not.expression());
    }
  }

  private void resolve(BinaryExpression binaryExpression) {
    resolve(binaryExpression.left());
    resolve(binaryExpression.right());
  }

  private void resolve(FunctionCall functionCall) {
    functionCall.arguments().forEach(this::resolve);
    switch (functionCall) {
      case LocalFunctionCall localFuncCall -> resolve(localFuncCall);
      case MemberFunctionCall memberFuncCall -> resolve(memberFuncCall.structExpression());
      case ForeignFunctionCall foreignFunctionCall -> resolve(foreignFunctionCall);
    }
  }

  private void resolve(IfExpression ifExpression) {
    resolve(ifExpression.condition());
    resolve(ifExpression.trueExpression());
    if (ifExpression.falseExpression() != null) {
      resolve(ifExpression.falseExpression());
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
  public record VariantStructConstructor(Id id, Struct struct) implements FunctionCallTarget {}

  public record QualifiedFunction(QualifiedModuleId ownerId, Id id, Function function) implements
      FunctionCallTarget {}
}
