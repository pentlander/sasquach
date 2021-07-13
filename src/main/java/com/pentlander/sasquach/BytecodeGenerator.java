package com.pentlander.sasquach;

import static com.pentlander.sasquach.BytecodeGenerator.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.BytecodeGenerator.ClassGenerator.STRUCT_BASE_INTERNAL_NAME;
import static com.pentlander.sasquach.BytecodeGenerator.ClassGenerator.constructorType;
import static com.pentlander.sasquach.ast.InvocationKind.SPECIAL;
import static com.pentlander.sasquach.ast.expression.Struct.Field;
import static com.pentlander.sasquach.ast.expression.Struct.StructKind;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Scope;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.StructDispatch;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.ForeignFieldType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class BytecodeGenerator implements Opcodes {
  private final TypeFetcher typeFetcher;

  BytecodeGenerator(TypeFetcher typeFetcher) {
    this.typeFetcher = typeFetcher;
  }

  record BytecodeResult(Map<String, byte[]> generatedBytecode) {}

  public BytecodeResult generateBytecode(CompilationUnit compilationUnit) throws Exception {
    var generatedBytecode = new HashMap<String, byte[]>();
    for (var moduleDeclaration : compilationUnit.modules()) {
      var classGen = new ClassGenerator(typeFetcher);
      classGen.generate(moduleDeclaration)
          .forEach((name, cw) -> generatedBytecode.put(name, cw.toByteArray()));
    }

    return new BytecodeResult(generatedBytecode);
  }

  static class ClassGenerator {
    static final String STRUCT_BASE_INTERNAL_NAME = new ClassType(StructBase.class).internalName();
    static final String INSTANCE_FIELD = "INSTANCE";
    private static final int CLASS_VERSION = V16;
    private final ClassWriter classWriter = new ClassWriter(
        ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
    private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
    private final TypeFetcher typeFetcher;
    private Node contextNode;

    ClassGenerator(TypeFetcher typeFetcher) {
      this.typeFetcher = typeFetcher;
    }

    public Map<String, ClassWriter> generate(ModuleDeclaration moduleDeclaration) {
      try {
        generateStruct(moduleDeclaration.struct());
      } catch (RuntimeException e) {
        throw new CodeGenerationException(contextNode, e);
      }
      return generatedClasses;
    }

    private void addContextNode(Node node) {
      contextNode = node;
    }

    private Type type(Expression expression) {
      return typeFetcher.getType(expression);
    }

    private Type type(Identifier identifier) {
      return typeFetcher.getType(identifier);
    }

    static FunctionType constructorType(List<Field> fields, TypeFetcher typeFetcher) {
      return new FunctionType(fields.stream().map(typeFetcher::getType).toList(), BuiltinType.VOID);
    }

    private void generateStruct(Struct struct) {
      addContextNode(struct);
      var structName = type(struct).internalName();
      generatedClasses.put(structName, classWriter);
      classWriter.visit(CLASS_VERSION,
          ACC_PUBLIC + ACC_FINAL,
          structName,
          null,
          "java/lang/Object",
          new String[]{STRUCT_BASE_INTERNAL_NAME});
      List<Field> fields = struct.fields();
      // Generate fields
      for (var field : fields) {
        var fv = classWriter
            .visitField(ACC_PUBLIC + ACC_FINAL, field.name(), type(field).descriptor(), null, null);
        fv.visitEnd();
      }

      // Generate constructor
      var initDescriptor = constructorType(fields, typeFetcher).descriptor();
      var mv = classWriter.visitMethod(ACC_PUBLIC, "<init>", initDescriptor, null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL,
          new ClassType(Object.class).internalName(),
          "<init>",
          "()V",
          false);

      // Set fields in constructor
      for (int i = 0; i < fields.size(); i++) {
        var field = fields.get(i);
        mv.visitVarInsn(ALOAD, 0);
        ExpressionGenerator.generateLoadVar(mv, type(field), i + 1);
        mv.visitFieldInsn(PUTFIELD, structName, field.name(), type(field).descriptor());
      }
      mv.visitInsn(RETURN);

      // Add a static INSTANCE field of the struct to make a singleton class.
      if (struct.structKind() == StructKind.MODULE) {
        var field = classWriter.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
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
      addContextNode(function);
      var funcType = type(function.id());
      var methodVisitor = classWriter
          .visitMethod(ACC_PUBLIC + ACC_STATIC, function.name(), funcType.descriptor(), null, null);
      methodVisitor.visitCode();
      var exprGenerator = new ExpressionGenerator(methodVisitor, typeFetcher);
      var returnExpr = function.returnExpression();
      if (returnExpr != null) {
        exprGenerator.generateExpr(returnExpr, function.scope());
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
    String BOOTSTRAP_DESCRIPTOR = MethodType
        .methodType(CallSite.class, List.of(Lookup.class, String.class, MethodType.class))
        .descriptorString();
    private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
    private final MethodVisitor methodVisitor;
    private final TypeFetcher typeFetcher;
    private Node contextNode;

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

    private void addContextNode(Node node) {
      contextNode = node;
    }

    public Map<String, ClassWriter> getGeneratedClasses() {
      return Map.copyOf(generatedClasses);
    }

    public void generateExpr(Expression expression, Scope scope) {
      try {
        generate(expression, scope);
      } catch (RuntimeException e) {
        throw new CodeGenerationException(contextNode, e);
      }
    }

    private void generate(Expression expression, Scope scope) {
      addContextNode(expression);
      if (expression instanceof PrintStatement printStatement) {
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        var expr = printStatement.expression();
        generate(expr, scope);
        String descriptor = "(%s)V".formatted(type(expr).descriptor());
        ClassType owner = new ClassType("java.io.PrintStream");
        methodVisitor
            .visitMethodInsn(INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
      } else if (expression instanceof VariableDeclaration varDecl) {
        var varDeclExpr = varDecl.expression();
        int idx = scope.findLocalIdentifierIdx(varDecl.name()).orElseThrow();
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
        var name = varReference.name();
        scope.findLocalIdentifierIdx(name)
            .ifPresentOrElse(idx -> generateLoadVar(methodVisitor, type(varReference), idx),
                () -> scope.findUse(name).ifPresent(use -> methodVisitor.visitFieldInsn(GETSTATIC,
                    use.qualifiedName(),
                    INSTANCE_FIELD,
                    type(varReference).descriptor())));
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
      } else if (expression instanceof LocalFunctionCall funcCall) {
        funcCall.arguments().forEach(arg -> generate(arg, scope));
        var funcType = type(scope.findFunction(funcCall.name()).id());
        var ownerName = scope.getClassName().replace('.', '/');
        methodVisitor.visitMethodInsn(INVOKESTATIC,
            ownerName,
            funcCall.name(),
            funcType.descriptor(),
            false);
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
          var fieldDescriptor = structType.fieldTypes().get(fieldAccess.fieldName()).descriptor();
          var handle = new Handle(H_INVOKESTATIC,
              new ClassType(StructDispatch.class).internalName(),
              "bootstrapField",
              BOOTSTRAP_DESCRIPTOR,
              false);
          methodVisitor.visitInvokeDynamicInsn(fieldAccess.fieldName(),
              "(L%s;)%s".formatted(STRUCT_BASE_INTERNAL_NAME, fieldDescriptor),
              handle);
        } else {
          throw new IllegalStateException();
        }

      } else if (expression instanceof Block block) {
        generateBlock(block);
      } else if (expression instanceof ForeignFieldAccess fieldAccess) {
        var fieldType = (ForeignFieldType) typeFetcher.getType(fieldAccess);
        var opCode = switch (fieldType.accessKind()) {
          case INSTANCE -> GETFIELD;
          case STATIC -> GETSTATIC;
        };
        methodVisitor.visitFieldInsn(opCode,
            fieldType.ownerType().internalName(),
            fieldAccess.fieldName(),
            fieldType.descriptor());
      } else if (expression instanceof ForeignFunctionCall foreignFuncCall) {
        var foreignFuncCallType = typeFetcher
            .getType(foreignFuncCall.classAlias(), foreignFuncCall.functionId());
        String owner = foreignFuncCallType.ownerType().internalName();
        if (foreignFuncCallType.callType() == SPECIAL) {
          methodVisitor.visitTypeInsn(NEW, owner);
          methodVisitor.visitInsn(DUP);
        }
        foreignFuncCall.arguments().forEach(arg -> generate(arg, scope));

        var foreignFuncType = typeFetcher
            .getType(foreignFuncCall.classAlias(), foreignFuncCall.functionId());
        int opCode = switch (foreignFuncCallType.callType()) {
          case SPECIAL -> INVOKESPECIAL;
          case STATIC -> INVOKESTATIC;
          case VIRTUAL -> INVOKEVIRTUAL;
        };
        var funcName = foreignFuncCall.name().equals("new") ? "<init>" : foreignFuncCall.name();
        methodVisitor.visitMethodInsn(opCode, owner, funcName, foreignFuncType.descriptor(), false);
      } else if (expression instanceof MemberFunctionCall structFuncCall) {
        generate(structFuncCall.structExpression(), scope);
        structFuncCall.arguments().forEach(arg -> generate(arg, scope));
        var structType = (StructType) type(structFuncCall.structExpression());
        var funcType = (FunctionType) Objects
            .requireNonNull(structType.fieldTypes().get(structFuncCall.name()));
        var handle = new Handle(H_INVOKESTATIC,
            new ClassType(StructDispatch.class).internalName(),
            "bootstrapMethod",
            BOOTSTRAP_DESCRIPTOR,
            false);
        methodVisitor.visitInvokeDynamicInsn(structFuncCall.name(),
            funcType.descriptorWith(0, new ClassType(StructBase.class)),
            handle);
      } else {
        throw new IllegalStateException("Unrecognized expression: " + expression);
      }
    }

    private void generateStructInit(Struct struct, Scope scope) {
      var structName = type(struct).internalName();
      methodVisitor.visitTypeInsn(NEW, structName);
      methodVisitor.visitInsn(DUP);
      struct.fields().forEach(field -> generateExpr(field.value(), scope));
      var initDescriptor = constructorType(struct.fields(), typeFetcher).descriptor();
      methodVisitor.visitMethodInsn(INVOKESPECIAL, structName, "<init>", initDescriptor, false);
    }

    private static void generateLoadVar(MethodVisitor methodVisitor, Type type, int idx) {
        if (idx < 0) {
            return;
        }

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
    }
  }

  static class CodeGenerationException extends RuntimeException {
    CodeGenerationException(Node node, Exception cause) {
      super(node.toString(), cause);
    }
  }
}
