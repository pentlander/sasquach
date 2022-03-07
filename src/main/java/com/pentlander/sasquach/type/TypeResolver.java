package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.expression.BinaryExpression.CompareExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.MathExpression;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Use;
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
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.ForeignFunctions;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.type.TypeUnifier.UnificationException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class TypeResolver implements TypeFetcher {
  private static final Type UNKNOWN_TYPE = new UnknownType();
  private final Map<Identifier, Type> idTypes = new HashMap<>();
  private final Map<Expression, Type> exprTypes = new HashMap<>();
  /**
   * Used to handle the resolution of function owner types.
   */
  private final Map<String, StructType> moduleTypes = new HashMap<>();
  private final Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes = new HashMap<>();
  private final List<RangedError> errors = new ArrayList<>();

  private final NameResolutionResult nameResolutionResult;
  private Node nodeResolving = null;

  public TypeResolver(NameResolutionResult nameResolutionResult) {
    this.nameResolutionResult = nameResolutionResult;
  }

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

  public List<RangedError> getErrors() {
    return List.copyOf(errors);
  }

  public record QualifiedFunctionId(Identifier classAlias, Identifier functionName) {}

  public RangedErrorList resolve(CompilationUnit compilationUnit) {
    try {
      compilationUnit.modules().forEach(this::resolveNodeType);
    } catch (RuntimeException e) {
      throw new TypeResolutionException("Failed at node: " + nodeResolving, e);
    }
    return new RangedErrorList(getErrors());
  }

  Type resolveNamedType(Type type) {
    return resolveNamedType(type, Map.of(), null);
  }

  Type resolveNamedType(Type type, Range range) {
    return resolveNamedType(type, Map.of(), range);
  }

  Type resolveNamedType(Type type, Map<String, Type> typeArgs, Range range) {
    if (type instanceof NamedType namedType) {
      // Get the alias or type parameter that defines the named type
      var resolvedType = nameResolutionResult.getNamedType(namedType)
          .orElseThrow(() -> new IllegalStateException("Unable to find named type: " + namedType));

      return switch (resolvedType) {
        // If it's a type parameter, check if there's a corresponding type argument. If not,
        // resolve to a type variable for later unification
        case TypeParameter typeParameter -> typeArgs.getOrDefault(typeParameter.typeName(),
            new TypeVariable(typeParameter.typeName()));
        case TypeAlias typeAlias -> {
          if (typeAlias.typeParameters().size() != namedType.typeArguments().size()) {
            yield addError(new TypeMismatchError("Number of type args does not match number of "
                + "type parameters for type '%s'".formatted(typeAlias.toPrettyString()),
                requireNonNullElse(range, typeAlias.range())));
          }
          var newTypeArgs = new HashMap<>(typeArgs);
          for (int i = 0; i < typeAlias.typeParameters().size(); i++) {
            var typeParam = typeAlias.typeParameters().get(i);
            var typeArg = resolveNamedType(namedType.typeArguments().get(i), range);
            newTypeArgs.put(typeParam.typeName(), typeArg);
          }

          yield switch (namedType) {
            case ModuleNamedType moduleNamedType -> new ResolvedModuleNamedType(moduleNamedType.moduleName(),
                moduleNamedType.name(), moduleNamedType.typeArguments(),
                resolveNamedType(typeAlias.type(), newTypeArgs, range));
            case LocalNamedType localNamedType -> new ResolvedLocalNamedType(localNamedType.typeName(), localNamedType.typeArguments(),
                resolveNamedType(typeAlias.type(), newTypeArgs, range));
          };
        }
        case ForeignClass foreignClass -> new ClassType(foreignClass.clazz());
      };
    }

    if (type instanceof ParameterizedType parameterizedType) {
      return switch (parameterizedType) {
        case StructType structType -> new StructType(structType.fieldTypes().entrySet().stream()
            .collect(toMap(Entry::getKey, e -> resolveNamedType(e.getValue(), typeArgs, range))));
        case FunctionType funcType -> new FunctionType(
            resolveNamedTypes(funcType.parameterTypes(), typeArgs, range),
            resolveNamedType(funcType.returnType(), typeArgs, range));
        case TypeVariable typeVariable -> typeArgs.getOrDefault(
            typeVariable.typeName(),
            typeVariable);
        case ResolvedModuleNamedType namedType -> new ResolvedModuleNamedType(namedType.moduleName(),
            namedType.name(),
            resolveNamedTypes(namedType.typeArgs(), typeArgs, range),
            resolveNamedType(namedType.type(), typeArgs, range));
        case ResolvedLocalNamedType namedType -> new ResolvedLocalNamedType(namedType.name(),
            resolveNamedTypes(namedType.typeArgs(), typeArgs, range),
            resolveNamedType(namedType.type(), typeArgs, range));
      };
    }

    return type;
  }

  private List<Type> resolveNamedTypes(List<Type> types, Map<String, Type> typeParams,
      Range range) {
    return types.stream().map(t -> resolveNamedType(t, typeParams, range)).toList();
  }

  FunctionType resolveFuncSignatureType(Identifier funcId, FunctionSignature funcSignature) {
    var type = getIdType(funcId).map(FunctionType.class::cast);
    if (type.isPresent()) {
      return type.get();
    }
    var funcType = resolveFuncSignatureType(funcId, funcSignature);
    putIdType(funcId, funcType);
    return funcType;
  }

  FunctionType resolveFuncSignatureType(FunctionSignature funcSignature) {
    var typeParams = funcSignature.typeParameters();
    var paramTypes = funcSignature.parameters().stream().map(funcParam -> resolveNamedType(
        resolveNodeType(funcParam),
        funcParam.typeNode().range())).toList();

    var returnType = resolveNamedType(funcSignature.returnType(),
        funcSignature.returnTypeNode().range());
    return new FunctionType(paramTypes, typeParams, returnType);
  }


  FunctionType resolveFuncType(NamedFunction func) {
    var type = getIdType(func.id()).map(FunctionType.class::cast);
    return type.orElseGet(() -> resolveFuncType(func.function()));
  }

  FunctionType resolveFuncType(Function func) {
    var funcType = resolveFuncSignatureType(func.functionSignature());
    var returnType = funcType.returnType();
    try {
      var resolvedReturnType = resolveExprType(func.expression());
      if (!returnType.isAssignableFrom(resolvedReturnType)) {
        addError(new TypeMismatchError(
            "Type of function return expression '%s' does not match return type of function '%s'"
                .formatted(resolvedReturnType.toPrettyString(), returnType.toPrettyString()),
            func.expression().range()));
        return null;
      }
    } catch (UnknownTypeException ignored) {
      // Encountering an unknown type means that the type resolution can no longer proceed
      // for the current function. Since the return type is annotated, type resolution can
      // continue without knowing the type of the expression body. This will have to be
      // revisited when lambdas are implemented since they will not require type annotations.
    }
    return funcType;
  }

  Type resolveNodeType(Node node) {
    nodeResolving = node;
    Type type;
    switch (node) {
      case ModuleDeclaration moduleDecl -> {
        var struct = moduleDecl.struct();
        type = resolveExprType(struct);
        moduleTypes.put(moduleDecl.name(), (StructType) type);
      }
      case FunctionParameter funcParam -> {
        type = resolveNamedType(funcParam.type(), funcParam.typeNode().range());
        putIdType(funcParam.id(), type);
      }
      case VariableDeclaration varDecl -> {
        type = resolveExprType(varDecl.expression());
        putIdType(varDecl.id(), type);
      }
      case Use.Foreign useForeign -> {
        type = new ClassType(useForeign.qualifiedName());
        putIdType(useForeign.alias(), type);
      }
      case Use.Module useModule -> type =
          requireNonNull(moduleTypes.get(useModule.qualifiedName()),
          "Not found: " + useModule);
      case NamedFunction func -> {
        type = resolveFuncType(func);
        putIdType(func.id(), type);
      }
      case Expression expr -> type = resolveExprType(expr);
      case default -> throw new IllegalStateException(node.toString());
    }

    return requireNonNullElse(type, UNKNOWN_TYPE);
  }

  private static Type builtinOrClassType(Class<?> clazz) {
    return Arrays.stream(BuiltinType.values()).filter(type -> type.typeClass().equals(clazz))
        .findFirst().map(Type.class::cast).orElseGet(() -> new ClassType(clazz));
  }

  public Type resolveExprType(Expression expr) {
    nodeResolving = expr;
    Type type = exprTypes.get(expr);
      if (type != null) {
          return type;
      }

    type = switch (expr) {
      case Value value ->  value.type();
      case VariableDeclaration varDecl -> {
        putIdType(varDecl.id(), resolveExprType(varDecl.expression()));
        yield BuiltinType.VOID;
      }
      case VarReference varRef -> switch (nameResolutionResult.getVarReference(varRef)) {
        case Local local -> getIdType(local.localVariable()
            .id()).orElseThrow(() -> new IllegalStateException("Unable to find local: " + local));
        case Module module -> resolveExprType(module.moduleDeclaration().struct());
      };
      case BinaryExpression binExpr -> {
        var leftType = resolveExprType(binExpr.left());
        var rightType = resolveExprType(binExpr.right());
        if (!leftType.equals(rightType)) {
          yield addError(new TypeMismatchError(("Type '%s' of left side does not match type "
              + "'%s' of right side").formatted(leftType.typeName(),
              rightType.typeName()),
              binExpr.range()));
        }
        yield switch (binExpr) {
          case CompareExpression b -> BuiltinType.BOOLEAN;
          case MathExpression b -> leftType;
        };
      }
      case ArrayValue arrayVal -> {
        // TODO: Asert expression types are equal to element type
        arrayVal.expressions().forEach(this::resolveExprType);
        yield new ArrayType(arrayVal.elementType());
      }
      case Block block -> {
        block.expressions().forEach(this::resolveExprType);
        yield resolveExprType(block.returnExpression());
      }
      case FieldAccess fieldAccess -> resolveFieldAccess(fieldAccess);
      case ForeignFieldAccess foreignFieldAccess -> resolveForeignFieldAccess(foreignFieldAccess);
      case ForeignFunctionCall foreignFuncCall -> resolveForeignFunctionCall(
          foreignFuncCall);
      case FunctionCall funcCall -> resolveFunctionCall(funcCall);
      case IfExpression ifExpr -> resolveIfExpression(ifExpr);
      case PrintStatement printStatement -> {
        resolveExprType(printStatement.expression());
        yield BuiltinType.VOID;
      }
      case Struct struct -> {
        var fieldTypes = new HashMap<String, Type>();
        struct.functions().forEach(func -> fieldTypes.put(func.name(), resolveNodeType(func)));
        struct.fields()
            .forEach(field -> fieldTypes.put(field.name(), resolveExprType(field)));
        yield struct.name().map(name -> new StructType(name, fieldTypes))
            .orElseGet(() -> new StructType(fieldTypes));
      }
      case Field field -> resolveExprType(field.value());
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        yield switch (recurPoint) {
          case Function func -> {
            var funcType = resolveFuncSignatureType(func.functionSignature());
            yield resolveParams("recur",
                recur.arguments(),
                recur.range(),
                funcType.parameterTypes(),
                funcType.returnType());
          }
          case Loop loop -> resolveParams("recur", recur.arguments(), recur.range(),
              loop.varDeclarations().stream()
                  .map(variableDeclaration -> {
                    resolveExprType(variableDeclaration);
                    return resolveExprType(variableDeclaration.expression());
                  }).toList(), new TypeVariable(recur.range().toString()));
        };
      }
      case Loop loop -> {
        loop.varDeclarations().forEach(this::resolveExprType);
        yield resolveExprType(loop.expression());
      }
      case Function func -> resolveFuncType(func);
    };

    putExprType(expr, requireNonNull(type, () -> "Null expression type for: %s".formatted(expr)));
    return type;
  }

  private Type resolveIfExpression(IfExpression ifExpr) {
    var condType = resolveExprType(ifExpr.condition());
    if (!(condType instanceof BuiltinType builtinType) || builtinType != BuiltinType.BOOLEAN) {
      return addError(new TypeMismatchError(("Expected type '%s' for if condition, but found type '%s'").formatted(
          BuiltinType.BOOLEAN.typeClass(),
          condType.typeName()), ifExpr.condition().range()));
    }

    var trueType = resolveExprType(ifExpr.trueExpression());
    if (ifExpr.falseExpression() != null) {
      var falseType = resolveExprType(ifExpr.falseExpression());
      var typeUnifier = new TypeUnifier();
      // Must unify in both directions since either the true or false statement types could be
      // unknown
      trueType = typeUnifier.unify(trueType, falseType);
      falseType = typeUnifier.unify(falseType, trueType);
      if (!trueType.isAssignableFrom(falseType)) {
        return addError(new TypeMismatchError(("Type of else expression '%s' must match type of "
            + "if expression '%s'").formatted(trueType.toPrettyString(),
            falseType.toPrettyString()), ifExpr.falseExpression().range()));
      }
      return trueType;
    }
    return BuiltinType.VOID;
  }

  private Type resolveFieldAccess(FieldAccess fieldAccess) {
    var exprType = resolveNamedType(resolveExprType(fieldAccess.expr()));
    var structType = TypeUtils.asStructType(exprType);

    if (structType.isPresent()) {
      var fieldType = structType.get().fieldType(fieldAccess.fieldName());
      if (fieldType != null) {
        return fieldType;
      } else {
        return addError(new TypeMismatchError("Type '%s' does not contain field '%s'".formatted(
            structType.get().typeName(),
            fieldAccess.fieldName()), fieldAccess.range()));
      }
    }

    return addError(new TypeMismatchError(
        "Can only access fields on struct types, found type '%s'".formatted(exprType.toPrettyString()),
        fieldAccess.range()));
  }

  private Type resolveParams(String name, List<Expression> args, Range range, List<Type> parameterTypes,
      Type returnType) {
    var argTypes = args.stream().map(this::resolveExprType).toList();
    var paramTypes = parameterTypes.stream().map(this::resolveNamedType).toList();
    // Handle mismatch between arg count and parameter count
    if (argTypes.size() != paramTypes.size()) {
      return addError(new TypeMismatchError("Function '%s' expects %s arguments but found %s"
          .formatted(name, paramTypes.size(), args.size()),
          range));
    }

    var typeUnifier = new TypeUnifier();
    // Handle mismatch between arg types and parameter types
    for (int i = 0; i < argTypes.size(); i++) {
      var argType = argTypes.get(i);
      var paramType = paramTypes.get(i);

      var oldParam = paramType;
      try {
        paramType = typeUnifier.unify(paramType, argType);
      } catch (UnificationException e) {
        return addError(new TypeMismatchError(
            "Type '%s' in '%s' should be '%s', but found '%s'".formatted(
                e.destType().toPrettyString(),
                oldParam.toPrettyString(),
                e.resolvedDestType().map(Type::toPrettyString).orElse("none"),
                e.sourceType().toPrettyString()),
            args.get(i).range()));
      }
      if (!paramType.isAssignableFrom(argType)) {
        return addError(new TypeMismatchError(
            "Argument type '%s' does not match parameter type '%s'"
                .formatted(argType.toPrettyString(), paramType.toPrettyString()),
            args.get(i).range()));
      }
    }

    return typeUnifier.resolve(resolveNamedType(returnType));
  }

  private Type resolveFunctionCall(FunctionCall funcCall) {
    FunctionType funcType = null;
    switch (funcCall) {
      case LocalFunctionCall localFuncCall -> {
        var callTarget = nameResolutionResult.getLocalFunction(localFuncCall);
        funcType = switch (callTarget) {
          case QualifiedFunction qualFunc -> resolveFuncType(qualFunc.function());
          case LocalVariable localVar -> TypeUtils.asFunctionType(resolveNodeType(localVar)).get();
        };
        if (funcType == null) return UNKNOWN_TYPE;
      }
      case MemberFunctionCall structFuncCall -> {
        var exprType = resolveExprType(structFuncCall.structExpression());
        var structType = TypeUtils.asStructType(exprType);

        if (structType.isPresent()) {
          var funcName = structFuncCall.functionId().name();
          var fieldType = structType.get().fieldType(funcName);
          if (fieldType instanceof FunctionType fieldFuncType) {
            funcType = fieldFuncType;
          } else if (fieldType == null) {
            return addError(new TypeMismatchError("Struct of type '%s' has no field named '%s'".formatted(
                structType.get().toPrettyString(),
                funcName), structFuncCall.range()));
          } else {
            return addError(new TypeMismatchError("Field '%s' of type '%s' is not a function".formatted(
                funcName,
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

    return resolveParams(
        funcCall.name(),
        funcCall.arguments(),
        funcCall.range(),
        funcType.parameterTypes(),
        funcType.returnType());
  }

  private Type resolveForeignFieldAccess(ForeignFieldAccess fieldAccess) {
    var field = nameResolutionResult.getForeignField(fieldAccess);
    var classType = new ClassType(field.getDeclaringClass());
    var accessKind =
        Modifier.isStatic(field.getModifiers()) ? FieldAccessKind.STATIC : FieldAccessKind.INSTANCE;
    return new ForeignFieldType(builtinOrClassType(field.getType()), classType, accessKind);
  }

  private boolean argsMatchParams(List<Class<?>> params, List<Class<?>> args) {
    for (int i = 0; i < args.size(); i++) {
      if (!params.get(i).isAssignableFrom(args.get(i))) {
        return false;
      }
    }
    return true;
  }

  private Type resolveForeignFunctionCall(ForeignFunctionCall funcCall) {
    ForeignFunctions funcCandidates = nameResolutionResult.getForeignFunction(funcCall);
    List<Class<?>> argClasses = funcCall.arguments().stream()
        .map(arg -> resolveExprType(arg).typeClass()).collect(toList());
    var argTypes = argClasses.stream().map(Class::getName).collect(joining(", ", "(", ")"));
    var classType = new ClassType(funcCandidates.ownerClass());

    for (var foreignFuncHandle : funcCandidates.functions()) {
      var methodHandle = foreignFuncHandle.methodHandle();
      var methodType = methodHandle.type();
      if (argsMatchParams(methodHandle.type().parameterList(), argClasses)) {
        var returnType = methodType.returnType();
        methodType = switch (foreignFuncHandle.invocationKind()) {
          // Drop the "this" for the call since it's implied by the owner
          case VIRTUAL -> methodType.dropParameterTypes(0, 1);
          case SPECIAL -> methodType.changeReturnType(void.class);
          case STATIC -> methodType;
        };

        putForeignFuncType(funcCall, new ForeignFunctionType(methodType, classType,
            foreignFuncHandle.invocationKind()));
        return builtinOrClassType(returnType);
      }
    }

    return addError(new TypeLookupError(
        "No method '%s' found on class '%s' matching argument types '%s'"
            .formatted(funcCall.name(), classType.typeClass().getName(), argTypes),
        funcCall.range()));
  }

  private void putForeignFuncType(ForeignFunctionCall foreignFunctionCall,
      ForeignFunctionType foreignFunctionType) {
    foreignFuncTypes.put(new QualifiedFunctionId(foreignFunctionCall.classAlias(),
        foreignFunctionCall.functionId()), foreignFunctionType);
  }

  private Type addError(RangedError error) {
    errors.add(error);
    return UNKNOWN_TYPE;
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

  static final class UnknownType implements Type {
    @Override
    public String typeName() {
      throw new UnknownTypeException();
    }

    @Override
    public Class<?> typeClass() {
      throw new UnknownTypeException();
    }

    @Override
    public String descriptor() {
      throw new UnknownTypeException();
    }

    @Override
    public String internalName() {
      throw new UnknownTypeException();
    }
  }

  static class UnknownTypeException extends RuntimeException {}

  static class TypeResolutionException extends RuntimeException {
    public TypeResolutionException(String message, Exception cause) {
      super(message, cause);
    }
  }
}
