package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;

import java.lang.invoke.MethodHandles;
import java.net.http.HttpClient;
import java.util.*;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static com.pentlander.sasquach.ast.ForeignFunctionCall.*;
import static com.pentlander.sasquach.ast.Struct.*;
import static java.util.stream.Collectors.joining;

class BytecodeGenerator implements Opcodes {
    record BytecodeResult(Map<String, byte[]> generatedBytecode) {
    }

    public BytecodeResult generateBytecode(CompilationUnit compilationUnit) throws Exception {
        ModuleDeclaration moduleDeclaration = compilationUnit.module();
        var classGen = new ClassGenerator();
        var generatedBytecode = new HashMap<String, byte[]>();
        classGen.generate(moduleDeclaration).forEach((name, cw) -> generatedBytecode.put(name, cw.toByteArray()));

        return new BytecodeResult(generatedBytecode);
    }

    static class ClassGenerator {
        private static final String INSTANCE_FIELD = "INSTANCE";
        private static final int CLASS_VERSION = V16;
        private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        private final Map<String, ClassWriter> generatedClasses = new HashMap<>();

        public Map<String, ClassWriter> generate(ModuleDeclaration moduleDeclaration) {
            generateStruct(moduleDeclaration.struct());
            return generatedClasses;
        }

        private void generateStruct(Struct struct) {
            var name = struct.name();
            generatedClasses.put(name, classWriter);
            classWriter.visit(CLASS_VERSION, ACC_PUBLIC, name, null, "java/lang/Object", null);
            List<Field> fields = struct.fields();
            // Generate fields
            for (var field : fields) {
                var fv = classWriter.visitField(ACC_PUBLIC + ACC_FINAL, field.name(), field.type().descriptor(), null, null);
                fv.visitEnd();
            }

            // Generate constructor
            var initDescriptor = DescriptorFactory.getMethodDescriptor(fields, BuiltinType.VOID);
            var mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", initDescriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, new ClassType("java.lang.Object").internalName(), "<init>", "()V", false);

            // Set fields in constructor
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                mv.visitVarInsn(ALOAD, 0);
                ExpressionGenerator.generateLoadVar(mv, field.type(), i + 1);
                mv.visitFieldInsn(PUTFIELD, name, field.name(), field.type().descriptor());
            }
            mv.visitInsn(RETURN);

            if (struct.structKind() == StructKind.MODULE) {
                var field = classWriter.visitField(
                        ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
                        INSTANCE_FIELD,
                        struct.type().descriptor(),
                        null,
                        null);
                field.visitEnd();

                var smv = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                smv.visitCode();
                new ExpressionGenerator(smv).generateStructInit(struct, Scope.NULL_SCOPE);
                smv.visitFieldInsn(PUTSTATIC, name, INSTANCE_FIELD, struct.type().descriptor());
                smv.visitInsn(RETURN);
                smv.visitMaxs(-1, -1);
                smv.visitEnd();
            }

            for (Function func : struct.functions()) {
                generateFunction(classWriter, func);
            }

            mv.visitMaxs(-1, -1);
            classWriter.visitEnd();
        }

        private void generateFunction(ClassWriter classWriter, Function function) {
            String descriptor = DescriptorFactory.getMethodDescriptor(function);
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC + ACC_STATIC, function.name(), descriptor, null, null);
            methodVisitor.visitCode();
            var exprGenerator = new ExpressionGenerator(methodVisitor);
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
                } else {
                    methodVisitor.visitInsn(ARETURN);
                }
            } else {
                methodVisitor.visitInsn(RETURN);
            }
            methodVisitor.visitMaxs(-1, -1);
            methodVisitor.visitEnd();
            generatedClasses.putAll(exprGenerator.getGeneratedClasses());
        }

        public Map<String, ClassWriter> getGeneratedClasses() {
            return generatedClasses;
        }
    }

    static class ExpressionGenerator {
        private final MethodVisitor methodVisitor;
        private final Map<String, ClassWriter> generatedClasses = new HashMap<>();

        ExpressionGenerator(MethodVisitor methodVisitor) {
            this.methodVisitor = methodVisitor;
        }

        public Map<String, ClassWriter> getGeneratedClasses() {
            return Map.copyOf(generatedClasses);
        }

        public void generate(Expression expression, Scope scope) {
            if (expression instanceof PrintStatement printStatement) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                var expr = printStatement.expression();
                generate(expr, scope);
                String descriptor = "(%s)V".formatted(expr.type().descriptor());
                ClassType owner = new ClassType("java.io.PrintStream");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
            } else if (expression instanceof VariableDeclaration varDecl) {
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
                } else {
                    methodVisitor.visitVarInsn(ASTORE, idx);
                }
            } else if (expression instanceof Identifier id) {
                int idx = scope.findIdentifierIdx(id.name());
                generateLoadVar(methodVisitor, id.type(), idx);
            } else if (expression instanceof Value value) {
                var type = value.type();
                var literal = value.value();
                if (type instanceof BuiltinType builtinType) {
                    switch (builtinType) {
                        case BOOLEAN -> {
                            boolean boolValue = Boolean.parseBoolean(literal);
                            methodVisitor.visitIntInsn(BIPUSH, boolValue ? 1 : 0);
                        }
                        case INT, CHAR, BYTE, SHORT -> {
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
                        case STRING_ARR -> {
                        }
                        case VOID -> methodVisitor.visitInsn(ACONST_NULL);
                    }
                }
            } else if (expression instanceof FunctionParameter funcParam) {
                if (funcParam.type() == BuiltinType.INT) {
                    methodVisitor.visitVarInsn(ILOAD, funcParam.index());
                }
            } else if (expression instanceof FunctionCall funcCall) {
                funcCall.arguments().forEach(arg -> generate(arg, scope));
                String descriptor = DescriptorFactory.getMethodDescriptor(funcCall.signature());
                Type owner = Objects.requireNonNullElseGet(funcCall.owner(), () -> {
                    System.out.println("Fallback type: " + funcCall);
                    return new ClassType(scope.getClassName());
                });
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
                } else if (expression instanceof BinaryExpression.CompareExpression cmpExpr) {
                    int opCode = switch (cmpExpr.compareOperator()) {
                        case EQ -> IF_ICMPEQ;
                        case NEQ -> IF_ICMPNE;
                        case GE -> IF_ICMPGE;
                        case LE -> IF_ICMPLE;
                        case LT -> IF_ICMPLT;
                        case GT -> IF_ICMPGT;
                    };

                    var trueLabel = new Label();
                    var endLabel = new Label();
                    methodVisitor.visitJumpInsn(opCode, trueLabel);
                    methodVisitor.visitInsn(ICONST_0);
                    methodVisitor.visitJumpInsn(GOTO, endLabel);
                    methodVisitor.visitLabel(trueLabel);
                    methodVisitor.visitInsn(ICONST_1);
                    methodVisitor.visitLabel(endLabel);
                }
            } else if (expression instanceof IfExpression ifExpr) {
                generate(ifExpr.condition(), scope);
                var falseLabel = new Label();
                var endLabel = new Label();
                methodVisitor.visitJumpInsn(IFEQ, falseLabel);
                generate(ifExpr.trueExpression(), scope);
                methodVisitor.visitJumpInsn(GOTO, endLabel);
                methodVisitor.visitLabel(falseLabel);
                generate(ifExpr.falseExpression(), scope);
                methodVisitor.visitLabel(endLabel);
            } else if (expression instanceof Struct struct) {
                var classGen = new ClassGenerator();
                classGen.generateStruct(struct);
                generatedClasses.putAll(classGen.getGeneratedClasses());

                generateStructInit(struct, scope);
            } else if (expression instanceof FieldAccess fieldAccess) {
                if (fieldAccess.expr().type() instanceof StructType structType) {
                    generate(fieldAccess.expr(), scope);
                    methodVisitor.visitFieldInsn(GETFIELD, structType.typeName(), fieldAccess.fieldName(), structType.fieldTypes().get(fieldAccess.fieldName()).descriptor());
                } else {
                    throw new IllegalStateException();
                }

            } else if (expression instanceof Block block) {
                generateBlock(block);
            } else if (expression instanceof ForeignFunctionCall foreignFuncCall) {
                if (foreignFuncCall.functionCallType() == FunctionCallType.SPECIAL) {
                    methodVisitor.visitTypeInsn(NEW, foreignFuncCall.owner());
                    methodVisitor.visitInsn(DUP);
                }
                foreignFuncCall.arguments().forEach(arg -> generate(arg, scope));
                if (foreignFuncCall.functionCallType() == FunctionCallType.VIRTUAL) {
                    methodVisitor.visitInsn(DUP);
                }

                String descriptor = foreignFuncCall.methodDescriptor();
                System.out.println(descriptor);
                String owner = foreignFuncCall.owner();
                int opCode = switch (foreignFuncCall.functionCallType()) {
                    case SPECIAL -> INVOKESPECIAL;
                    case STATIC -> INVOKESTATIC;
                    case VIRTUAL -> INVOKEVIRTUAL;
                };
                methodVisitor.visitMethodInsn(opCode, owner, foreignFuncCall.name(), descriptor, false);
            }
        }

        private void generateStructInit(Struct struct, Scope scope) {
            methodVisitor.visitTypeInsn(NEW, struct.name());
            methodVisitor.visitInsn(DUP);
            struct.fields().forEach(field -> generate(field.value(), scope));
            var initDescriptor = DescriptorFactory.getMethodDescriptor(struct.fields(), BuiltinType.VOID);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, struct.name(), "<init>", initDescriptor, false);
        }

        private static void generateLoadVar(MethodVisitor methodVisitor, Type type, int idx) {
            if (idx < 0) return;

            if (type instanceof BuiltinType builtinType) {
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
            } else {
                methodVisitor.visitVarInsn(ALOAD, idx);
            }
        }

        private void generateBlock(Block block) {
            block.expressions().forEach(expr -> generate(expr, block.scope()));
            generate(block.returnExpression(), block.scope());
        }
    }

    static class DescriptorFactory {
        public static String getMethodDescriptor(Function function) {
            return getMethodDescriptor(function.arguments(), function.type());
        }

        public static String getMethodDescriptor(FunctionSignature signature) {
            return getMethodDescriptor(signature.parameters(), signature.returnType());
        }

        public static String getMethodDescriptor(Collection<? extends Expression> parameters, Type returnType) {
            String paramDescriptor = parameters.stream()
                    .map(param -> param.type().descriptor())
                    .collect(joining("", "(", ")"));
            return paramDescriptor + returnType.descriptor();
        }
    }
}
