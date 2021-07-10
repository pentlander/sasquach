package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;

import static java.util.Objects.*;
import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.joining;

public class TypeResolver implements TypeFetcher {
    private static final Type UNKNOWN_TYPE = new UnknownType();
    private final Map<Identifier, Type> idTypes = new HashMap<>();
    private final Map<Expression, Type> exprTypes = new HashMap<>();
    /** Used to handle the resolution of function owner types. */
    private final Map<String, StructType> moduleTypes = new HashMap<>();
    private final Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes = new HashMap<>();
    private final List<RangedError> errors = new ArrayList<>();
    private Node nodeResolving = null;

    @Override
    public Type getType(Expression expression) {
        return requireNonNull(exprTypes.get(expression), expression::toString);
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

    public record QualifiedFunctionId(Identifier classAlias, Identifier functionName) {
    }

    public List<RangedError> resolve(CompilationUnit compilationUnit) {
        try {
            compilationUnit.modules().forEach(this::resolveNodeType);
        } catch (RuntimeException e) {
            throw new TypeResolutionException("Failed at node: " + nodeResolving, e);
        }
        return getErrors();
    }

    FunctionType resolveFuncType(Function func) {
        var type = getIdType(func.id()).map(FunctionType.class::cast);
        if (type.isPresent()) return type.get();

        var paramTypes = func.functionSignature().parameters().stream().map(this::resolveNodeType).toList();
        try {
            var resolvedReturnType = resolveExprType(func.returnExpression(), func.scope());
            if (!resolvedReturnType.equals(func.returnType())) {
                addError(new TypeMismatchError(
                    "Type of function return expression '%s' does not match return type of function '%s'"
                        .formatted(resolvedReturnType.typeName(), func.returnType().typeName()),
                    func.returnExpression().range()));
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
        if (node instanceof ModuleDeclaration moduleDecl) {
            var struct = moduleDecl.struct();
            type = resolveExprType(struct, struct.scope());
            moduleTypes.put(moduleDecl.name(), (StructType) type);
        } else if (node instanceof Function func) {
            type = resolveFuncType(func);
        } else if (node instanceof FunctionParameter funcParam) {
            type = funcParam.type();
            putIdType(funcParam.id(), type);
        } else if (node instanceof Use.Foreign useForeign) {
            type = new ClassType(useForeign.qualifiedName());
            putIdType(useForeign.alias(), type);
        } else if (node instanceof Use.Module useModule) {
          type = requireNonNull(moduleTypes.get(useModule.qualifiedName().replace("/", ".")),
              "Not found: " + useModule);
        } else {
            throw new IllegalStateException();
        }

        return type;
    }

    private static Type builtinOrClassType(Class<?> clazz) {
        return Arrays.stream(BuiltinType.values())
            .filter(type -> type.typeClass().equals(clazz))
            .findFirst()
            .map(Type.class::cast)
            .orElseGet(() -> new ClassType(clazz));
    }

    public Type resolveExprType(Expression expr, Scope scope) {
        nodeResolving = expr;
        Type type = exprTypes.get(expr);
        if (type != null) return type;

        if (expr instanceof Value value) {
            type = value.type();
        } else if (expr instanceof VariableDeclaration varDecl) {
            putIdType(varDecl.id(), resolveExprType(varDecl.expression(), scope));
            type = BuiltinType.VOID;
        } else if (expr instanceof VarReference varRef) {
            var name = varRef.name();
            type = scope.getLocalIdentifier(name).flatMap(this::getIdType)
                .or(() -> scope.getNamedType(name).map(TypeNode::type))
                .or(() -> scope.findUse(name).map(this::resolveNodeType)).orElseThrow();
        } else if (expr instanceof BinaryExpression binExpr) {
            var leftType = resolveExprType(binExpr.left(), scope);
            var rightType = resolveExprType(binExpr.right(), scope);
            if (!leftType.equals(rightType)) {
                errors.add(new TypeMismatchError(
                    "Type '%s' of left side does not match type '%s' of right side"
                        .formatted(leftType.typeName(), rightType.typeName()), binExpr.range()));
            }
            if (binExpr instanceof BinaryExpression.CompareExpression) {
                type = BuiltinType.BOOLEAN;
            } else if (binExpr instanceof BinaryExpression.MathExpression) {
                type = leftType;
            } else {
                throw new IllegalStateException("Unknown binary expression: " + binExpr);
            }
        } else if (expr instanceof ArrayValue arrayVal) {
            // TODO: Asert expression types are equal to element type
            arrayVal.expressions().forEach(e -> resolveExprType(e, scope));
            type = new ArrayType(arrayVal.elementType());
        } else if (expr instanceof Block block) {
            block.expressions().forEach(e -> resolveExprType(e, block.scope()));
            type = resolveExprType(block.returnExpression(), block.scope());
        } else if (expr instanceof FieldAccess fieldAccess) {
            var exprType = resolveExprType(fieldAccess.expr(), scope);
            if (exprType instanceof StructType structType) {
                var fieldType = structType.fieldTypes().get(fieldAccess.fieldName());
                if (fieldType != null) {
                    type = fieldType;
                } else {
                    type = addError(new TypeMismatchError(
                        "Type '%s' does not contain field '%s'"
                            .formatted(structType.typeName(), fieldAccess.fieldName()),
                        fieldAccess.range()));
                }
            } else {
              type = addError(new TypeMismatchError("Can only access fields on struct types, "
                  + "found type '%s'".formatted(exprType), expr.range()));
            }
        } else if (expr instanceof ForeignFieldAccess foreignFieldAccess) {
            type = resolveForeignFieldAccess(scope, foreignFieldAccess);
        } else if (expr instanceof ForeignFunctionCall foreignFuncCall) {
            type = resolveForeignFunctionCall(scope, foreignFuncCall);
        } else if (expr instanceof FunctionCall funcCall) {
            type = resolveFunctionCall(scope, funcCall);
        } else if (expr instanceof IfExpression ifExpr) {
            var condtype = resolveExprType(ifExpr.condition(), scope);
            if (!(condtype instanceof BuiltinType builtinType) || builtinType != BuiltinType.BOOLEAN) {
                return addError(new TypeMismatchError(
                    "Expected type '%s' for if condition, but found type '%s'"
                        .formatted(BuiltinType.BOOLEAN.typeClass(), condtype.typeName()),
                    ifExpr.condition().range()));
            }
            var trueType = resolveExprType(ifExpr.trueExpression(), scope);
            if (ifExpr.falseExpression() != null) {
                var falseType = resolveExprType(ifExpr.falseExpression(), scope);
                if (!trueType.equals(falseType)) {
                    return addError(new TypeMismatchError(
                        "Type of else expression '%s' must match type of if expression '%s'"
                            .formatted(trueType.typeName(), falseType.typeName()),
                        ifExpr.falseExpression().range()));
                }
                type = trueType;
            } else {
                type = BuiltinType.VOID;
            }
        } else if (expr instanceof PrintStatement printStatement) {
            resolveExprType(printStatement.expression(), scope);
            type = BuiltinType.VOID;
        } else if (expr instanceof Struct struct) {
            var structScope = struct.scope();
            var fieldTypes = new HashMap<String, Type>();
            struct.functions().forEach(func -> fieldTypes.put(func.name(), resolveNodeType(func)));
            struct.fields().forEach(
                field -> fieldTypes.put(field.name(), resolveExprType(field, structScope)));
            type = struct.name().map(name -> new StructType(name, fieldTypes))
                .orElseGet(() -> {
                    var structType = new StructType(fieldTypes);
                    struct.scope().setMetadata(new Metadata(structType.typeName()));
                    return structType;
                });
        } else if (expr instanceof Struct.Field field) {
            type = resolveExprType(field.value(), scope);
        } else {
            throw new IllegalStateException("Unhandled expression: " + expr);
        }

        putExprType(expr, requireNonNull(type,
            () -> "Null expression type for: %s".formatted(expr)));
        return type;
    }

    private Type resolveFunctionCall(Scope scope, FunctionCall funcCall) {
        FunctionType funcType;
        if (funcCall instanceof LocalFunctionCall localFuncCall) {
            var func = scope.findFunction(localFuncCall.name());
            funcType = resolveFuncType(func);
        } else if (funcCall instanceof MemberFunctionCall structFuncCall) {
            var exprType = resolveExprType(structFuncCall.structExpression(), scope);
            if (exprType instanceof StructType structType) {
                var funcName = structFuncCall.functionId().name();
                var fieldType = structType.fieldTypes().get(funcName);
                if (fieldType instanceof FunctionType fieldFuncType) {
                    funcType = fieldFuncType;
                } else if (fieldType == null) {
                    return addError(new TypeMismatchError(
                        "Struct of type '%s' has no field named '%s'".formatted(structType.toPrettyString(), funcName),
                        structFuncCall.range()));
                } else {
                    return addError(new TypeMismatchError(
                        "Field '%s' of type '%s' is not a function".formatted(funcName, fieldType.toPrettyString()),
                        structFuncCall.functionId().range()));
                }
            } else {
                return addError(new TypeMismatchError(
                    "Expected field access on type struct, found type '%s'"
                        .formatted(exprType.toPrettyString()),
                    structFuncCall.structExpression().range()));
            }
        } else {
            throw new IllegalStateException(funcCall.toString());
        }

        var argTypes = funcCall.arguments().stream().map(arg -> resolveExprType(arg, scope)).toList();
        var paramTypes = funcType.parameterTypes();
        // Handle mismatch between arg count and parameter count
        if (argTypes.size() != paramTypes.size()) {
            return addError(new TypeMismatchError(
                "Function '%s' expects %s arguments but found %s"
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

    private Type resolveForeignFieldAccess(Scope scope, ForeignFieldAccess fieldAccess) {
        var classAlias = fieldAccess.classAlias();
        var use = scope.findUse(classAlias.name());
        if (use.isEmpty()) {
            return addError(new ImportMissingError("Missing import for class '%s'"
                .formatted(classAlias.name()), fieldAccess.range()));
        }

        var classType = resolveNodeType(use.get());
        try {
            var field = classType.typeClass().getField(fieldAccess.fieldName());
            var accessKind = Modifier.isStatic(field.getModifiers()) ? FieldAccessKind.STATIC
                : FieldAccessKind.INSTANCE;
            return new ForeignFieldType(builtinOrClassType(field.getType()), classType, accessKind);
        } catch (NoSuchFieldException e) {
            return addError(new TypeLookupError(
                "No field '%s' found on class '%s'"
                    .formatted(fieldAccess.fieldName(), classType.typeClass().getName()),
                fieldAccess.range()));
        }
    }

    private Type resolveForeignFunctionCall(Scope scope,
        ForeignFunctionCall funcCall) {
        var use = scope.findUse(funcCall.classAlias().name());
        if (use.isEmpty()) {
            return addError(new ImportMissingError("Missing import for class '%s'"
                .formatted(funcCall.classAlias().name()), funcCall.range()));
        }
        var classType = resolveNodeType(use.get());
        List<Class<?>> argClasses = funcCall.arguments().stream()
            .map(arg -> resolveExprType(arg, scope).typeClass())
            .collect(toList());
        var argTypes = argClasses.stream()
            .map(Class::getName)
            .collect(joining(", ", "(", ")"));
        var funcName = funcCall.functionId().name();

        try {
            if (funcName.equals("new")) {
                var callType = FunctionCallType.SPECIAL;
                var handle = MethodHandles.lookup().findConstructor(classType.typeClass(),
                    MethodType.methodType(void.class, argClasses));
                var methodType = handle.type().changeReturnType(void.class);
                putForeignFuncType(funcCall,
                    new ForeignFunctionType(methodType, classType, callType));
                return classType;
            } else {
                for (var method : classType.typeClass().getMethods()) {
                    if (method.getName().equals(funcName)) {
                        var methodType = MethodHandles.lookup().unreflect(method).type();
                        if (methodType.parameterCount() != argClasses.size()) continue;
                        boolean argsMatchParams = true;
                        for (int i = 0; i < methodType.parameterCount(); i++) {
                            argsMatchParams =
                                methodType.parameterType(i).isAssignableFrom(argClasses.get(i));
                            if (!argsMatchParams) break;
                        }
                        if (argsMatchParams) {
                            var callType = Modifier.isStatic(method.getModifiers())
                                ? FunctionCallType.STATIC : FunctionCallType.VIRTUAL;
                            if (callType == FunctionCallType.VIRTUAL) {
                                // Drop the "this" for the call since it's implied by the owner
                                methodType = methodType.dropParameterTypes(0, 1);
                            }
                            putForeignFuncType(funcCall, new ForeignFunctionType(methodType, classType,
                                callType));
                            return builtinOrClassType(methodType.returnType());
                        }
                    }
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            // Exception ignored, type error propagated below
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
        return "TypeResolver{" +
                "idTypes=" + idTypes +
                ", exprTypes=" + exprTypes +
                ", foreignFuncTypes=" + foreignFuncTypes +
                '}';
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

    record ImportMissingError(String message, Range range) implements RangedError {
        @Override
        public String toPrettyString(Source source) {
            return """
                    %s
                    %s
                    """.formatted(message, source.highlight(range));
        }
    }

    static class UnknownType implements Type {
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
