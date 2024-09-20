package com.pentlander.sasquach.type;

import static com.pentlander.sasquach.Preconditions.checkNotInstanceOf;
import static com.pentlander.sasquach.Util.concat;
import static com.pentlander.sasquach.type.TypeUtils.reify;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import com.pentlander.sasquach.AbstractRangedError;
import com.pentlander.sasquach.BasicError;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.RangedErrorList.Builder;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.Branch;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Labeled;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Pattern;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.ast.UnqualifiedTypeName;
import com.pentlander.sasquach.ast.expression.PipeOperator;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.MemberAccess;
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
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.tast.TBranch;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionParameter.Label;
import com.pentlander.sasquach.tast.TFunctionSignature;
import com.pentlander.sasquach.tast.TNamedFunction;
import com.pentlander.sasquach.tast.TPattern;
import com.pentlander.sasquach.tast.TPattern.TVariantTuple;
import com.pentlander.sasquach.tast.TPatternVariable;
import com.pentlander.sasquach.tast.expression.*;
import com.pentlander.sasquach.tast.expression.TBasicFunctionCall.TArgs;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TBooleanExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TCompareExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TMathExpression;
import com.pentlander.sasquach.tast.expression.TForeignFunctionCall.Varargs;
import com.pentlander.sasquach.tast.expression.TBasicFunctionCall.TCallTarget;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Local;
import com.pentlander.sasquach.type.FunctionType.Param;
import com.pentlander.sasquach.type.MemberScopedTypeResolver.LabeledMap.Indexed;
import com.pentlander.sasquach.type.ModuleScopedTypes.FuncCallType;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.LocalVar;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.Module;
import com.pentlander.sasquach.type.ModuleScopedTypes.VarRefType.Singleton;
import com.pentlander.sasquach.type.TypeUnifier.UnificationException;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Modifier;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class MemberScopedTypeResolver {
  private static final boolean DEBUG = true;
  private static final UnqualifiedName NAME_RECUR = new UnqualifiedName("recur");
  private final Map<Id, TLocalVariable> localVariables = new HashMap<>();
  private final Map<Expression, TypedExpression> typedExprs = new HashMap<>();
  private final TypeUnifier typeUnifier = new TypeUnifier();
  private final Builder errors = RangedErrorList.builder();
  private final AtomicInteger typeVarNum = new AtomicInteger();
  private final AtomicInteger anonStructNum = new AtomicInteger();
  private final NameResolutionResult nameResolutionResult;
  private final NamedTypeResolver namedTypeResolver;
  private final ModuleScopedTypes moduleScopedTypes;

  @Nullable private Node nodeResolving = null;
  @Nullable private Node currentNode = null;

  public MemberScopedTypeResolver(NameResolutionResult nameResolutionResult,
      ModuleScopedTypes moduleScopedTypes) {
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

  private Map<String, Type> typeParamToVar(List<TypeParameter> typeParams) {
    return typeParams(typeParams, param -> {
      return param.toTypeVariable(typeVarNum.getAndIncrement());
    });
  }
  private <T extends ParameterizedType> T convertUniversals(T type, Range range) {
    var typeParamToVar = typeParamToVar(type.typeParameters());
    return namedTypeResolver.resolveNames(type, typeParamToVar, range);
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
    return typeParams.stream().collect(toMap(TypeParameter::name, paramFunc));
  }

  public TypedExpression check(Expression expr, Type type) {
    checkNotInstanceOf(type, NamedType.class, "type must be resolved");

    return switch (expr) {
      // Check that the function matches the given type
      case Function func when type instanceof FunctionType funcType -> {
        List<TFunctionParameter> funcParams = new ArrayList<>();
        // TODO need to include type args from parent if this is a nested func
        var funcSig = namedTypeResolver.resolveTypeNode(func.functionSignature(), Map.of());
        for (int i = 0; i < func.parameters().size(); i++) {
          var param = funcSig.parameters().get(i);
          var expectedParamType = funcType.parameterTypes().get(i);
          typeUnifier.unify(param.type(), expectedParamType);
          var typedVar = new TFunctionParameter(param.id(), Label.of(param.label(), null),  expectedParamType, param.range());
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
      // When checking something that looks like literal Integer annotated with a different int-like
      // type, convert the literal to be the type it's checking against
      case Value(var valueType, var value, var range) when valueType == BuiltinType.INT
          && type instanceof BuiltinType builtinType && builtinType.isIntegerLike() ->
          new Value(builtinType, value, range);
      // Same as above but for Double
      case Value(var valueType, var value, var range) when valueType == BuiltinType.DOUBLE
          && type instanceof BuiltinType builtinType && builtinType.isDoubleLike() ->
          new Value(builtinType, value, range);
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
            yield addError(expr, new TypeMismatchError(msg, expr.range(), e));
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
        TypedExpression tExpr;
        if (varDecl.typeAnnotation() != null) {
          tExpr = check(varDecl.expression(), varDecl.typeAnnotation().type());
        } else {
          tExpr = infer(varDecl.expression());
        }
        var localVar = new TVariableDeclaration(varDecl.id(), tExpr, varDecl.range());
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
      case Block block -> {
        var tExprs = new ArrayList<TypedExpression>(block.expressions().size());
        // Loop instead of stream for easier debugger stepping
        for (var blockExpr : block.expressions()) {
          tExprs.add(infer(blockExpr));
        }
        yield new TBlock(tExprs, block.range());
      }
      case MemberAccess fieldAccess -> resolveFieldAccess(fieldAccess);
      case ForeignFieldAccess foreignFieldAccess -> resolveForeignFieldAccess(foreignFieldAccess);
      case FunctionCall funcCall -> resolveFunctionCall(funcCall);
      case IfExpression ifExpr -> resolveIfExpression(ifExpr);
      case PrintStatement(var pExpr, var pRange) -> new TPrintStatement(infer(pExpr), pRange);
      case Struct struct -> resolveStruct(struct);
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        yield switch (recurPoint) {
          case Function func -> {
            var funcType = convertUniversals((FunctionType) infer(func).type(), func.range());
            var typedExprs = checkFuncArgTypes(NAME_RECUR,
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
            var typedExprs = checkFuncArgTypes(NAME_RECUR,
                recur.arguments(),
                typedVarDecls.stream().map(TVariableDeclaration::variableType).toList(),
                recur.range());
            yield new TRecur(typedVarDecls,
                typedExprs,
                typeUnifier.resolve(new TypeVariable("Loop", typeVarNum.getAndIncrement())),
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
          Type paramType;
          var typeNode = param.typeNode();
          if (typeNode != null) {
            paramType = namedTypeResolver.resolveNames(typeNode.type(), Map.of(), typeNode.range());
          } else {
            paramType = new TypeVariable(param.name().toString(), lvl);
          }
          var typedParam = new TFunctionParameter(param.id(), Label.of(param.label(), null), paramType, param.range());
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
      case PipeOperator applyOperator -> {
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
      case Singleton(var sumType, var singletonType) -> new TVarReference(varRef.id(),
          new RefDeclaration.Singleton(singletonType),
          convertUniversals(sumType, varRef.range()));
    };
  }

  private List<TNamedFunction> typedFuncs(Struct struct) {
    return struct.functions().stream().map(func -> {
      var typedFunc = infer(func.function());
      return new TNamedFunction(func.id(), (TFunction) typedFunc);
    }).toList();
  }

  private TypedExpression resolveStruct(Struct struct) {
    return switch (struct) {
      case ModuleStruct s -> {
        var typedFunctions = typedFuncs(struct);
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
      // Literal structs have all of their fields inferred since they're not "based" on any
      // pre-existing type
      case LiteralStruct s -> {
        var typedFields = new ArrayList<TField>();
        var fieldTypes = new HashMap<UnqualifiedName, Type>();
        struct.fields()
            .forEach(field -> {
              var tExpr = infer(field.value());
              typedFields.add(new TField(field.id(), tExpr));
              fieldTypes.put(field.name(), tExpr.type());
            });
        struct.functions()
            .forEach(func -> {
              var function = func.function();
              var tExpr = infer(function);
              typedFields.add(new TField(func.id(), tExpr));

              var typeVarCount = new AtomicInteger();
              var paramMap = typeParams(
                  function.typeParameters(),
                  _ -> new UniversalType(Integer.toString(typeVarCount.getAndIncrement())));
              var normalizeFuncType = namedTypeResolver.resolveNames(
                  tExpr.type(),
                  paramMap,
                  func.range());
              fieldTypes.put(func.name(), normalizeFuncType);
            });

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

        var name = moduleScopedTypes.getLiteralStructName(fieldTypes);
        yield new TLiteralStruct(name, typedFields, spreads, s.range());
      }
      // Named structs need to have their field types checked against their type definition, similar
      // to functions.
      case NamedStruct namedStruct ->
          resolveMemberFunctionCall(new TThisExpr(moduleScopedTypes.getThisType(),
              namedStruct.range()), namedStruct.toFunctionCall());
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
      var exprVariantTypes = sumType.types().stream().collect(toMap(Type::typeNameStr, identity()));
      var matchTypeNodes = nameResolutionResult.getMatchTypeNodes(match);
      List<Branch> branches = match.branches();
      var typedBranches = new ArrayList<TBranch>();
      Type returnType = null;
      for (int i = 0; i < branches.size(); i++) {
        var branch = branches.get(i);
        var typeNode = matchTypeNodes.get(i);
        var branchVariantTypeName = typeNode.typeNameStr();
        var variantType = requireNonNull(exprVariantTypes.remove(branchVariantTypeName));
        var typedPattern = switch (branch.pattern()) {
          case Pattern.Singleton singleton ->
              new TPattern.TSingleton(singleton.id(), (SingletonType) variantType);
          case Pattern.VariantTuple tuple -> {
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
          case Pattern.VariantStruct struct -> {
            var structType = TypeUtils.asStructType(variantType).orElseThrow();
            var typedPatternVars = new ArrayList<TPatternVariable>();
            for (var binding : struct.bindings()) {
              var fieldType = structType.fieldType(binding.id().name());
              var typedVar = new TPatternVariable(binding.id(), fieldType);
              putLocalVarType(binding, typedVar);
              typedPatternVars.add(typedVar);
            }
            yield new TPattern.TVariantStruct(struct.id(),
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

  private TypedExpression resolveFieldAccess(MemberAccess memberAccess) {
    var typedStructExpr = infer(memberAccess.expr());
    var structType = TypeUtils.asStructType(typedStructExpr.type());
    var fieldName = memberAccess.fieldName();

    if (structType.isPresent()) {
      var fieldType = structType.get().fieldType(fieldName);
      if (fieldType != null) {
        return new TFieldAccess(typedStructExpr, memberAccess.id(), fieldType);
      } else {
        var variantWithSum = structType.get().constructableType(fieldName.toTypeName());
        if (variantWithSum != null) {
          var sumType = variantWithSum.sumType();
          return new TVarReference(
              memberAccess.id(),
              new RefDeclaration.Singleton((SingletonType) variantWithSum.type()),
              convertUniversals(sumType, memberAccess.range()));
        }
        return addError(
            memberAccess,
            new TypeMismatchError("Type '%s' does not contain field '%s'".formatted(structType.get()
                .toPrettyString(), memberAccess.fieldName()), memberAccess.range()));
      }
    }

    return addError(memberAccess, new TypeMismatchError(
        "Can only access fields on struct types, found type '%s'".formatted(typedStructExpr.toPrettyString()),
        memberAccess.range()));
  }

  static class LabeledMap<T extends Labeled> {
    record Indexed<T>(int idx, T item) {}

    private final List<T> items = new ArrayList<>();
    private final HashMap<UnqualifiedName, Indexed<T>> labeledItems = new LinkedHashMap<>();

    static <T extends Labeled> LabeledMap<T> of(Collection<? extends T> items) {
      var map = new LabeledMap<T>();
      map.addAll(items);
      return map;
    }

    void addAll(Collection<? extends T> items) {
      int labeledIdx = 0;
      for (var item : items) {
        if (item.label() == null) {
          this.items.add(item);
        } else {
          labeledItems.put(item.label(), new Indexed<>(labeledIdx++, item));
        }
      }
    }

    @Nullable Indexed<T> get(UnqualifiedName name) {
      return labeledItems.get(name);
    }

    int labeledSize() {
      return labeledItems.size();
    }

    List<T> positionalItems() {
      return items;
    }
  }

  private TArgs checkFuncArgs(UnqualifiedName name, List<Argument> args,
      List<FunctionType.Param> params, Range range) {
    var argsMap = LabeledMap.of(args);
    var posArgs = argsMap.positionalItems();

    var paramsMap = LabeledMap.of(params);
    var posParamTypes = paramsMap.positionalItems().stream().map(Param::type).toList();
    var posExprs = checkFuncArgTypes(name, posArgs, posParamTypes, range);
    var argIndexes = new int[args.size()];
    for (int i = 0; i < posExprs.size(); i++) {
      argIndexes[i] = i;
    }

    var argSet = argsMap.labeledItems.values()
        .stream()
        .map(Indexed::item)
        .collect(toCollection(HashSet::new));
    var labeledExprs = new TypedExpression[argsMap.labeledSize()];
    paramsMap.labeledItems.forEach((paramLabel, idxParam) -> {
      var param = idxParam.item();
      var idxArg = argsMap.get(paramLabel);
      if (idxArg == null && !param.hasDefault()) {
        errors.add(new BasicError("Missing labeled arg '%s' of type '%s'".formatted(
            paramLabel,
            param.type().toPrettyString()), range));
      } else if (idxArg != null) {
        var arg = idxArg.item();
        argSet.remove(arg);

        var tExpr = check(arg.expression(), param.type());
        labeledExprs[idxArg.idx()] = tExpr;
        argIndexes[posExprs.size() + idxParam.idx()] = idxArg.idx();
      }
    });

    argSet.forEach(arg -> errors.add(new BasicError(
        "No param labeled '%s' on the function definition".formatted(arg.label()),
        arg.range())));

    return new TArgs(argIndexes, concat(posExprs, Arrays.asList(labeledExprs)));
  }

  private List<TypedExpression> checkFuncArgTypes(UnqualifiedName name, List<Argument> args,
      List<Type> paramTypes, Range range) {
    // Handle mismatch between arg count and parameter count
    if (args.size() != paramTypes.size()) {
      addError(new TypeMismatchError("Function '%s' expects %s positional args but found %s".formatted(name,
          paramTypes.size(),
          args.size()), range));
      return List.of();
    }

    // Handle mismatch between arg types and parameter types
    var typedExprs = new ArrayList<TypedExpression>(args.size());
    for (int i = 0; i < args.size(); i++) {
      var arg = args.get(i);
      var paramType = paramTypes.get(i);
      typedExprs.add(check(arg.expression(), paramType));
    }

    return typedExprs;
  }

  private TypedExpression resolveMemberFunctionCall(
      TypedExpression structExpr, FunctionCall structFuncCall
  ) {
    var name = structFuncCall.name();
    var args = structFuncCall.arguments();
    var range = structFuncCall.range();
    var structType = TypeUtils.asStructType(structExpr.type());

    if (structType.isEmpty()) {
      return addError(structFuncCall, new TypeMismatchError(
          "Expected field access on type struct, found type '%s'".formatted(structExpr.toPrettyString()),
          structExpr.range()));
    }

    // need to replace existential types with actual types here
    var fieldType = structType.get().fieldType(name);
    if (fieldType instanceof FunctionType fieldFuncType) {
      // We only have the function type here, not the full function. Need to figure out the right
      // place to match up labeled args with their parameters
      var funcType = convertUniversals(fieldFuncType, range);
      var typedFuncArgs = checkFuncArgs(name, args, funcType.parameters(), range);
      return new TBasicFunctionCall(TCallTarget.struct(structExpr),
          structFuncCall.functionId().name(),
          funcType, // TODO I think this needs to be the unconverted type. Add a test
          typedFuncArgs,
          typeUnifier.resolve(funcType.returnType()),
          range);
    }

    if (fieldType != null) {
      return addError(structFuncCall,
          new TypeMismatchError(("Field '%s' of type '%s' is not a function").formatted(
              name,
              fieldType.toPrettyString()), structFuncCall.functionId().range()));
    }

    return addError(structFuncCall,
        new TypeMismatchError(("No function '%s' found on struct of type '%s'").formatted(name,
            structType.get().toPrettyString()), structFuncCall.range()));
  }

  private TypedExpression resolveFunctionCall(FunctionCall funcCall) {
    var name = funcCall.name();
    var args = funcCall.arguments();
    var range = funcCall.range();

    return switch (funcCall) {
      case LocalFunctionCall localFuncCall ->
          switch (moduleScopedTypes.getFunctionCallType(localFuncCall)) {
            case FuncCallType.Module() ->
                resolveMemberFunctionCall(new TThisExpr(moduleScopedTypes.getThisType(), range),
                    localFuncCall);
            case FuncCallType.LocalVar(var localVar) -> {
              var tLocalVar = getLocalVar(localVar).orElseThrow();
              var funcType = TypeUtils.asFunctionType(tLocalVar.variableType()).orElseThrow();
              var resolvedFuncType = convertUniversals(funcType, range);
              var typedExprs = checkFuncArgs(name, args, resolvedFuncType.parameters(), range);
              yield new TBasicFunctionCall(TCallTarget.localVar(tLocalVar),
                  localFuncCall.functionId().name(),
                  funcType,
                  typedExprs,
                  typeUnifier.resolve(resolvedFuncType.returnType()),
                  range);
            }
          };
      case MemberFunctionCall structFuncCall -> {
        var structExpr = structFuncCall.structExpression();
        var typedExpr = infer(structExpr);
        yield resolveMemberFunctionCall(typedExpr, structFuncCall);
      }
      case ForeignFunctionCall foreignFuncCall -> resolveForeignFunctionCall(foreignFuncCall);
    };
  }


  private TypedExpression resolveForeignFieldAccess(ForeignFieldAccess fieldAccess) {
    var field = nameResolutionResult.getForeignField(fieldAccess);
    var classType = new ClassType(field.getDeclaringClass());
    var accessKind =
        Modifier.isStatic(field.getModifiers()) ? FieldAccessKind.STATIC : FieldAccessKind.INSTANCE;
    return new TForeignFieldAccess(
        fieldAccess.classAlias(),
        fieldAccess.id(),
        classType,
        builtinOrClassType(field.getType(), List.of()),
        accessKind);
  }

  private boolean argsMatchParamTypes(List<Type> params, List<Type> args, boolean isVarArgs) {
    var numParams = params.size();
    var numArgs = args.size();
    // If it's varargs, it doesn't matter if they're not equal
    if (!isVarArgs && numArgs != numParams) {
      return false;
    } else if (isVarArgs && numArgs < numParams - 1) {
      return false;
    }

    for (int i = 0; i < numArgs; i++) {
      Type paramType;
      if (isVarArgs && i >= params.size() - 1) {
        var arrType = (ArrayType) params.getLast();
        paramType = arrType.elementType();
      } else {
        paramType = params.get(i);
      }

      if (!paramType.isAssignableFrom(args.get(i))) {
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
              new TypeVariable(typeVariable.getName(), typeVarNum.getAndIncrement()));
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
      case java.lang.reflect.ParameterizedType paramType -> Arrays.stream(paramType.getActualTypeArguments())
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
            t -> new TypeVariable(t.getName(), lvl)));
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
        .map(Argument::expression)
        .map(this::infer)
        .map(TypedExpression::type)
        .collect(toList());
    var classType = new ClassType(funcCandidates.ownerClass());

    for (var foreignFuncHandle : funcCandidates.functions()) {
      var executable = foreignFuncHandle.executable();
      var typeParams = executableTypeParams(executable);
      var paramTypes = executableParamTypes(executable, typeParams);
      var isVarArgs = executable.isVarArgs();
      if (argsMatchParamTypes(paramTypes, argTypes, isVarArgs)) {
        // Need to alter the parameter types to omit or repeat the vararg parm type. Then in code
        // generations the extra args need to be collected into an array before passing to the func
        // (I think? Maybe it'll just work)
        var returnType = javaTypeToType(executable.getAnnotatedReturnType().getType(), typeParams);
        var checkParamTypes = isVarArgs ? argTypes : paramTypes;
        var typedExprs = checkFuncArgTypes(funcCall.name(),
            funcCall.arguments(),
            checkParamTypes,
            funcCall.range());
        var resolvedReturnType = typeUnifier.resolve(returnType);
        var castType = returnType instanceof TypeVariable ? resolvedReturnType : null;

        var foreignFuncType = new ForeignFunctionType(
            foreignFuncHandle.methodHandleDesc(),
            castType);

        Varargs varargs = Varargs.none();
        if (isVarArgs) {
          var varargsIdx = paramTypes.size() - 1;
          var varargsType = (ArrayType) paramTypes.get(varargsIdx);
          varargs = Varargs.some(varargsType, varargsIdx);
        }
        return new TForeignFunctionCall(
            funcCall.classAlias(),
            funcCall.functionId().name(),
            foreignFuncType,
            typedExprs,
            varargs,
            resolvedReturnType,
            funcCall.range());
      }
    }

    var methodSignature = funcCall.name() + argTypes.stream()
        .map(Type::toPrettyString)
        .collect(joining("," + " ", "(", ")"));
    return addError(funcCall,
        new TypeLookupError("No method '%s' found on class '%s'".formatted(methodSignature,
            classType.toPrettyString()), funcCall.range()));
  }

  private void addError(RangedError error) {
    errors.add(error);
  }

  private TypedExpression addError(Expression expr, RangedError error) {
    addError(error);
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

  static final class TypeMismatchError extends AbstractRangedError implements RangedError {
    TypeMismatchError(String message, Range range, Throwable cause) {
      super(message, range, cause);
    }

    TypeMismatchError(String message, Range range) {
      this(message, range, null);
    }

    @Override
      public String toPrettyString(Source source) {
        return """
            %s
            %s
            """.formatted(getMessage(), source.highlight(range));
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
    public String typeNameStr() {
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
