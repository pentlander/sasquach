package com.pentlander.sasquach.type;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.RangedErrorList;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.TypeNode;
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
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.ForeignFunctions;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Local;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration.Module;
import com.pentlander.sasquach.name.NameResolutionResult;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  FunctionType resolveFuncType(Function func) {
    var type = getIdType(func.id()).map(FunctionType.class::cast);
      if (type.isPresent()) {
          return type.get();
      }

    var paramTypes = func.functionSignature().parameters().stream().map(this::resolveNodeType)
        .toList();
    try {
      var resolvedReturnType = resolveExprType(func.expression());
      if (!resolvedReturnType.equals(func.returnType())) {
        addError(new TypeMismatchError(
            "Type of function return expression '%s' does not match return type of function '%s'"
                .formatted(resolvedReturnType.typeName(), func.returnType().typeName()),
            func.expression().range()));
      }
    } catch (UnknownTypeException ignored) {
      // Encountering an unknown type means that the type resolution can no longer proceed
      // for the current function. Since the return type is annotated, type resolution can
      // continue without knowing the type of the expression body. This will have to be
      // revisited when lambdas are implemented since they will not require type annotations.
    }
    var typeParams = func.functionSignature().typeParameters().stream().map(TypeNode::type)
        .map(NamedType.class::cast).toList();
    var funcType = new FunctionType(paramTypes, typeParams, func.returnType());
    putIdType(func.id(), funcType);
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
      case Function func -> type = resolveFuncType(func);
      case FunctionParameter funcParam -> {
        type = funcParam.type();
        putIdType(funcParam.id(), type);
      }
      case Use.Foreign useForeign -> {
        type = new ClassType(useForeign.qualifiedName());
        putIdType(useForeign.alias(), type);
      }
      case Use.Module useModule -> type =
          requireNonNull(moduleTypes.get(useModule.qualifiedName()),
          "Not found: " + useModule);
      case null, default -> throw new IllegalStateException();
    }

    return type;
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
        case Local local -> getIdType(local.localVariable().id()).get();
        case Module module -> resolveExprType(module.moduleDeclaration().struct());
      };
      case BinaryExpression binExpr -> {
        var leftType = resolveExprType(binExpr.left());
        var rightType = resolveExprType(binExpr.right());
        if (!leftType.equals(rightType)) {
          yield addError(new TypeMismatchError(("Type '%s' of left side does not match type "
              + "'%s' ofight side").formatted(leftType.typeName(),
              rightType.typeName()),
              binExpr.range()));
        }
        yield switch (binExpr) {
          case BinaryExpression.CompareExpression b -> BuiltinType.BOOLEAN;
          case BinaryExpression.MathExpression b -> leftType;
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
      case IfExpression ifExpr -> {
        var condtype = resolveExprType(ifExpr.condition());
        if (!(condtype instanceof BuiltinType builtinType) || builtinType != BuiltinType.BOOLEAN) {
          yield addError(new TypeMismatchError(("Expected type '%s' for if condition, but found "
              + "type%s'").formatted(BuiltinType.BOOLEAN.typeClass(),
              condtype.typeName()),
              ifExpr.condition().range()));
        }
        var trueType = resolveExprType(ifExpr.trueExpression());
        if (ifExpr.falseExpression() != null) {
          var falseType = resolveExprType(ifExpr.falseExpression());
          if (!trueType.equals(falseType)) {
            yield addError(new TypeMismatchError(("Type of else expression '%s' must match type "
                + "of if expression '%s'").formatted(
                trueType.typeName(),
                falseType.typeName()),
                ifExpr.falseExpression().range()));
          }
          yield trueType;
        } else {
          yield BuiltinType.VOID;
        }
      }
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
      case Struct.Field field -> resolveExprType(field.value());
      case null, default -> throw new IllegalStateException("Unhandled expression: " + expr);
    };

    putExprType(expr, requireNonNull(type, () -> "Null expression type for: %s".formatted(expr)));
    return type;
  }

  private Type resolveFieldAccess(FieldAccess fieldAccess) {
    var exprType = resolveExprType(fieldAccess.expr());
    if (exprType instanceof StructType structType) {
      var fieldType = structType.fieldTypes().get(fieldAccess.fieldName());
      if (fieldType != null) {
        return fieldType;
      } else {
        return addError(new TypeMismatchError("Type '%s' does not contain field '%s'".formatted(
            structType.typeName(),
            fieldAccess.fieldName()), fieldAccess.range()));
      }
    }

    return addError(new TypeMismatchError(
        "Can only access fields on struct types, " + "found type '%s'".formatted(exprType),
        fieldAccess.range()));
  }

  private Type resolveFunctionCall(FunctionCall funcCall) {
    FunctionType funcType = null;
    switch (funcCall) {
      case LocalFunctionCall localFuncCall -> {
        var qualFunc = nameResolutionResult.getLocalFunction(localFuncCall);
        funcType = resolveFuncType(qualFunc.function());
      }
      case MemberFunctionCall structFuncCall -> {
        var exprType = resolveExprType(structFuncCall.structExpression());
        if (exprType instanceof StructType structType) {
          var funcName = structFuncCall.functionId().name();
          var fieldType = structType.fieldTypes().get(funcName);
          if (fieldType instanceof FunctionType fieldFuncType) {
            funcType = fieldFuncType;
          } else if (fieldType == null) {
            return addError(new TypeMismatchError("Struct of type '%s' has no field named '%s'".formatted(
                structType.toPrettyString(),
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

    var argTypes = funcCall.arguments().stream().map(this::resolveExprType).toList();
    var paramTypes = funcType.parameterTypes();
    // Handle mismatch between arg count and parameter count
    if (argTypes.size() != paramTypes.size()) {
      return addError(new TypeMismatchError("Function '%s' expects %s arguments but found %s"
          .formatted(funcCall.name(), paramTypes.size(), funcCall.arguments().size()),
          funcCall.range()));
    }

    var typeUnifier = new TypeUnifier();
    // Handle mismatch between arg types and parameter types
    for (int i = 0; i < argTypes.size(); i++) {
      var argType = argTypes.get(i);
      var paramType = paramTypes.get(i);

      paramType = typeUnifier.unify(paramType, argType);
      if (!paramType.isAssignableFrom(argType)) {
        return addError(new TypeMismatchError(
            "Argument type '%s' does not match parameter type '%s'"
                .formatted(argType.toPrettyString(), paramType.toPrettyString()),
            funcCall.arguments().get(i).range()));
      }
    }

    return typeUnifier.resolve(funcType.returnType());
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
    exprTypes.put(expr, type);
  }

  private Type putIdType(Identifier id, Type type) {
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
