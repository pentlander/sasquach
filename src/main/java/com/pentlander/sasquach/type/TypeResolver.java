package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static com.pentlander.sasquach.ast.ForeignFunctionCall.*;

public class TypeResolver {
    private final Map<Identifier, Type> idTypes = new HashMap<>();
    private final Map<Expression, Type> exprTypes = new HashMap<>();
    private final Map<QualifiedFunctionId, ForeignFunctionType> foreignFuncTypes = new HashMap<>();

    record QualifiedFunctionId(Identifier classAlias, Identifier functionName) {}

    void resolveNodeType(Node node) {
        if (node instanceof Function func) {
            if (putIdType(func.id(), func.returnType()) != null) return;

            func.functionSignature().parameters().forEach(this::resolveNodeType);
            resolveExprType(func.returnExpression(), func.scope());
            // TODO: Check func return type matches return expression
        } else if (node instanceof FunctionParameter funcParam) {
            putIdType(funcParam.id(), funcParam.type());
        } else if (node instanceof Use.Foreign useForeign) {
            putIdType(useForeign.alias(), new ClassType(useForeign.qualifiedName()));
        }
    }

    Type resolveExprType(Expression expr, Scope scope) {
        Type type = exprTypes.get(expr);
        if (type != null) return type;

        if (expr instanceof Value value) {
            type = value.type();
        } else if (expr instanceof VariableDeclaration varDecl) {
            putIdType(varDecl.id(), resolveExprType(varDecl.expression(), scope));
            type = BuiltinType.VOID;
        } else if (expr instanceof VarReference varRef) {
            type = scope.getIdentifier(varRef.name()).flatMap(this::getIdType).orElseThrow();
        } else if (expr instanceof Block block) {
            block.expressions().forEach(e -> resolveExprType(e, block.scope()));
            type = resolveExprType(block.returnExpression(), block.scope());
        } else if (expr instanceof FieldAccess fieldAccess) {
            if (resolveExprType(fieldAccess.expr(), scope) instanceof StructType structType) {
                var fieldType = structType.fieldTypes().get(fieldAccess.fieldName());
                if (fieldType != null) {
                    type = fieldType;
                } else {
                    throw new IllegalStateException("No field on struct type");
                }
            } else {
                throw new IllegalStateException("Must access field on struct");
            }
        } else if (expr instanceof ForeignFunctionCall foreignFuncCall) {
            var use = scope.findUse(foreignFuncCall.classAlias().name()).orElseThrow();
            resolveNodeType(use);
            var classType = getIdType(use.alias()).orElseThrow();
            List<Class<?>> argClasses =
                    foreignFuncCall.arguments().stream().map(arg -> resolveExprType(arg, scope).typeClass()).collect(Collectors.toList());
            var funcName = foreignFuncCall.functionName().name();
            MethodType methodType;
            FunctionCallType callType;

            try {
                if (funcName.equals("new")) {
                    callType = FunctionCallType.SPECIAL;
                    var handle = MethodHandles.lookup().findConstructor(classType.typeClass(),
                            MethodType.methodType(void.class, argClasses));
                    methodType = handle.type().changeReturnType(void.class);
                } else {
                    var method = classType.typeClass().getMethod(funcName,
                            argClasses.stream().skip(1).toList().toArray(new Class<?>[]{}));
                    methodType = MethodHandles.lookup().unreflect(method).type();
                    callType = Modifier.isStatic(method.getModifiers()) ? FunctionCallType.STATIC : FunctionCallType.VIRTUAL;
                    if (callType == FunctionCallType.VIRTUAL) {
                        // Drop the "this" for the call since it's implied by the owner
                        methodType = methodType.dropParameterTypes(0, 1);
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            putForeignFuncType(foreignFuncCall.classAlias(), foreignFuncCall.functionName(),
                    new ForeignFunctionType(methodType,
                            callType));
        } else if (expr instanceof FunctionCall funcCall) {
            var argTypes = funcCall.arguments().stream().map(arg -> resolveExprType(arg, scope)).toList();
            var func = scope.findFunction(funcCall.name());
            resolveNodeType(func);
            for (int i = 0; i < argTypes.size(); i++) {
                var argType = argTypes.get(i);
                var param = func.functionSignature().parameters().get(i);
                var paramType = getIdType(param.id()).orElseThrow();
                if (!argType.equals(paramType)) {
                    throw new IllegalStateException("Type mismatch");
                }
            }
            type = func.returnType();
        } else if (expr instanceof IfExpression ifExpr) {
            var condtype = resolveExprType(ifExpr.condition(), scope);
            if (!(condtype instanceof BuiltinType builtinType) || builtinType != BuiltinType.BOOLEAN) {
                throw new IllegalStateException("Condition type must be boolean");
            }
            var trueType = resolveExprType(ifExpr.trueExpression(), scope);
            if (ifExpr.falseExpression() != null) {
                var falseType = resolveExprType(ifExpr.falseExpression(), scope);
                if (!trueType.equals(falseType)) {
                    throw new IllegalStateException("Both branches of if expr must have same type");
                }
                type = trueType;
            } else {
                type = BuiltinType.VOID;
            }
        } else if (expr instanceof PrintStatement printStatement) {
            resolveExprType(printStatement.expression(), scope);
            type = BuiltinType.VOID;
        } else if (expr instanceof Struct struct) {
            var fieldTypes = struct.fields().stream().collect(Collectors.toMap(Struct.Field::name, Struct.Field::type));
            type = new StructType(fieldTypes);
        } else {
            throw new IllegalStateException("Unhandled expression: " + expr);
        }

        putExprType(expr, type);
        return type;
    }

    private void putForeignFuncType(Identifier classAlias, Identifier functionName,
                                    ForeignFunctionType foreignFunctionType) {
        foreignFuncTypes.put(new QualifiedFunctionId(classAlias, functionName), foreignFunctionType);
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
}
