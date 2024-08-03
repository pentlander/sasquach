package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.type.TypeUtils.reify;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Branch;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.Pattern.VariantStruct;
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
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.TBranch;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionSignature;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.TPattern.TSingleton;
import com.pentlander.sasquach.tast.TPattern.TVariantStruct;
import com.pentlander.sasquach.tast.TPattern.TVariantTuple;
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
import com.pentlander.sasquach.tast.expression.TLiteralStructBuilder;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall.TargetKind;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.tast.expression.TLoop;
import com.pentlander.sasquach.tast.expression.TMatch;
import com.pentlander.sasquach.tast.expression.TMemberFunctionCall;
import com.pentlander.sasquach.tast.expression.TModuleStructBuilder;
import com.pentlander.sasquach.tast.expression.TNot;
import com.pentlander.sasquach.tast.expression.TPrintStatement;
import com.pentlander.sasquach.tast.expression.TRecur;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Local;
import com.pentlander.sasquach.tast.expression.TVariableDeclaration;
import com.pentlander.sasquach.tast.expression.TVariantStructBuilder;
import com.pentlander.sasquach.tast.expression.TypedExprWrapper;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.ModuleScopedTypeResolver.ModuleTypeProvider;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.LocalVar;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.Module;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.Singleton;
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
import org.jspecify.annotations.Nullable;

public class MemberScopedTypeResolver {
  private final Map<Id, TLocalVariable> localVariables = new HashMap<>();
  private final Map<Expression, TypedExpression> typedExprs = new HashMap<>();
  private final TypeUnifier typeUnifier = new TypeUnifier();
  private final Builder errors = RangedErrorList.builder();
  private final AtomicInteger typeVarNum = new AtomicInteger();

  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleScopedTypes moduleScopedTypes;
  @Nullable private Node nodeResolving = null;
  @Nullable private Node currentNode = null;

  public MemberScopedTypeResolver(NameResolutionResult nameResolutionResult,
      ModuleTypeProvider moduleTypeProvider, ModuleScopedTypes moduleScopedTypes) {
    this.nameResolutionResult = nameResolutionResult;
    this.namedTypeResolver = new NamedTypeResolver(nameResolutionResult);
    this.moduleScopedTypes = moduleScopedTypes;
  }

  public TypeResolutionResult checkType(NamedFunction namedFunction) {
    nodeResolving = namedFunction;
    try {
      var typedFunction = (TFunction) check(namedFunction.function(),
          namedFunction.functionSignature().type());
      return TypeResolutionResult.ofTypedMember(new TNamedFunction(namedFunction.id(),
          typedFunction), errors());
    } catch (RuntimeException e) {
      throw new TypeResolutionException("Failed at node: " + currentNode, e);
    }
  }

  public TypeResolutionResult checkField(Field field) {
    nodeResolving = field;
    var type = infer(field.value());
    var typedField = new TField(field.id(), type);
    return TypeResolutionResult.ofTypedMember(typedField, errors());
  }

  public RangedErrorList errors() {
    return errors.build().concat(namedTypeResolver.errors());
  }

  FunctionType convertUniversals(FunctionType type, Range range) {
    var typeParams = typeParams(type.typeParameters(),
        param -> new TypeVariable(param.typeName() + typeVarNum.getAndIncrement()));
    return (FunctionType) namedTypeResolver.resolveNames(type, typeParams, range);
  }

  SumType convertUniversals(SumType type, Range range) {
    var typeParams = typeParams(type.typeParameters(),
        param -> new TypeVariable(param.typeName() + typeVarNum.getAndIncrement()));
    return (SumType) namedTypeResolver.resolveNames(type, typeParams, range);
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

  public TypedExpression check(Expression expr, Type type) {
    return switch (expr) {
      // Check that the function matches the given type
      case Function func when type instanceof FunctionType funcType -> {
        List<TFunctionParameter> funcParams = new ArrayList<>();
        for (int i = 0; i < func.parameters().size(); i++) {
          var param = func.parameters().get(i);
          var paramType = funcType.parameterTypes().get(i);
          var typedVar = new TFunctionParameter(param.id(), paramType, param.range());
          funcParams.add(typedVar);
          putLocalVarType(param, typedVar);
        }

        var typedExpr = check(func.expression(), funcType.returnType());
        memoizeExpr(func, typedExpr);
        var typedFuncSig = new TFunctionSignature(funcParams,
            func.typeParameters(),
            funcType.returnType(),
            func.range());
        var captures = nameResolutionResult.getFunctionCaptures(func)
            .stream()
            .map(this::getLocalVar)
            .map(Optional::orElseThrow)
            .toList();
        yield new TFunction(typedFuncSig, typedExpr, captures);
      }
      default -> switch (type) {
        case ResolvedNamedType resolvedType -> check(expr, resolvedType.type());
        default -> {
          var typedExpr = infer(expr);
          try {
            typeUnifier.unify(typedExpr.type(), type);
            yield typedExpr;
          } catch (UnificationException e) {
            var msg = e.resolvedDestType()
                .map(resolvedDestType -> "Type '%s' should be '%s', but found '%s'".formatted(e.destType()
                        .toPrettyString(),
                    resolvedDestType.toPrettyString(),
                    e.sourceType().toPrettyString()))
                .orElseGet(() -> "Type should be '%s', but found '%s'".formatted(e.sourceType()
                    .toPrettyString(), e.destType().toPrettyString()));
            yield addError(expr, new TypeMismatchError(msg, expr.range()));
          }
        }
      };
    };
  }

  public TypedExpression infer(Expression expr) {
    currentNode = expr;
    var memoizedTypedExpr = typedExprs.get(expr);
    if (memoizedTypedExpr != null) {
      return memoizedTypedExpr;
    }

    TypedExpression typedExpr = switch (expr) {
      case Value value -> value;
      case VariableDeclaration varDecl -> {
        var exprType = infer(varDecl.expression());
        var localVar = new TVariableDeclaration(varDecl.id(), exprType, varDecl.range());
        putLocalVarType(varDecl, localVar);
        yield localVar;
      }
      case VarReference varRef -> resolveVarReference(varRef);
      case BinaryExpression binExpr -> {
        var left = binExpr.left();
        var right = binExpr.right();
        var leftTypedExpr = infer(left);
        var rightTypedExpr = check(right, leftTypedExpr.type());

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
        var typedExprs = arrayVal.expressions()
            .stream()
            .map(arrayExpr -> check(arrayExpr, elemType))
            .toList();
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
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        yield switch (recurPoint) {
          case Function func -> {
            var funcType = convertUniversals((FunctionType) infer(func).type(), func.range());
            var typedExprs = checkFuncArgs("recur",
                recur.arguments(),
                funcType.parameterTypes(),
                recur.range());
            var typedFunc = (TFunction) infer(func);
            yield new TRecur(typedFunc.parameters(),
                typedExprs,
                typeUnifier.resolve(funcType.returnType()),
                recur.range());
          }
          case Loop loop -> {
            var typedVarDecls = loop.varDeclarations()
                .stream()
                .map(variableDeclaration -> (TVariableDeclaration) infer(variableDeclaration))
                .toList();
            var typedExprs = checkFuncArgs("recur",
                recur.arguments(),
                typedVarDecls.stream().map(TVariableDeclaration::variableType).toList(),
                recur.range());
            yield new TRecur(typedVarDecls,
                typedExprs,
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
        var paramTypes = func.parameters().stream().map(param -> {
          var paramType = new TypeVariable(param.name() + lvl);
          var typedParam = new TFunctionParameter(param.id(), paramType, param.range());
          putLocalVarType(param, typedParam);
          return typedParam;
        }).toList();
        var typedBodyExpr = infer(func.expression());
        var typedFuncSig = new TFunctionSignature(paramTypes,
            func.typeParameters(),
            typedBodyExpr.type(),
            func.range());
        var captures = nameResolutionResult.getFunctionCaptures(func)
            .stream()
            .map(this::getLocalVar)
            .map(Optional::orElseThrow)
            .toList();
        yield new TFunction(typedFuncSig, typedBodyExpr, captures);
      }
      case ApplyOperator applyOperator -> {
        var funcCall = applyOperator.toFunctionCall();
        yield new TApplyOperator(infer(funcCall), funcCall.range());
      }
      case Match match -> resolveMatch(match);
      case Not(var notExpr, var range) -> {
        var tExpr = check(notExpr, BuiltinType.BOOLEAN);
        yield new TNot(tExpr, range);
      }
    };

    memoizeExpr(expr,
        requireNonNull(typedExpr, () -> "Null expression type for: %s".formatted(expr)));
    return typedExpr;
  }

  private TVarReference resolveVarReference(VarReference varRef) {
    return switch (moduleScopedTypes.getVarReferenceType(varRef)) {
      case Module(var moduleId, var fieldType) ->
          new TVarReference(varRef.id(), new RefDeclaration.Module(moduleId), fieldType);
      case LocalVar(var localVar) -> {
        var typedLocalVar = getLocalVar(localVar).orElseThrow(() -> new IllegalStateException(
            "Unable to find local: " + localVar));
        yield new TVarReference(varRef.id(),
            new Local(typedLocalVar),
            typedLocalVar.variableType());
      }
      case Singleton(var sumType, var singletonType) ->
          new TVarReference(varRef.id(), new RefDeclaration.Singleton(singletonType), sumType);
    };
  }

  private TStruct resolveStruct(Struct struct) {
    var typedFunctions = struct.functions().stream().map(func -> {
      var typedFunc = infer(func.function());
      return new TNamedFunction(func.id(), (TFunction) typedFunc);
    }).toList();

    return switch (struct) {
      // Literal structs have all of their fields inferred since they're not "based" on any
      // pre-existing type
      case LiteralStruct s -> {
        var typedFields = struct.fields()
            .stream()
            .map(field -> new TField(field.id(), infer(field.value())))
            .toList();
        var spreads = s.spreads().stream().map(varRef -> {
          var tVarRef = resolveVarReference(varRef);
          if (!(tVarRef.type() instanceof StructType)) {
            addError(varRef,
                new TypeMismatchError("Expected variable '%s' to be type struct, found: '%s'".formatted(
                    varRef.name(),
                    tVarRef.type().toPrettyString()), varRef.range()));
          }
          return tVarRef;
        }).toList();

        yield TLiteralStructBuilder.builder()
            .fields(typedFields)
            .functions(typedFunctions)
            .spreads(spreads)
            .range(s.range())
            .build();
      }
      case ModuleStruct s -> {
        var typedFields = struct.fields()
            .stream()
            .map(field -> new TField(field.id(), infer(field.value())))
            .toList();
        yield TModuleStructBuilder.builder()
            .name(s.name())
            .fields(typedFields)
            .functions(typedFunctions)
            .range(s.range())
            .build();
      }
      // Named structs need to have their field types checked against their type definition, similar
      // to functions.
      case NamedStruct s -> {
        var sumType = moduleScopedTypes.getSumType(nameResolutionResult.getNamedStructType(s));
        var convertedSumType = convertUniversals(sumType, s.range());
        // TODO This is dumb. The captureName resolution step already determines what variant this
        //  particular struct actually is. Pipe it through instead of this hack.
        var variant = convertedSumType.types()
            .stream()
            .filter(t -> t.typeName().endsWith(s.name()))
            .findFirst()
            .map(StructType.class::cast)
            .orElseThrow();
        var typedFields = struct.fields().stream().map(field -> {
          var typedExpr = check(field.value(), variant.fieldType(field.name()));
          return new TField(field.id(), typedExpr);
        }).toList();

        var constructorParams = List.copyOf(variant.fieldTypes().values());
        var type = (SumType) typeUnifier.resolve(convertedSumType);
        yield TVariantStructBuilder.builder()
            .name(sumType.moduleName().qualifyInner(s.name()))
            .fields(typedFields)
            .functions(typedFunctions)
            .constructorParams(constructorParams)
            .type(type)
            .range(s.range())
            .build();
      }
    };
  }

  private TypedExpression resolveIfExpression(IfExpression ifExpr) {
    var condExpr = check(ifExpr.condition(), BuiltinType.BOOLEAN);

    var typedTrueExpr = infer(ifExpr.trueExpression());
    var falseExpr = ifExpr.falseExpression();
    Type type;
    TypedExpression falseTypedExpr = null;
    if (falseExpr != null) {
      falseTypedExpr = check(falseExpr, typedTrueExpr.type());
      type = typedTrueExpr.type();
    } else {
      type = BuiltinType.VOID;
    }

    return new TIfExpression(condExpr, typedTrueExpr, falseTypedExpr, type, ifExpr.range());
  }

  private TypedExpression resolveMatch(Match match) {
    var typedExpr = infer(match.expr());
    if (reify(typedExpr.type()) instanceof SumType sumType) {
//      var convertedSumType = convertUniversals(sumType, match.range());
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
          case Pattern.Singleton singleton ->
              new TSingleton(singleton.id(), (SingletonType) variantType);
          case VariantTuple tuple -> {
            var tupleType = TypeUtils.asStructType(variantType).orElseThrow();
            // The fields types are stored in a hashmap, but we sort them to bind the variables
            // in a // consistent order. This works because the fields are named by number,
            // e.g _0, _1, etc.
            var tupleFieldTypes = tupleType.sortedFieldTypes();
            var typedPatternVars = new ArrayList<TPatternVariable>();
            for (int j = 0; j < tuple.bindings().size(); j++) {
              var binding = tuple.bindings().get(j);
              var fieldType = tupleFieldTypes.get(j);
              var typedVar = new TPatternVariable(binding.id(), fieldType);
              putLocalVarType(binding, typedVar);
              typedPatternVars.add(typedVar);
            }
            yield new TVariantTuple(tuple.id(),
                tupleType,
                typedPatternVars,
                tuple.range());
          }
          case VariantStruct struct -> {
            var structType = TypeUtils.asStructType(variantType).orElseThrow();
            var typedPatternVars = new ArrayList<TPatternVariable>();
            for (var binding : struct.bindings()) {
              var fieldType = structType.fieldType(binding.id().name());
              var typedVar = new TPatternVariable(binding.id(), fieldType);
              putLocalVarType(binding, typedVar);
              typedPatternVars.add(typedVar);
            }
            yield new TVariantStruct(struct.id(),
                structType,
                typedPatternVars,
                struct.range());
          }
        };

        // Infer the type of the first branch, check that the rest of the branches match the first
        TypedExpression branchTypedExpr;
        if (i == 0) {
          branchTypedExpr = infer(branch.expr());
          returnType = branchTypedExpr.type();
        } else {
          branchTypedExpr = check(branch.expr(), returnType);
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
      return List.of();
    }

    // Handle mismatch between arg types and parameter types
    var typedExprs = new ArrayList<TypedExpression>(args.size());
    for (int i = 0; i < args.size(); i++) {
      var arg = args.get(i);
      var paramType = paramTypes.get(i);
      typedExprs.add(check(arg, paramType));
    }

    return typedExprs;
  }

  private TypedExpression resolveFunctionCall(FunctionCall funcCall) {
    TFunctionCall typedFuncCall;
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
        var callTarget = nameResolutionResult.getLocalFunctionCallTarget(localFuncCall);
        var targetKind = switch (callTarget) {
          case QualifiedFunction qualifiedFunction ->
              new TargetKind.QualifiedFunction(qualifiedFunction.ownerId());
          // Since we're just inferring the literal struct that's created here, we end up with
          // the literal struct type, not the struct type that's defined in the module. This
          // means that the actual constructor with possible generic parameters is not found and
          // it fails at runtime.
          case VariantStructConstructor variantConstructor ->
              new TargetKind.VariantStructConstructor((TStruct) infer(variantConstructor.struct()));
          case LocalVariable localVar ->
              new TargetKind.LocalVariable(getLocalVar(localVar).orElseThrow());
        };
        var resolvedFuncType = convertUniversals(funcType, range);
        var typedExprs = checkFuncArgs(name, args, resolvedFuncType.parameterTypes(), range);
        typedFuncCall = new TLocalFunctionCall(localFuncCall.functionId(),
            targetKind,
            typedExprs,
            funcType,
            typeUnifier.resolve(resolvedFuncType.returnType()),
            range);
      }
      case MemberFunctionCall structFuncCall -> {
        var structExpr = structFuncCall.structExpression();
        var typedExpr = infer(structExpr);
        var structType = TypeUtils.asStructType(typedExpr.type());

        if (structType.isPresent()) {
          // need to replace existential types with actual types here
          var fieldType = structType.get().fieldType(name);
          if (reify(fieldType) instanceof FunctionType fieldFuncType) {
            var funcType = convertUniversals(fieldFuncType, range);
            var typedFuncArgs = checkFuncArgs(name, args, funcType.parameterTypes(), range);
            typedFuncCall = new TMemberFunctionCall(typedExpr,
                structFuncCall.functionId(),
                funcType,
                typedFuncArgs,
                typeUnifier.resolve(funcType.returnType()),
                range);
          } else if (fieldType == null) {
            return addError(funcCall,
                new TypeMismatchError(("Struct of type '%s' has no field "
                    + "named '%s'").formatted(structType.get().toPrettyString(), name), range));
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
      case ParameterizedType paramType -> {
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
        return new TForeignFunctionCall(funcCall.classAlias(),
            funcCall.functionId(),
            foreignFuncType,
            typedExprs,
            resolvedReturnType,
            funcCall.range());
      }
    }

    var methodSignature = funcCall.name() + argTypes.stream()
        .map(Type::toPrettyString)
        .collect(joining("," + " ", "(", ")"));
    return addError(funcCall,
        new TypeLookupError("No method '%s' found on class '%s'".formatted(methodSignature,
            classType.typeName()), funcCall.range()));
  }

  private void addError(RangedError error) {
    errors.add(error);
    new UnknownType();
  }

  private TypedExpression addError(Expression expr, RangedError error) {
    errors.add(error);
    return new TypedExprWrapper(expr, new UnknownType());
  }

  private void memoizeExpr(Expression expr, TypedExpression typedExpr) {
    var type = typedExpr.type();
    if (type instanceof NamedType) {
      throw new IllegalArgumentException("Cannot save named type '%s' for expr: %s".formatted(type,
          expr));
    }
    typedExprs.put(expr, typedExpr);
  }

  private void putLocalVarType(LocalVariable localVar, TLocalVariable typedLocalVar) {
    var type = typedLocalVar.variableType();
    if (type instanceof NamedType) {
      throw new IllegalArgumentException("Cannot save named type '%s' for local variable: %s".formatted(type,
          localVar));
    }
    localVariables.put(localVar.id(), typedLocalVar);
  }

  private Optional<Type> getLocalVarType(LocalVariable localVar) {
    return Optional.ofNullable(localVariables.get(localVar.id()).variableType());
  }

  private Optional<TLocalVariable> getLocalVar(LocalVariable localVar) {
    return Optional.ofNullable(localVariables.get(localVar.id()));
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
