package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Branch;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.Pattern.VariantTuple;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.Match;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.TBranch;
import com.pentlander.sasquach.tast.TPattern;
import com.pentlander.sasquach.tast.TPatternVariable;
import com.pentlander.sasquach.tast.expression.TApplyOperator;
import com.pentlander.sasquach.tast.expression.TArrayValue;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TBooleanExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TCompareExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TMathExpression;
import com.pentlander.sasquach.tast.expression.TBlock;
import com.pentlander.sasquach.tast.expression.TFieldAccess;
import com.pentlander.sasquach.tast.expression.TForeignFieldAccess;
import com.pentlander.sasquach.tast.expression.TForeignFunctionCall;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TFunctionCall;
import com.pentlander.sasquach.tast.expression.TIfExpression;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall;
import com.pentlander.sasquach.tast.expression.TLoop;
import com.pentlander.sasquach.tast.expression.TMatch;
import com.pentlander.sasquach.tast.expression.TMemberFunctionCall;
import com.pentlander.sasquach.tast.expression.TPrintStatement;
import com.pentlander.sasquach.tast.expression.TRecur;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TStructBuilder;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVariableDeclaration;
import com.pentlander.sasquach.tast.expression.TypedExprWrapper;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.ModuleScopedTypeResolver.ModuleTypeProvider;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType;
import com.pentlander.sasquach.type.TypeUnifier.UnificationException;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class MemberScopedTypeResolver implements TypeFetcher {
  private final Map<Identifier, Type> idTypes = new HashMap<>();
  private final Map<Expression, Type> exprTypes = new HashMap<>();
  private final Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes = new HashMap<>();
  private final TypeUnifier typeUnifier = new TypeUnifier();
  private final RangedErrorList.Builder errors = RangedErrorList.builder();
  private final AtomicInteger typeVarNum = new AtomicInteger();

  private final NameResolutionResult nameResolutionResult;
  private final ModuleTypeProvider moduleTypeProvider;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleScopedTypes moduleScopedTypes;
  private Node nodeResolving = null;
  private Node currentNode = null;

  public MemberScopedTypeResolver(Map<Identifier, Type> idTypes,
      NameResolutionResult nameResolutionResult, ModuleTypeProvider moduleTypeProvider,
      ModuleScopedTypes moduleScopedTypes) {
    this.idTypes.putAll(idTypes);
    this.nameResolutionResult = nameResolutionResult;
    this.namedTypeResolver = new NamedTypeResolver(nameResolutionResult);
    this.moduleTypeProvider = moduleTypeProvider;
    this.moduleScopedTypes = moduleScopedTypes;
  }

//  private sealed interface

  @Override
  public Type getType(Expression expression) {
    return requireNonNull(exprTypes.get(expression), expression::toPrettyString);
  }

  @Override
  public Type getType(Identifier identifier) {
    return requireNonNull(idTypes.get(identifier), "Not found: " + identifier);
  }

  @Override
  public ForeignFunctionType getType(Identifier classAlias, Identifier functionName) {
    return requireNonNull(foreignFuncTypes.get(new QualifiedFunctionId(classAlias, functionName)));
  }

  public TypeResolutionResult checkType(NamedFunction namedFunction) {
    nodeResolving = namedFunction;
    try {
      check(namedFunction.function(), namedFunction.functionSignature().type());
    } catch (RuntimeException e) {
      throw new TypeResolutionException("Failed at node: " + currentNode, e);
    }
    return new TypeResolutionResult(idTypes, exprTypes, foreignFuncTypes, errors());
  }

  public TypeResolutionResult inferType(Expression expression) {
    nodeResolving = expression;
    infer(expression);
    return new TypeResolutionResult(idTypes, exprTypes, foreignFuncTypes, errors());
  }

  public RangedErrorList errors() {
    return errors.build().concat(namedTypeResolver.errors());
  }

  public record QualifiedFunctionId(Identifier classAlias, Identifier functionName) {}

  FunctionType convertUniversals(FunctionType type, Range range) {
    var typeParams = typeParams(type.typeParameters(),
        param -> new TypeVariable(param.typeName() + typeVarNum.getAndIncrement()));
    return (FunctionType) namedTypeResolver.resolveNames(type, typeParams, range);
  }

  private static Type builtinOrClassType(Class<?> clazz, List<Type> typeArgs) {
    if (clazz.componentType() != null) {
      return new ArrayType(builtinOrClassType(clazz.componentType(), typeArgs));
    }
    return Arrays.stream(BuiltinType.values())
        .filter(type -> type.typeClass().equals(clazz))
        .findFirst()
        .map(Type.class::cast)
        .orElseGet(() -> new ClassType(clazz, typeArgs));
  }

  static Map<String, Type> typeParams(Collection<TypeParameter> typeParams,
      java.util.function.Function<TypeParameter, Type> paramFunc) {
    return typeParams.stream().collect(toMap(TypeParameter::typeName, paramFunc));
  }

  @SuppressWarnings("SwitchStatementWithTooFewBranches")
  public void check(Expression expr, Type type) {
    switch (expr) {
      // Check that the function matches the given type
      case Function func when type instanceof FunctionType funcType -> {
        var resolvedType = convertUniversals(funcType, func.range());
        for (int i = 0; i < func.parameters().size(); i++) {
          var param = func.parameters().get(i);
          var paramType = resolvedType.parameterTypes().get(i);
          putLocalVarType(param, paramType);
        }

        check(func.expression(), resolvedType.returnType());
        putExprType(expr, type);
      }
      default -> {
        switch (type) {
          case ResolvedNamedType resolvedType -> check(expr, resolvedType.type());
          default -> {
            var inferredType = infer(expr).type();
            try {
              typeUnifier.unify(inferredType, type);
              idTypes.replaceAll((_id, idType) -> typeUnifier.resolve(idType));
              exprTypes.replaceAll((_expr, exprType) -> typeUnifier.resolve(exprType));
            } catch (UnificationException e) {
              var msg = e.resolvedDestType()
                  .map(resolvedDestType -> "Type '%s' should be '%s', but found '%s'".formatted(e.destType()
                          .toPrettyString(),
                      resolvedDestType.toPrettyString(),
                      e.sourceType().toPrettyString()))
                  .orElseGet(() -> "Type should be '%s', but found '%s'".formatted(e.sourceType()
                      .toPrettyString(), e.destType().toPrettyString()));
              addError(new TypeMismatchError(msg, expr.range()));
            }
          }
        }
      }
    }
  }

  public TypedExpression infer(Expression expr) {
    currentNode = expr;
    Type mtype = exprTypes.get(expr);
    if (mtype != null) {
      return new TypedExprWrapper(expr, mtype);
    }

    TypedExpression type = switch (expr) {
      case Value value -> value;
      case VariableDeclaration varDecl -> {
        var exprType = infer(varDecl.expression());
        putLocalVarType(varDecl, exprType.type());
        yield new TVariableDeclaration(varDecl.id(), exprType, varDecl.range());
      }
      case VarReference varRef -> {
        var varRefType = switch (moduleScopedTypes.getVarReferenceType(varRef)) {
          case VarRefType.Module(var fieldType) -> fieldType;
          case VarRefType.LocalVar(var localVar) ->
              getLocalVarType(localVar).orElseThrow(() -> new IllegalStateException(
                  "Unable to find local: " + localVar));
        };
        yield new TVarReference(varRef.id(), varRefType);
      }
      case BinaryExpression binExpr -> {
        var left = binExpr.left();
        var right = binExpr.right();
        var leftTypedExpr = infer(left);
        check(right, leftTypedExpr.type());

        var rightTypedExpr = new TypedExprWrapper(right, leftTypedExpr.type());
        yield switch (binExpr) {
          case CompareExpression b ->
              new TCompareExpression(b.operator(), leftTypedExpr, rightTypedExpr, b.range());
          case MathExpression b ->
              new TMathExpression(b.operator(), leftTypedExpr, rightTypedExpr, b.range());
          case BooleanExpression b ->
              new TBooleanExpression(b.operator(), leftTypedExpr, rightTypedExpr, b.range());
        };
      }
      case ArrayValue arrayVal -> {
        var elemType = arrayVal.elementType();
        var typedExprs = arrayVal.expressions().stream().map(arrayExpr -> {
          check(arrayExpr, elemType);
          return new TypedExprWrapper(arrayExpr, elemType);
        }).toList();
        yield new TArrayValue(arrayVal.type(), typedExprs, arrayVal.range());
      }
      case Block block ->
          new TBlock(block.expressions().stream().map(this::infer).toList(), block.range());
      case FieldAccess fieldAccess -> resolveFieldAccess(fieldAccess);
      case ForeignFieldAccess foreignFieldAccess -> resolveForeignFieldAccess(foreignFieldAccess);
      case ForeignFunctionCall foreignFuncCall -> resolveForeignFunctionCall(foreignFuncCall);
      case FunctionCall funcCall -> resolveFunctionCall(funcCall);
      case IfExpression ifExpr -> resolveIfExpression(ifExpr);
      case PrintStatement(var pExpr, var pRange) -> new TPrintStatement(infer(pExpr), pRange);
      case Struct struct -> resolveStruct(struct);
      case Field field -> {
        var fieldType = infer(field.value());
        yield new TField(field.id(), fieldType);
      }
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        yield switch (recurPoint) {
          case Function func -> {
            var funcType = convertUniversals((FunctionType) infer(func).type(), func.range());
            var typedExprs = checkFuncArgs("recur",
                recur.arguments(),
                funcType.parameterTypes(),
                recur.range());
            yield new TRecur(typedExprs, typeUnifier.resolve(funcType.returnType()), recur.range());
          }
          case Loop loop -> {
            var typedExprs = checkFuncArgs("recur",
                recur.arguments(),
                loop.varDeclarations().stream().map(variableDeclaration -> {
                  infer(variableDeclaration);
                  return getLocalVarType(variableDeclaration).orElseThrow();
                }).toList(),
                recur.range());
            yield new TRecur(typedExprs,
                typeUnifier.resolve(new TypeVariable("Loop" + typeVarNum.getAndIncrement())),
                recur.range());
          }
        };
      }
      case Loop loop -> {
        var typedExprs = loop.varDeclarations()
            .stream()
            .map(this::infer)
            .map(TVariableDeclaration.class::cast)
            .toList();
        yield new TLoop(typedExprs, infer(loop.expression()), loop.range());
      }
      // Should only infer anonymous function, not ones defined at the module level
      case Function func -> {
        var lvl = typeVarNum.getAndIncrement();
        var paramTypes = func.parameters()
            .stream()
            .collect(toMap(FunctionParameter::name, param -> {
              var paramType = new TypeVariable(param.name() + lvl);
              putLocalVarType(param, paramType);
              return paramType;
            }));
        var typedExpr = infer(func.expression());
        var funcType = new FunctionType(List.copyOf(paramTypes.values()),
            List.of(),
            typedExpr.type());
        yield new TFunction(func.functionSignature(), funcType, typedExpr);
      }
      case ApplyOperator applyOperator -> {
        var funcCall = applyOperator.toFunctionCall();
        yield new TApplyOperator(infer(funcCall), funcCall.range());
      }
      case Match match -> resolveMatch(match);
    };

    putExprType(expr,
        requireNonNull(type.type(), () -> "Null expression type for: %s".formatted(expr)));
    return type;
  }

  private TStruct resolveStruct(Struct struct) {
    struct.functions().forEach(func -> {
      var typedFunc = infer(func.function());
      putIdType(func.id(), typedFunc.type());
    });
    var typedFields = struct.fields()
        .stream()
        .map(field -> new TField(field.id(), infer(field)))
        .toList();
    return TStructBuilder.builder()
        .name(struct.name())
        .fields(typedFields)
        .functions(struct.functions())
        .build();
  }

  private TypedExpression resolveIfExpression(IfExpression ifExpr) {
    check(ifExpr.condition(), BuiltinType.BOOLEAN);

    var typedTrueExpr = infer(ifExpr.trueExpression());
    var falseExpr = ifExpr.falseExpression();
    Type type;
    if (falseExpr != null) {
      check(falseExpr, typedTrueExpr.type());
      type = typedTrueExpr.type();
    } else {
      type = BuiltinType.VOID;
    }

    return new TIfExpression(new TypedExprWrapper(ifExpr.condition(), BuiltinType.BOOLEAN),
        typedTrueExpr,
        typedTrueExpr,
        type,
        ifExpr.range());
  }

  private TypedExpression resolveMatch(Match match) {
    var typedExpr = infer(match.expr());
    if (typedExpr.type() instanceof SumType sumType) {
      // All the variants of the sum type with any type parameters already filled in
      var exprVariantTypes = sumType.types().stream().collect(toMap(Type::typeName, identity()));
      var matchTypeNodes = nameResolutionResult.getMatchTypeNodes(match);
      List<Branch> branches = match.branches();
      var typedBranches = new ArrayList<TBranch>();
      Type returnType = null;
      for (int i = 0; i < branches.size(); i++) {
        var branch = branches.get(i);
        var typeNode = matchTypeNodes.get(i);
        var branchVariantTypeName = typeNode.typeName();
        var variantType = requireNonNull(exprVariantTypes.remove(branchVariantTypeName));
        var typedPattern = switch (branch.pattern()) {
          case Pattern.Singleton singleton -> {
            putIdType((Identifier) singleton.id(), variantType);
            yield new TPattern.TSingleton(singleton.id(), variantType);
          }
          case VariantTuple tuple -> {
            putIdType((Identifier) tuple.id(), variantType);
            var tupleType = (StructType) variantType;
            // The fields types are stored in a hashmap, but we sort them to bind the variables
            // in a // consistent order. This works because the fields are named by number,
            // e.g _0, _1, etc.
            var tupleFieldTypes = tupleType.sortedFieldTypes();
            var typedPatternVars = new ArrayList<TPatternVariable>();
            for (int j = 0; j < tuple.bindings().size(); j++) {
              var binding = tuple.bindings().get(j);
              var fieldType = tupleFieldTypes.get(j);
              putLocalVarType(binding, fieldType);
              typedPatternVars.add(new TPatternVariable(binding.id(), fieldType));
            }
            yield new TPattern.TVariantTuple(tuple.id(), typedPatternVars, tuple.range());
          }
          case Pattern.VariantStruct struct -> {
            var structType = (StructType) variantType;
            var typedPatternVars = new ArrayList<TPatternVariable>();
            for (var binding : struct.bindings()) {
              var fieldType = structType.fieldType(binding.id().name());
              putLocalVarType(binding, fieldType);
              typedPatternVars.add(new TPatternVariable(binding.id(), fieldType));
            }
            yield new TPattern.TVariantStruct(struct.id(), typedPatternVars, struct.range());
          }
        };

        // Infer the type of the first branch, check that the rest of the branches match the first
        TypedExpression branchTypedExpr;
        if (i == 0) {
          branchTypedExpr = infer(branch.expr());
          returnType = branchTypedExpr.type();
        } else {
          check(branch.expr(), returnType);
          branchTypedExpr = new TypedExprWrapper(branch.expr(), returnType);
        }
        typedBranches.add(new TBranch(typedPattern, branchTypedExpr, branch.range()));
      }

      if (!exprVariantTypes.isEmpty()) {
        return addError(match, new MatchNotExhaustive("Match is not exhaustive", match.range()));
      }

      return new TMatch(typedExpr, typedBranches, returnType, match.range());
    } else {
      return addError(match,
          new TypeMismatchError("Type '%s' in match is not a sum type".formatted(typedExpr.toPrettyString()),
              match.expr().range()));
    }
  }

  private TypedExpression resolveFieldAccess(FieldAccess fieldAccess) {
    var typedStructExpr = infer(fieldAccess.expr());
    var structType = TypeUtils.asStructType(typedStructExpr.type());

    if (structType.isPresent()) {
      var fieldType = structType.get().fieldType(fieldAccess.fieldName());
      if (fieldType != null) {
        return new TFieldAccess(typedStructExpr, fieldAccess.id(), fieldType);
      } else {
        return addError(fieldAccess,
            new TypeMismatchError("Type '%s' does not contain field '%s'".formatted(structType.get()
                .typeName(), fieldAccess.fieldName()), fieldAccess.range()));
      }
    }

    return addError(fieldAccess, new TypeMismatchError(
        "Can only access fields on struct types, found type '%s'".formatted(typedStructExpr.toPrettyString()),
        fieldAccess.range()));
  }

  private List<TypedExpression> checkFuncArgs(String name, List<Expression> args,
      List<Type> paramTypes, Range range) {
    // Handle mismatch between arg count and parameter count
    if (args.size() != paramTypes.size()) {
      addError(new TypeMismatchError("Function '%s' expects %s arguments but found %s".formatted(name,
          paramTypes.size(),
          args.size()), range));
    }

    // Handle mismatch between arg types and parameter types
    var typedExprs = new ArrayList<TypedExpression>(args.size());
    for (int i = 0; i < args.size(); i++) {
      var arg = args.get(i);
      var paramType = paramTypes.get(i);
      check(arg, paramType);
      typedExprs.add(new TypedExprWrapper(arg, paramType));
    }

    return typedExprs;
  }

  private TypedExpression resolveFunctionCall(FunctionCall funcCall) {
    TFunctionCall typedFuncCall = null;
    var name = funcCall.name();
    var args = funcCall.arguments();
    var range = funcCall.range();

    switch (funcCall) {
      case LocalFunctionCall localFuncCall -> {
        var funcType = switch (moduleScopedTypes.getFunctionCallType(localFuncCall)) {
          case FuncCallType.Module(var type) -> type;
          case FuncCallType.LocalVar(var localVar) ->
              getLocalVarType(localVar).flatMap(TypeUtils::asFunctionType).orElseThrow();
        };
        funcType = convertUniversals(funcType, range);
        var typedExprs = checkFuncArgs(name, args, funcType.parameterTypes(), range);
        typedFuncCall = new TLocalFunctionCall(localFuncCall.functionId(),
            typedExprs,
            typeUnifier.resolve(funcType.returnType()),
            range);
      }
      case MemberFunctionCall structFuncCall -> {
        var structExpr = structFuncCall.structExpression();
        var typedExpr = infer(structExpr);
        var structType = TypeUtils.asStructType(typedExpr.type());

        if (structType.isPresent()) {
          // need to replace existential types with actual types here
          var fieldType = structType.get().fieldType(name);
          if (fieldType instanceof FunctionType fieldFuncType) {
            var funcType = convertUniversals(fieldFuncType, range);
            var typedFuncArgs = checkFuncArgs(name, args, funcType.parameterTypes(), range);
            typedFuncCall = new TMemberFunctionCall(typedExpr,
                structFuncCall.functionId(),
                typedFuncArgs,
                typeUnifier.resolve(funcType.returnType()),
                range);
          } else if (fieldType == null) {
            return addError(funcCall,
                new TypeMismatchError(("Struct of type '%s' has no field " + "named%s'").formatted(
                    structType.get().toPrettyString(),
                    name), range));
          } else {
            return addError(funcCall,
                new TypeMismatchError(("Field '%s' of type '%s' is not a " + "function").formatted(
                    name,
                    fieldType.toPrettyString()), structFuncCall.functionId().range()));
          }
        } else {
          return addError(funcCall, new TypeMismatchError(
              "Expected field access on type struct, found type '%s'".formatted(typedExpr.toPrettyString()),
              structFuncCall.structExpression().range()));
        }
      }
      case ForeignFunctionCall f -> throw new IllegalStateException(f.toString());
    }

    return typedFuncCall;
  }


  private TypedExpression resolveForeignFieldAccess(ForeignFieldAccess fieldAccess) {
    var field = nameResolutionResult.getForeignField(fieldAccess);
    var classType = new ClassType(field.getDeclaringClass());
    var accessKind =
        Modifier.isStatic(field.getModifiers()) ? FieldAccessKind.STATIC : FieldAccessKind.INSTANCE;
    var foreignFieldType = new ForeignFieldType(builtinOrClassType(field.getType(), List.of()),
        classType,
        accessKind);
    return new TForeignFieldAccess(fieldAccess.classAlias(), fieldAccess.id(), foreignFieldType);
  }

  private boolean argsMatchParamTypes(List<Type> params, List<Type> args) {
    if (params.size() != args.size()) {
      return false;
    }
    for (int i = 0; i < args.size(); i++) {
      if (!params.get(i).isAssignableFrom(args.get(i))) {
        return false;
      }
    }
    return true;
  }

  private Type javaTypeToType(java.lang.reflect.Type type,
      Map<String, TypeVariable> typeVariables) {
    return switch (type) {
      case Class<?> clazz -> {
        var typeArgs = Arrays.stream(clazz.getTypeParameters())
            .map(t -> javaTypeToType(t, typeVariables))
            .toList();
        yield builtinOrClassType(clazz, typeArgs);
      }
      case java.lang.reflect.TypeVariable<?> typeVariable ->
          typeVariables.getOrDefault(typeVariable.getName(),
              new TypeVariable(typeVariable.getName() + typeVarNum.getAndIncrement()));
      case java.lang.reflect.ParameterizedType paramType -> {
        var typeArgs = Arrays.stream(paramType.getActualTypeArguments())
            .map(t -> javaTypeToType(t, typeVariables))
            .toList();
        yield new ClassType((Class<?>) paramType.getRawType(), typeArgs);
      }
      case WildcardType wildcard -> javaTypeToType(wildcard.getUpperBounds()[0], typeVariables);
      default -> throw new IllegalStateException(type.toString());
    };
  }

  private List<? extends java.lang.reflect.TypeVariable<? extends GenericDeclaration>> javaTypeParams(
      java.lang.reflect.Type type) {
    return switch (type) {
      case Class<?> clazz -> Arrays.stream(clazz.getTypeParameters()).toList();
      case ParameterizedType paramType -> Arrays.stream(paramType.getActualTypeArguments())
          .flatMap(t -> t instanceof java.lang.reflect.TypeVariable<?> typeVar ? Stream.of(typeVar)
              : Stream.empty())
          .toList();
      default -> List.of();
    };
  }

  private Map<String, TypeVariable> executableTypeParams(Executable executable) {
    var receiverType = Optional.ofNullable(executable.getAnnotatedReceiverType())
        .map(AnnotatedType::getType);
    var receiverTypeParams = receiverType.stream().flatMap(t -> javaTypeParams(t).stream());
    var lvl = typeVarNum.getAndIncrement();
    return Stream.concat(Arrays.stream(executable.getTypeParameters()), receiverTypeParams)
        .collect(toMap(java.lang.reflect.TypeVariable::getName,
            t -> new TypeVariable(t.getName() + lvl)));
  }

  private List<Type> executableParamTypes(Executable executable,
      Map<String, TypeVariable> typeParams) {
    var receiverType = Stream.ofNullable(executable.getAnnotatedReceiverType())
        .map(AnnotatedType::getType);
    return Stream.concat(receiverType, Arrays.stream(executable.getGenericParameterTypes()))
        .map(type -> javaTypeToType(type, typeParams))
        .toList();
  }

  private TypedExpression resolveForeignFunctionCall(ForeignFunctionCall funcCall) {
    var funcCandidates = nameResolutionResult.getForeignFunction(funcCall);
    var argTypes = funcCall.arguments()
        .stream()
        .map(this::infer)
        .map(TypedExpression::type)
        .collect(toList());
    var classType = new ClassType(funcCandidates.ownerClass());

    for (var foreignFuncHandle : funcCandidates.functions()) {
      var executable = foreignFuncHandle.executable();
      var typeParams = executableTypeParams(executable);
      var paramTypes = executableParamTypes(executable, typeParams);
      if (argsMatchParamTypes(paramTypes, argTypes)) {
        var returnType = javaTypeToType(executable.getAnnotatedReturnType().getType(), typeParams);
        var typedExprs = checkFuncArgs(funcCall.name(),
            funcCall.arguments(),
            paramTypes,
            funcCall.range());
        var resolvedReturnType = typeUnifier.resolve(returnType);
        var methodHandleDesc = foreignFuncHandle.methodHandle()
            .describeConstable()
            .map(DirectMethodHandleDesc.class::cast)
            .orElseThrow();
        var castType = returnType instanceof TypeVariable ? resolvedReturnType : null;

        var foreignFuncType = new ForeignFunctionType(methodHandleDesc, castType);
        var foreignFuncCall = new TForeignFunctionCall(funcCall.classAlias(),
            funcCall.functionId(),
            foreignFuncType,
            typedExprs,
            resolvedReturnType,
            funcCall.range());
        putForeignFuncType(funcCall, foreignFuncType);
        return foreignFuncCall;
      }
    }

    var methodSignature = funcCall.name() + argTypes.stream()
        .map(Type::toPrettyString)
        .collect(joining("," + " ", "(", ")"));
    return addError(funcCall,
        new TypeLookupError("No method '%s' found on class '%s'".formatted(methodSignature,
            classType.typeName()), funcCall.range()));
  }

  private void putForeignFuncType(ForeignFunctionCall foreignFunctionCall,
      ForeignFunctionType foreignFunctionType) {
    foreignFuncTypes.put(new QualifiedFunctionId(foreignFunctionCall.classAlias(),
        foreignFunctionCall.functionId()), foreignFunctionType);
  }

  private Type addError(RangedError error) {
    errors.add(error);
    return new UnknownType();
  }

  private TypedExpression addError(Expression expr, RangedError error) {
    errors.add(error);
    return new TypedExprWrapper(expr, new UnknownType());
  }

  private void putExprType(Expression expr, Type type) {
    if (type instanceof NamedType) {
      throw new IllegalArgumentException("Cannot save named type '%s' for expr: %s".formatted(type,
          expr));
    }
    exprTypes.put(expr, type);
  }

  private Type putIdType(Identifier id, Type type) {
    if (type instanceof NamedType) {
      throw new IllegalArgumentException("Cannot save named type '%s' for ID: %s".formatted(type,
          id));
    }
    return idTypes.put(id, type);
  }

  private Type putLocalVarType(LocalVariable localVar, Type type) {
    if (type instanceof NamedType) {
      throw new IllegalArgumentException("Cannot save named type '%s' for local variable: %s".formatted(type,
          localVar));
    }
    return idTypes.put(localVar.id(), type);
  }

  private Optional<Type> getLocalVarType(LocalVariable localVar) {
    return Optional.ofNullable(idTypes.get(localVar.id()));
  }

  @Override
  public String toString() {
    return "TypeResolver{" + "idTypes=" + idTypes + ", exprTypes=" + exprTypes
        + ", foreignFuncTypes=" + foreignFuncTypes + '}';
  }

  record TypeMismatchError(String message, Range range) implements RangedError {
    @Override
    public String toPrettyString(Source source) {
      return """
          %s
          %s
          """.formatted(message, source.highlight(range));
    }
  }

  record TypeLookupError(String message, Range range) implements RangedError {
    @Override
    public String toPrettyString(Source source) {
      return """
          %s
          %s
          """.formatted(message, source.highlight(range));
    }
  }

  record MatchNotExhaustive(String message, Range range) implements RangedError {
    @Override
    public String toPrettyString(Source source) {
      return """
          %s
          %s
          """.formatted(message, source.highlight(range));
    }
  }

  static final class UnknownType implements Type {
    private final UnknownTypeException exception;

    UnknownType() {
      this.exception = new UnknownTypeException();
    }

    @Override
    public String typeName() {
      throw exception;
    }

    @Override
    public ClassDesc classDesc() {
      throw exception;
    }

    @Override
    public String internalName() {
      throw exception;
    }

    @Override
    public String toPrettyString() {
      return "unknown";
    }
  }

  static class UnknownTypeException extends RuntimeException {}

  static class TypeResolutionException extends RuntimeException {
    public TypeResolutionException(String message, Exception cause) {
      super(message, cause);
    }
  }
}
