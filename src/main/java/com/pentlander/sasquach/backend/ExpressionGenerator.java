package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.ast.InvocationKind.SPECIAL;
import static com.pentlander.sasquach.backend.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.backend.ClassGenerator.STRUCT_BASE_INTERNAL_NAME;
import static com.pentlander.sasquach.backend.ClassGenerator.constructorType;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.StructDispatch;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.ForeignFieldType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.ModuleNamedType;
import com.pentlander.sasquach.type.LocalNamedType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
import com.pentlander.sasquach.type.TypeUtils;
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

class ExpressionGenerator {
  String BOOTSTRAP_DESCRIPTOR = MethodType.methodType(
      CallSite.class,
      List.of(Lookup.class, String.class, MethodType.class)).descriptorString();
  private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
  private final MethodVisitor methodVisitor;
  private final NameResolutionResult nameResolutionResult;
  private final TypeFetcher typeFetcher;
  private Node contextNode;

  ExpressionGenerator(MethodVisitor methodVisitor, NameResolutionResult nameResolutionResult,
      TypeFetcher typeFetcher) {
    this.methodVisitor = methodVisitor;
    this.nameResolutionResult = nameResolutionResult;
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

  public void generateExpr(Expression expression) {
    try {
      generate(expression);
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
  }

  void generate(Expression expression) {
    addContextNode(expression);
    switch (expression) {
      case PrintStatement printStatement -> {
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC,
            "java/lang/System",
            "out",
            "Ljava/io/PrintStream;");
        var expr = printStatement.expression();
        generate(expr);
        String descriptor = "(%s)V".formatted(type(expr).descriptor());
        ClassType owner = new ClassType("java.io.PrintStream");
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
            owner.internalName(),
            "println",
            descriptor,
            false);
      }
      case VariableDeclaration varDecl -> {
        var varDeclExpr = varDecl.expression();
        int idx = nameResolutionResult.getVarIndex(varDecl);
        generate(varDeclExpr);
        var type = type(varDeclExpr);
        if (type instanceof BuiltinType builtinType) {
          Integer opcode = switch (builtinType) {
            case BOOLEAN, INT, BYTE, CHAR, SHORT -> Opcodes.ISTORE;
            case LONG -> Opcodes.LSTORE;
            case FLOAT -> Opcodes.FSTORE;
            case DOUBLE -> Opcodes.DSTORE;
            case STRING -> Opcodes.ASTORE;
            case STRING_ARR -> Opcodes.AASTORE;
            case VOID -> null;
          };
          if (opcode != null) {
            methodVisitor.visitVarInsn(opcode, idx);
          }
        } else {
          methodVisitor.visitVarInsn(Opcodes.ASTORE, idx);
        }
      }
      case VarReference varReference -> {
        var refDecl = nameResolutionResult.getVarReference(varReference);
        switch (refDecl) {
          case ReferenceDeclaration.Local local -> generateLoadVar(methodVisitor,
              type(varReference),
              local.index());
          case ReferenceDeclaration.Module module -> methodVisitor.visitFieldInsn(Opcodes.GETSTATIC,
              module.moduleDeclaration().name(),
              INSTANCE_FIELD,
              type(varReference).descriptor());
        }
      }
      case Value value -> {
        var type = type(value);
        var literal = value.value();
        if (type instanceof BuiltinType builtinType) {
          switch (builtinType) {
            case BOOLEAN -> {
              boolean boolValue = Boolean.parseBoolean(literal);
              methodVisitor.visitIntInsn(Opcodes.BIPUSH, boolValue ? 1 : 0);
            }
            case INT, CHAR, BYTE, SHORT -> {
              int intValue = Integer.parseInt(literal);
              methodVisitor.visitIntInsn(Opcodes.BIPUSH, intValue);
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
            case VOID -> methodVisitor.visitInsn(Opcodes.ACONST_NULL);
          }
        }
      }
      case ArrayValue arrayValue -> {
        // TODO: Support primitive arrays.
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, arrayValue.expressions().size());
        methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, arrayValue.elementType().internalName());

        var expressions = arrayValue.expressions();
        for (int i = 0; i < expressions.size(); i++) {
          var expr = expressions.get(i);
          methodVisitor.visitInsn(Opcodes.DUP);
          methodVisitor.visitLdcInsn(i);
          generate(expr);
          methodVisitor.visitInsn(Opcodes.AASTORE);
        }
      }
      case LocalFunctionCall funcCall -> {
        funcCall.arguments().forEach(this::generate);
        var qualifiedFunc = nameResolutionResult.getLocalFunction(funcCall);
        var funcType = type(qualifiedFunc.function().id());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
            qualifiedFunc.ownerId().name(),
            funcCall.name(),
            funcType.descriptor(),
            false);
      }
      case BinaryExpression binExpr -> {
        generate(binExpr.left());
        generate(binExpr.right());
        switch (binExpr) {
          case BinaryExpression.MathExpression mathExpr -> {
            int opcode = switch (mathExpr.operator()) {
              case PLUS -> Opcodes.IADD;
              case MINUS -> Opcodes.ISUB;
              case TIMES -> Opcodes.IMUL;
              case DIVIDE -> Opcodes.IDIV;
            };
            methodVisitor.visitInsn(opcode);
          }
          case BinaryExpression.CompareExpression cmpExpr -> {
            int opCode = switch (cmpExpr.compareOperator()) {
              case EQ -> Opcodes.IF_ICMPEQ;
              case NE -> Opcodes.IF_ICMPNE;
              case GE -> Opcodes.IF_ICMPGE;
              case LE -> Opcodes.IF_ICMPLE;
              case LT -> Opcodes.IF_ICMPLT;
              case GT -> Opcodes.IF_ICMPGT;
            };
            var trueLabel = new Label();
            var endLabel = new Label();
            methodVisitor.visitJumpInsn(opCode, trueLabel);
            methodVisitor.visitInsn(Opcodes.ICONST_0);
            methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
            methodVisitor.visitLabel(trueLabel);
            methodVisitor.visitInsn(Opcodes.ICONST_1);
            methodVisitor.visitLabel(endLabel);
          }
        }
      }
      case IfExpression ifExpr -> {
        generate(ifExpr.condition());
        var falseLabel = new Label();
        var endLabel = new Label();
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseLabel);
        generate(ifExpr.trueExpression());
        methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
        methodVisitor.visitLabel(falseLabel);
        generate(ifExpr.falseExpression());
        methodVisitor.visitLabel(endLabel);
      }
      case Struct struct -> {
        var classGen = new ClassGenerator(nameResolutionResult, typeFetcher);
        classGen.generateStruct(struct);
        generatedClasses.putAll(classGen.getGeneratedClasses());

        generateStructInit(struct);
      }
      case FieldAccess fieldAccess -> {
        var fieldAccessType = TypeUtils.asStructType(type(fieldAccess.expr()));
        if (fieldAccessType.isPresent()) {
          generate(fieldAccess.expr());
          var fieldDescriptor =
              fieldAccessType.get().fieldTypes().get(fieldAccess.fieldName()).descriptor();
          var handle = new Handle(Opcodes.H_INVOKESTATIC,
              new ClassType(StructDispatch.class).internalName(),
              "bootstrapField",
              BOOTSTRAP_DESCRIPTOR,
              false);
          methodVisitor.visitInvokeDynamicInsn(fieldAccess.fieldName(),
              "(L%s;)%s".formatted(STRUCT_BASE_INTERNAL_NAME, fieldDescriptor),
              handle);
        } else {
          throw new IllegalStateException("Failed to generate field access of type %s".formatted(fieldAccessType));
        }
      }
      case Block block -> generateBlock(block);
      case ForeignFieldAccess fieldAccess -> {
        var fieldType = (ForeignFieldType) typeFetcher.getType(fieldAccess);
        var opCode = switch (fieldType.accessKind()) {
          case INSTANCE -> Opcodes.GETFIELD;
          case STATIC -> Opcodes.GETSTATIC;
        };
        methodVisitor.visitFieldInsn(opCode,
            fieldType.ownerType().internalName(),
            fieldAccess.fieldName(),
            fieldType.descriptor());
      }
      case ForeignFunctionCall foreignFuncCall -> {
        var foreignFuncCallType = typeFetcher.getType(foreignFuncCall.classAlias(),
            foreignFuncCall.functionId());
        String owner = foreignFuncCallType.ownerType().internalName();
        if (foreignFuncCallType.callType() == SPECIAL) {
          methodVisitor.visitTypeInsn(Opcodes.NEW, owner);
          methodVisitor.visitInsn(Opcodes.DUP);
        }
        foreignFuncCall.arguments().forEach(this::generate);

        var foreignFuncType = typeFetcher.getType(foreignFuncCall.classAlias(),
            foreignFuncCall.functionId());
        int opCode = switch (foreignFuncCallType.callType()) {
          case SPECIAL -> Opcodes.INVOKESPECIAL;
          case STATIC -> Opcodes.INVOKESTATIC;
          case VIRTUAL -> Opcodes.INVOKEVIRTUAL;
        };
        var funcName = foreignFuncCall.name().equals("new") ? "<init>" : foreignFuncCall.name();
        methodVisitor.visitMethodInsn(opCode, owner, funcName, foreignFuncType.descriptor(), false);
      }
      case MemberFunctionCall structFuncCall -> {
        generate(structFuncCall.structExpression());
        structFuncCall.arguments().forEach(this::generate);
        var structType = (StructType) type(structFuncCall.structExpression());
        var funcType = (FunctionType) Objects.requireNonNull(structType.fieldTypes()
            .get(structFuncCall.name()));
        var handle = new Handle(Opcodes.H_INVOKESTATIC,
            new ClassType(StructDispatch.class).internalName(),
            "bootstrapMethod",
            BOOTSTRAP_DESCRIPTOR,
            false);
        methodVisitor.visitInvokeDynamicInsn(structFuncCall.name(),
            funcType.descriptorWith(0, new ClassType(StructBase.class)),
            handle);
      }
      case default -> throw new IllegalStateException("Unrecognized expression: " + expression);
    }
  }

  void generateStructInit(Struct struct) {
    var structName = type(struct).internalName();
    methodVisitor.visitTypeInsn(Opcodes.NEW, structName);
    methodVisitor.visitInsn(Opcodes.DUP);
    struct.fields().forEach(field -> generateExpr(field.value()));
    var initDescriptor = constructorType(struct.fields(), typeFetcher).descriptor();
    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        structName,
        "<init>",
        initDescriptor,
        false);
  }

  static void generateLoadVar(MethodVisitor methodVisitor, Type type, int idx) {
    if (idx < 0) {
      return;
    }

    if (type instanceof BuiltinType builtinType) {
      Integer opcode = switch (builtinType) {
        case BOOLEAN, INT, CHAR, BYTE, SHORT -> Opcodes.ILOAD;
        case LONG -> Opcodes.LLOAD;
        case FLOAT -> Opcodes.FLOAD;
        case DOUBLE -> Opcodes.DLOAD;
        case STRING -> Opcodes.ALOAD;
        case STRING_ARR -> Opcodes.AALOAD;
        case VOID -> null;
      };
      if (opcode != null) {
        methodVisitor.visitVarInsn(opcode, idx);
      } else {
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
      }
    } else {
      methodVisitor.visitVarInsn(Opcodes.ALOAD, idx);
    }
  }

  private void generateBlock(Block block) {
    block.expressions().forEach(this::generate);
  }
}
