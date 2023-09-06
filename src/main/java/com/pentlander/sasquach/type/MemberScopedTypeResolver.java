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
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Singleton;
import com.pentlander.sasquach.name.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.type.ModuleScopedTypeResolver.ModuleTypeProvider;
import com.pentlander.sasquach.type.TypeUnifier.UnificationException;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
  private Node nodeResolving = null;
  private Node currentNode = null;

  public MemberScopedTypeResolver(Map<Identifier, Type> idTypes,
      NameResolutionResult nameResolutionResult, ModuleTypeProvider moduleTypeProvider) {
    this.idTypes.putAll(idTypes);
    this.nameResolutionResult = nameResolutionResult;
    this.namedTypeResolver = new NamedTypeResolver(nameResolutionResult);
    this.moduleTypeProvider = moduleTypeProvider;
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


  // Resolve a named type into the underlying type that is represented.
  Type resolveNamedType(Type type, Range range) {
    return namedTypeResolver.resolveNames(type, range);
  }

  Type resolveNamedType(Type type, Map<String, Type> typeMap, Range range) {
    return namedTypeResolver.resolveNames(type, typeMap, range);
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

  @SuppressWarnings("SwitchStatementWithTooFewBranches")
  public void check(Expression expr, Type type) {
    switch (expr) {
      // Check that the function matches the given type
      case Function func when type instanceof FunctionType funcType -> {
        var resolvedType = convertUniversals(funcType, func.range());
        for (int i = 0; i < func.parameters().size(); i++) {
          var param = func.parameters().get(i);
          var paramType = resolvedType.parameterTypes().get(i);
          putIdType(param.id(), paramType);
        }

        check(func.expression(), resolvedType.returnType());
        putExprType(expr, type);
      }
      default -> {
        switch (type) {
          case ResolvedNamedType resolvedType -> check(expr, resolvedType.type());
          default -> {
            var inferredType = infer(expr);
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

  public Type infer(Expression expr) {
    currentNode = expr;
    Type type = exprTypes.get(expr);
    if (type != null) {
      return type;
    }

    type = switch (expr) {
      case Value value -> value.type();
      case VariableDeclaration varDecl -> {
        putIdType(varDecl.id(), infer(varDecl.expression()));
        yield BuiltinType.VOID;
      }
      case VarReference varRef -> switch (nameResolutionResult.getVarReference(varRef)) {
        case Local local ->
            getIdType(local.localVariable().id()).orElseThrow(() -> new IllegalStateException(
                "Unable to find local: " + local));
        case Module module -> moduleTypeProvider.getModuleType(module.moduleDeclaration().id());
        case Singleton(var node) ->
          // Actually if we're seeing a bare (i.e. not qualified by a module) singleton, it
          // must've been defined in this module. So we don't actually need a qualified ID.

          // Need to initialize the sum type with a type variable in place of any type args
            convertUniversals((SumType) getType(node.aliasId()), node.range());
      };
      case BinaryExpression binExpr -> {
        var leftType = infer(binExpr.left());
        check(binExpr.right(), leftType);
        yield switch (binExpr) {
          case CompareExpression b -> BuiltinType.BOOLEAN;
          case MathExpression b -> leftType;
          case BooleanExpression b -> BuiltinType.BOOLEAN;
        };
      }
      case ArrayValue arrayVal -> {
        var elemType = arrayVal.elementType();
        arrayVal.expressions().forEach(arrayExpr -> check(expr, elemType));
        yield new ArrayType(elemType);
      }
      case Block block -> {
        var exprs = block.expressions();
        for (int i = 0; i < exprs.size() - 1; i++) {
          infer(exprs.get(i));
        }
        yield infer(block.returnExpression());
      }
      case FieldAccess fieldAccess -> resolveFieldAccess(fieldAccess);
      case ForeignFieldAccess foreignFieldAccess -> resolveForeignFieldAccess(foreignFieldAccess);
      case ForeignFunctionCall foreignFuncCall -> resolveForeignFunctionCall(foreignFuncCall);
      case FunctionCall funcCall -> resolveFunctionCall(funcCall);
      case IfExpression ifExpr -> resolveIfExpression(ifExpr);
      case PrintStatement printStatement -> {
        infer(printStatement.expression());
        yield BuiltinType.VOID;
      }
      case Struct struct -> {
        var fieldTypes = new HashMap<String, Type>();
        struct.functions().forEach(func -> {
          var funcType = infer(func.function());
          fieldTypes.put(func.name(), funcType);
          putIdType(func.id(), funcType);
        });
        struct.fields().forEach(field -> fieldTypes.put(field.name(), infer(field)));
        yield struct.name()
            .map(name -> new StructType(name, fieldTypes))
            .orElseGet(() -> new StructType(fieldTypes));
      }
      case Field field -> infer(field.value());
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        yield switch (recurPoint) {
          case Function func -> {
            var funcType = convertUniversals((FunctionType) infer(func), func.range());
            yield resolveParams("recur",
                recur.arguments(),
                recur.range(),
                funcType.parameterTypes(),
                funcType.returnType());
          }
          case Loop loop -> resolveParams("recur",
              recur.arguments(),
              recur.range(),
              loop.varDeclarations().stream().map(variableDeclaration -> {
                infer(variableDeclaration);
                return getIdType(variableDeclaration.id()).orElseThrow();
              }).toList(),
              new TypeVariable("Loop" + typeVarNum.getAndIncrement()));
        };
      }
      case Loop loop -> {
        loop.varDeclarations().forEach(this::infer);
        yield infer(loop.expression());
      }
      // Should only infer anonymous function, not ones defined at the module level
      case Function func -> {
        var lvl = typeVarNum.getAndIncrement();
        var paramTypes = func.parameters()
            .stream()
            .collect(toMap(FunctionParameter::name, param -> {
              var paramType = new TypeVariable(param.name() + lvl);
              putIdType(param.id(), paramType);
              return paramType;
            }));
        var returnType = infer(func.expression());
        yield new FunctionType(List.copyOf(paramTypes.values()), List.of(), returnType);
      }
      case ApplyOperator applyOperator -> infer(applyOperator.toFunctionCall());
      case Match match -> resolveMatch(match);
    };

    putExprType(expr, requireNonNull(type, () -> "Null expression type for: %s".formatted(expr)));
    return type;
  }

  private Type resolveIfExpression(IfExpression ifExpr) {
    check(ifExpr.condition(), BuiltinType.BOOLEAN);

    var trueType = infer(ifExpr.trueExpression());
    var falseExpr = ifExpr.falseExpression();
    if (falseExpr != null) {
      check(falseExpr, trueType);
      return trueType;
    }
    return BuiltinType.VOID;
  }

  private Type resolveMatch(Match match) {
    var exprType = infer(match.expr());
    if (exprType instanceof SumType sumType) {
      // All the variants of the sum type with any type parameters already filled in
      var exprVariantTypes = sumType.types().stream().collect(toMap(Type::typeName, identity()));
      var matchTypeNodes = nameResolutionResult.getMatchTypeNodes(match);
      List<Branch> branches = match.branches();
      Type returnType = null;
      for (int i = 0; i < branches.size(); i++) {
        var branch = branches.get(i);
        var typeNode = matchTypeNodes.get(i);
        var branchVariantTypeName = typeNode.typeName();
        var variantType = requireNonNull(exprVariantTypes.remove(branchVariantTypeName));
        switch (branch.pattern()) {
          case Pattern.Singleton singleton -> putIdType((Identifier) singleton.id(), variantType);
          case VariantTuple tuple -> {
            putIdType((Identifier) tuple.id(), variantType);
            var tupleType = (StructType) variantType;
            // The fields types are stored in a hashmap, but we sort them to bind the variables
            // in a // consistent order. This works because the fields are named by number,
            // e.g _0, _1, etc.
            var tupleFieldTypes = tupleType.sortedFieldTypes();
            for (int j = 0; j < tuple.bindings().size(); j++) {
              var binding = tuple.bindings().get(j);
              var fieldType = tupleFieldTypes.get(j);
              putIdType(binding.id(), fieldType);
            }
          }
          case Pattern.VariantStruct struct -> {
            var structType = (StructType) variantType;
            for (var binding : struct.bindings()) {
              var fieldType = structType.fieldType(binding.id().name());
              putIdType(binding.id(), fieldType);
            }
          }
        }

        if (i == 0) {
          returnType = infer(branch.expr());
        } else {
          check(branch.expr(), returnType);
        }
      }
      if (!exprVariantTypes.isEmpty()) {
        return addError(new MatchNotExhaustive("Match is not exhaustive", match.range()));
      }

      return returnType;
    } else {
      return addError(new TypeMismatchError("Type '%s' in match is not a sum type".formatted(
          exprType.toPrettyString()), match.expr().range()));
    }
  }

  private Type resolveFieldAccess(FieldAccess fieldAccess) {
    var exprType = infer(fieldAccess.expr());
    var structType = TypeUtils.asStructType(exprType);

    if (structType.isPresent()) {
      var fieldType = structType.get().fieldType(fieldAccess.fieldName());
      if (fieldType != null) {
        return fieldType;
      } else {
        return addError(new TypeMismatchError("Type '%s' does not contain field '%s'".formatted(structType.get().typeName(),
            fieldAccess.fieldName()), fieldAccess.range()));
      }
    }

    return addError(new TypeMismatchError(
        "Can only access fields on struct types, found type '%s'".formatted(exprType.toPrettyString()),
        fieldAccess.range()));
  }

  private Type resolveParams(String name, List<Expression> args, Range range, List<Type> paramTypes,
      Type returnType) {
    // Handle mismatch between arg count and parameter count
    if (args.size() != paramTypes.size()) {
      return addError(new TypeMismatchError("Function '%s' expects %s arguments but found %s".formatted(name,
          paramTypes.size(),
          args.size()), range));
    }

    // Handle mismatch between arg types and parameter types
    for (int i = 0; i < args.size(); i++) {
      var arg = args.get(i);
      var paramType = paramTypes.get(i);
      check(arg, paramType);
    }

    return typeUnifier.resolve(resolveNamedType(returnType, range));
  }

  private Type resolveFunctionCall(FunctionCall funcCall) {
    FunctionType funcType = null;
    switch (funcCall) {
      case LocalFunctionCall localFuncCall -> {
        var callTarget = nameResolutionResult.getLocalFunction(localFuncCall);
        var type = switch (callTarget) {
          case QualifiedFunction qualFunc -> qualFunc.function().functionSignature().type();
          case LocalVariable localVar -> getIdType(localVar.id()).orElseThrow();
          // TODO Need to store the ID -> variant type signature and check that they match here.
          // Possibly need to include the qualified name of the owning module
          case VariantStructConstructor struct -> {
            var structType = struct.type();
            // Ensure that the struct expr and fields have their types in the type map
            var t = infer(struct.struct());
            // Need to unify the type params of the sum type with the type params of the struct
            var sumType = getType(struct.sumAliasId());
            yield new FunctionType(structType.fieldTypes()
                .values()
                .stream()
                .sorted(Comparator.comparing(Type::typeName))
                .toList(), struct.typeParameters(), sumType);
          }
        };
        funcType = TypeUtils.asFunctionType(type).orElseThrow();
      }
      case MemberFunctionCall structFuncCall -> {
        var exprType = infer(structFuncCall.structExpression());
        var structType = TypeUtils.asStructType(exprType);

        if (structType.isPresent()) {
          var funcName = structFuncCall.functionId().name();
          // need to replace existential types with actual types here
          var fieldType = structType.get().fieldType(funcName);
          if (fieldType instanceof FunctionType fieldFuncType) {
            funcType = fieldFuncType;
          } else if (fieldType == null) {
            return addError(new TypeMismatchError("Struct of type '%s' has no field named '%s'".formatted(structType.get().toPrettyString(),
                funcName), structFuncCall.range()));
          } else {
            return addError(new TypeMismatchError("Field '%s' of type '%s' is not a function".formatted(funcName,
                fieldType.toPrettyString()), structFuncCall.functionId().range()));
          }
        } else {
          return addError(new TypeMismatchError(
              "Expected field access on type struct, found type '%s'".formatted(exprType.toPrettyString()),
              structFuncCall.structExpression().range()));
        }
      }
      case ForeignFunctionCall f -> throw new IllegalStateException(f.toString());
    }

    funcType = convertUniversals(funcType, funcCall.range());
    var p = resolveParams(funcCall.name(),
        funcCall.arguments(),
        funcCall.range(),
        funcType.parameterTypes(),
        funcType.returnType());
    return p;
  }

  private Type resolveForeignFieldAccess(ForeignFieldAccess fieldAccess) {
    var field = nameResolutionResult.getForeignField(fieldAccess);
    var classType = new ClassType(field.getDeclaringClass());
    var accessKind =
        Modifier.isStatic(field.getModifiers()) ? FieldAccessKind.STATIC : FieldAccessKind.INSTANCE;
    return new ForeignFieldType(builtinOrClassType(field.getType(), List.of()),
        classType,
        accessKind);
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

  private Type resolveForeignFunctionCall(ForeignFunctionCall funcCall) {
    var funcCandidates = nameResolutionResult.getForeignFunction(funcCall);
    var argTypes = funcCall.arguments().stream().map(this::infer).collect(toList());
    var classType = new ClassType(funcCandidates.ownerClass());

    for (var foreignFuncHandle : funcCandidates.functions()) {
      var executable = foreignFuncHandle.executable();
      var typeParams = executableTypeParams(executable);
      var paramTypes = executableParamTypes(executable, typeParams);
      if (argsMatchParamTypes(paramTypes, argTypes)) {
        var returnType = javaTypeToType(executable.getAnnotatedReturnType().getType(), typeParams);
        var resolvedReturnType = resolveParams(executable.getName(),
            funcCall.arguments(),
            funcCall.range(),
            paramTypes,
            returnType);
        var methodType = foreignFuncHandle.methodHandle().type();
        methodType = switch (foreignFuncHandle.invocationKind()) {
          // Drop the "this" for the call since it's implied by the owner
          case VIRTUAL, INTERFACE -> methodType.dropParameterTypes(0, 1);
          case SPECIAL -> methodType.changeReturnType(void.class);
          case STATIC -> methodType;
        };

        var castType = returnType instanceof TypeVariable ? resolvedReturnType : null;
        putForeignFuncType(funcCall,
            new ForeignFunctionType(methodType,
                classType,
                foreignFuncHandle.invocationKind(),
                castType));
        return resolvedReturnType;
      }
    }

    var methodSignature = funcCall.name() + argTypes.stream()
        .map(Type::toPrettyString)
        .collect(joining("," + " ", "(", ")"));
    return addError(new TypeLookupError("No method '%s' found on class '%s'".formatted(methodSignature,
        classType.typeClass().getName()), funcCall.range()));
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

  private Optional<Type> getIdType(Identifier id) {
    return Optional.ofNullable(idTypes.get(id));
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
    public Class<?> typeClass() {
      throw exception;
    }

    @Override
    public String descriptor() {
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
