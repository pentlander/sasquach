package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;

import java.lang.invoke.MethodHandle;
import java.util.*;

import com.pentlander.sasquach.type.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static com.pentlander.sasquach.BytecodeGenerator.ClassGenerator.constructorType;
import static com.pentlander.sasquach.ast.FunctionCallType.*;
import static com.pentlander.sasquach.ast.Struct.*;
import static java.util.stream.Collectors.joining;

class BytecodeGenerator implements Opcodes {
    private final TypeFetcher typeFetcher;

    BytecodeGenerator(TypeFetcher typeFetcher) {
        this.typeFetcher = typeFetcher;
    }

    record BytecodeResult(Map<String, byte[]> generatedBytecode) {
    }

    public BytecodeResult generateBytecode(CompilationUnit compilationUnit) throws Exception {
        ModuleDeclaration moduleDeclaration = compilationUnit.module();
        var classGen = new ClassGenerator(typeFetcher);
        var generatedBytecode = new HashMap<String, byte[]>();
        classGen.generate(moduleDeclaration).forEach((name, cw) -> generatedBytecode.put(name, cw.toByteArray()));

        return new BytecodeResult(generatedBytecode);
    }

    static class ClassGenerator {
        private static final String INSTANCE_FIELD = "INSTANCE";
        private static final int CLASS_VERSION = V16;
        private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
        private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
        private final TypeFetcher typeFetcher;

        ClassGenerator(TypeFetcher typeFetcher) {
            this.typeFetcher = typeFetcher;
        }

        public Map<String, ClassWriter> generate(ModuleDeclaration moduleDeclaration) {
            generateStruct(moduleDeclaration.struct());
            return generatedClasses;
        }

        private Type type(Expression expression) {
            return typeFetcher.getType(expression);
        }

        private Type type(Identifier identifier) {
            return typeFetcher.getType(identifier);
        }

        static FunctionType constructorType(String ownerName, List<Field> fields,
            TypeFetcher typeFetcher) {
            return new FunctionType(ownerName, fields.stream().map(typeFetcher::getType).toList(),
                BuiltinType.VOID);
        }

        private void generateStruct(Struct struct) {
            var structName = type(struct).typeName();
            generatedClasses.put(structName, classWriter);
            classWriter.visit(CLASS_VERSION, ACC_PUBLIC, structName, null, "java/lang/Object", null);
            List<Field> fields = struct.fields();
            // Generate fields
            for (var field : fields) {
                var fv = classWriter.visitField(ACC_PUBLIC + ACC_FINAL, field.name(), type(field).descriptor(),
                        null, null);
                fv.visitEnd();
            }

            // Generate constructor
            var initDescriptor = constructorType(structName, fields, typeFetcher).descriptor();
            var mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", initDescriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, new ClassType("java.lang.Object").internalName(), "<init>", "()V", false);

            // Set fields in constructor
            for (int i = 0; i < fields.size(); i++) {
                var field = fields.get(i);
                mv.visitVarInsn(ALOAD, 0);
                ExpressionGenerator.generateLoadVar(mv, type(field), i + 1);
                mv.visitFieldInsn(PUTFIELD, structName, field.name(), type(field).descriptor());
            }
            mv.visitInsn(RETURN);

            if (struct.structKind() == StructKind.MODULE) {
                var field = classWriter.visitField(
                        ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
                        INSTANCE_FIELD,
                        type(struct).descriptor(),
                        null,
                        null);
                field.visitEnd();

                var smv = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                smv.visitCode();
                new ExpressionGenerator(smv, typeFetcher).generateStructInit(struct, Scope.NULL_SCOPE);
                smv.visitFieldInsn(PUTSTATIC, structName, INSTANCE_FIELD, type(struct).descriptor());
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
            var funcType = type(function.id());
            var methodVisitor = classWriter.visitMethod(ACC_PUBLIC + ACC_STATIC, function.name(), funcType.descriptor(), null, null);
            methodVisitor.visitCode();
            var exprGenerator = new ExpressionGenerator(methodVisitor, typeFetcher);
            var returnExpr = function.returnExpression();
            if (returnExpr != null) {
                exprGenerator.generate(returnExpr, function.scope());
                Type type = type(returnExpr);
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
        private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
        private final MethodVisitor methodVisitor;
        private final TypeFetcher typeFetcher;

        ExpressionGenerator(MethodVisitor methodVisitor, TypeFetcher typeFetcher) {
            this.methodVisitor = methodVisitor;
            this.typeFetcher = typeFetcher;
        }

        private Type type(Expression expression) {
            return typeFetcher.getType(expression);
        }

        private Type type(Identifier identifier) {
            return typeFetcher.getType(identifier);
        }


        public Map<String, ClassWriter> getGeneratedClasses() {
            return Map.copyOf(generatedClasses);
        }

        public void generate(Expression expression, Scope scope) {
            if (expression instanceof PrintStatement printStatement) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                var expr = printStatement.expression();
                generate(expr, scope);
                String descriptor = "(%s)V".formatted(type(expr).descriptor());
                ClassType owner = new ClassType("java.io.PrintStream");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
            } else if (expression instanceof VariableDeclaration varDecl) {
                var varDeclExpr = varDecl.expression();
                int idx = scope.findIdentifierIdx(varDecl.name());
                generate(varDeclExpr, scope);
                var type = type(varDeclExpr);
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
            } else if (expression instanceof VarReference varReference) {
                int idx = scope.findIdentifierIdx(varReference.name());
                generateLoadVar(methodVisitor, type(varReference), idx);
            } else if (expression instanceof Value value) {
                var type = type(value);
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
            } else if (expression instanceof ArrayValue arrayValue) {
                // TODO: Support primitive arrays.
                methodVisitor.visitIntInsn(BIPUSH, arrayValue.expressions().size());
                methodVisitor.visitTypeInsn(ANEWARRAY, arrayValue.elementType().internalName());

                var expressions = arrayValue.expressions();
                for (int i = 0; i < expressions.size(); i++) {
                    var expr = expressions.get(i);
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitLdcInsn(i);
                    generate(expr, scope);
                    methodVisitor.visitInsn(AASTORE);
                }
            } else if (expression instanceof FunctionCall funcCall) {
                funcCall.arguments().forEach(arg -> generate(arg, scope));
                var funcType = (FunctionType) type(scope.findFunction(funcCall.name()).id());
                methodVisitor.visitMethodInsn(INVOKESTATIC, funcType.ownerName(), funcCall.name(),
                    funcType.descriptor(), false);
            } else if (expression instanceof BinaryExpression binExpr) {
                generate(binExpr.left(), scope);
                generate(binExpr.right(), scope);
                if (expression instanceof BinaryExpression.MathExpression mathExpr) {
                    int opcode = switch (mathExpr.operator()) {
                        case PLUS -> IADD;
                        case MINUS -> ISUB;
                        case TIMES -> IMUL;
                        case DIVIDE -> IDIV;
                    };
                    methodVisitor.visitInsn(opcode);
                } else if (expression instanceof BinaryExpression.CompareExpression cmpExpr) {
                    int opCode = switch (cmpExpr.compareOperator()) {
                        case EQ -> IF_ICMPEQ;
                        case NE -> IF_ICMPNE;
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
                var classGen = new ClassGenerator(typeFetcher);
                classGen.generateStruct(struct);
                generatedClasses.putAll(classGen.getGeneratedClasses());

                generateStructInit(struct, scope);
            } else if (expression instanceof FieldAccess fieldAccess) {
                if (type(fieldAccess.expr()) instanceof StructType structType) {
                    generate(fieldAccess.expr(), scope);
                    methodVisitor.visitFieldInsn(GETFIELD, structType.typeName(), fieldAccess.fieldName(), structType.fieldTypes().get(fieldAccess.fieldName()).descriptor());
                } else {
                    throw new IllegalStateException();
                }

            } else if (expression instanceof Block block) {
                generateBlock(block);
            } else if (expression instanceof ForeignFunctionCall foreignFuncCall) {
                var foreignFuncCallType = typeFetcher.getType(foreignFuncCall.classAlias(), foreignFuncCall.functionName());
                String owner = foreignFuncCallType.ownerType().internalName();
                if (foreignFuncCallType.callType() == SPECIAL) {
                    methodVisitor.visitTypeInsn(NEW, owner);
                    methodVisitor.visitInsn(DUP);
                }
                foreignFuncCall.arguments().forEach(arg -> generate(arg, scope));

                var foreignFuncType = typeFetcher.getType(foreignFuncCall.classAlias(), foreignFuncCall.functionName());
                int opCode = switch (foreignFuncCallType.callType()) {
                    case SPECIAL -> INVOKESPECIAL;
                    case STATIC -> INVOKESTATIC;
                    case VIRTUAL -> INVOKEVIRTUAL;
                };
                var funcName = foreignFuncCall.name().equals("new") ? "<init>" : foreignFuncCall.name();
                methodVisitor.visitMethodInsn(opCode, owner, funcName, foreignFuncType.descriptor(), false);
            } else {
                throw new IllegalStateException("Unrecognized expression: " + expression);
            }
        }

        private void generateStructInit(Struct struct, Scope scope) {
            var structName = type(struct).typeName();
            methodVisitor.visitTypeInsn(NEW, structName);
            methodVisitor.visitInsn(DUP);
            struct.fields().forEach(field -> generate(field.value(), scope));
            var initDescriptor =
                constructorType(structName, struct.fields(), typeFetcher).descriptor();
            methodVisitor.visitMethodInsn(INVOKESPECIAL, structName, "<init>", initDescriptor, false);
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
            if (block.returnExpression() != null) {
                generate(block.returnExpression(), block.scope());
            }
        }
    }
}
