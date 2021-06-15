package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.joining;

class BytecodeGenerator implements Opcodes {
    public byte[] generateBytecode(CompilationUnit compilationUnit) throws Exception {
        ModuleDeclaration moduleDeclaration = compilationUnit.module();
        return new ClassGenerator().generate(moduleDeclaration).toByteArray();
    }

    static class ClassGenerator {
        private static final int CLASS_VERSION = V16;
        private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);

        public ClassWriter generate(ModuleDeclaration moduleDeclaration) {
            String name = moduleDeclaration.name();
            classWriter.visit(CLASS_VERSION, ACC_PUBLIC, name, null, "java/lang/Object", null);
            for (Function func : moduleDeclaration.functions()) {
                generateFunction(classWriter, func);
            }
            classWriter.visitEnd();
            return classWriter;
        }

        private void generateFunction(ClassWriter classWriter, Function function) {
            String descriptor = DescriptorFactory.getMethodDescriptor(function);
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC + ACC_STATIC, function.name(), descriptor, null, null);
            methodVisitor.visitCode();
            var exprGenerator = new ExpressionGenerator(methodVisitor);
            var expressions = function.expressions();
            expressions.forEach(expr -> exprGenerator.generate(expr, function.scope()));
            var returnExpr = function.returnExpression();
            if (returnExpr != null) {
                exprGenerator.generate(returnExpr, function.scope());
                Type type = returnExpr.type();
                if (type instanceof BuiltinType builtinType) {
                    int opcode = switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> IRETURN;
                        case LONG -> LRETURN;
                        case FLOAT -> FRETURN;
                        case DOUBLE -> DRETURN;
                        case STRING, STRING_ARR -> ARETURN;
                        case VOID -> RETURN;
                    };
                    methodVisitor.visitInsn(opcode);
                }
            } else {
                methodVisitor.visitInsn(RETURN);
            }
            methodVisitor.visitMaxs(-1, -1);
            methodVisitor.visitEnd();
        }
    }

    static class ExpressionGenerator {
        private final MethodVisitor methodVisitor;

        ExpressionGenerator(MethodVisitor methodVisitor) {
            this.methodVisitor = methodVisitor;
        }

        public void generate(Expression expression, Scope scope) {
            if (expression instanceof PrintStatement printStatement) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generate(printStatement.expression(), scope);
                String descriptor = "(%s)V".formatted(printStatement.expression().type().descriptor());
                StructType owner = new StructType("java.io.PrintStream");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
            } else if (expression instanceof  VariableDeclaration varDecl) {
                var varDeclExpr = varDecl.expression();
                int idx = scope.findIdentifierIdx(varDecl.name());
                generate(varDeclExpr, scope);
                var type = varDeclExpr.type();
                if (type instanceof BuiltinType builtinType) {
                    Integer opcode = switch (builtinType) {
                        case BOOLEAN, INT, BYTE, CHAR, SHORT -> ISTORE;
                        case LONG -> LSTORE;
                        case FLOAT -> FSTORE;
                        case DOUBLE -> DSTORE;
                        case STRING -> ASTORE;
                        case STRING_ARR -> AASTORE;
                        case VOID -> null;
                    };
                    if (opcode != null) {
                        methodVisitor.visitVarInsn(opcode, idx);
                    }
                }
            } else if (expression instanceof Identifier id) {
                int idx = scope.findIdentifierIdx(id.name());
                if (idx != -1 && id.type() instanceof BuiltinType builtinType) {
                    Integer opcode = switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> ILOAD;
                        case LONG -> LLOAD;
                        case FLOAT -> FLOAD;
                        case DOUBLE -> DLOAD;
                        case STRING -> ALOAD;
                        case STRING_ARR -> AALOAD;
                        case VOID -> null;
                    };
                    if (opcode != null) {
                        methodVisitor.visitVarInsn(opcode, idx);
                    } else {
                        methodVisitor.visitInsn(ACONST_NULL);
                    }
                }
            } else if (expression instanceof Value value) {
                var type = value.type();
                var literal = value.value();
                if (type instanceof BuiltinType builtinType) {
                    switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> {
                            int intValue = Integer.parseInt(literal);
                            methodVisitor.visitIntInsn(BIPUSH, intValue);
                        }
                        case LONG -> {
                            long longValue = Long.parseLong(literal);
                            methodVisitor.visitLdcInsn(longValue);
                        }
                        case FLOAT -> {
                            float floatValue = Float.parseFloat(literal);
                            methodVisitor.visitLdcInsn(floatValue);
                        }
                        case DOUBLE -> {
                            double doubleValue = Double.parseDouble(literal);
                            methodVisitor.visitLdcInsn(doubleValue);
                        }
                        case STRING -> methodVisitor.visitLdcInsn(literal.replace("\"", ""));
                        case STRING_ARR -> {}
                        case VOID -> methodVisitor.visitInsn(ACONST_NULL);
                    }
                }
            } else if (expression instanceof FunctionParameter funcParam) {
                if (funcParam.type() == BuiltinType.INT) {
                    System.out.println("Visited function param: " + funcParam);
                    methodVisitor.visitVarInsn(ILOAD, funcParam.index());
                }
            } else if (expression instanceof FunctionCall funcCall) {
                funcCall.arguments().forEach(arg -> generate(arg, scope));
                String descriptor = DescriptorFactory.getMethodDescriptor(funcCall.signature());
                Type owner = Objects.requireNonNullElseGet(funcCall.owner(), () -> new StructType(scope.getClassName()));
                methodVisitor.visitMethodInsn(INVOKESTATIC, owner.internalName(), funcCall.functionName(), descriptor, false);
            } else if (expression instanceof BinaryExpression binExpr) {
                generate(binExpr.left(), scope);
                generate(binExpr.right(), scope);
                if (expression instanceof BinaryExpression.MathExpression mathExpr) {
                    int opcode = switch (mathExpr.operator()) {
                        case PLUS -> IADD;
                        case MINUS -> ISUB;
                        case ASTERISK -> IMUL;
                        case DIVIDE -> IDIV;
                    };
                    methodVisitor.visitInsn(opcode);
                }
            } else if (expression instanceof IfExpression ifExpr) {
                generate(ifExpr.condition(), scope);
                var falseLabel = new Label();
                var endLabel = new Label();
                methodVisitor.visitJumpInsn(IFEQ, falseLabel);
                generateBlock(ifExpr.trueBlock(), scope);
                methodVisitor.visitJumpInsn(GOTO, endLabel);
                methodVisitor.visitLabel(falseLabel);
                generateBlock(ifExpr.falseBlock(), scope);
                methodVisitor.visitLabel(endLabel);
            }
        }

        private void generateBlock(Block block, Scope scope) {
            block.expressions().forEach(expr -> generate(expr, scope));
            generate(block.returnExpression(), scope);
        }
    }

    static class DescriptorFactory {
        private static final Map<BuiltinType, String> FIELD_DESCRIPTOR_MAPPING =
                Map.of(BuiltinType.VOID, "V", BuiltinType.INT, "I");

        public static String getMethodDescriptor(Function function) {
            return getMethodDescriptor(function.arguments(), function.type());
        }

        public static String getMethodDescriptor(FunctionSignature signature) {
            return getMethodDescriptor(signature.parameters(), signature.returnType());
        }

        public static String getMethodDescriptor(Collection<FunctionParameter> parameters, Type returnType) {
            String paramDescriptor = parameters.stream()
                    .map(param -> param.type().descriptor())
                    .collect(joining("", "(", ")"));
            return paramDescriptor + returnType.descriptor();
        }
    }
}
