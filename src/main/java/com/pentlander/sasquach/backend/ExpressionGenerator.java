package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.ast.InvocationKind.SPECIAL;
import static com.pentlander.sasquach.backend.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.backend.ClassGenerator.STRUCT_BASE_INTERNAL_NAME;
import static com.pentlander.sasquach.backend.ClassGenerator.constructorType;
import static com.pentlander.sasquach.backend.ClassGenerator.signatureDescriptor;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.ApplyOperator;
import com.pentlander.sasquach.ast.expression.ArrayValue;
import com.pentlander.sasquach.ast.expression.BinaryExpression;
import com.pentlander.sasquach.ast.expression.BinaryExpression.BooleanExpression;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.FieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.ast.expression.IfExpression;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.Loop;
import com.pentlander.sasquach.ast.expression.MemberFunctionCall;
import com.pentlander.sasquach.ast.expression.PrintStatement;
import com.pentlander.sasquach.ast.expression.Recur;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.StructDispatch;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.ForeignFieldType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
import com.pentlander.sasquach.type.TypeUtils;
import com.pentlander.sasquach.type.TypeVariable;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Deque;
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
  private final Deque<Label> loopLabels = new ArrayDeque<>();
  private final Deque<LocalVarLabel> localVarLabels = new ArrayDeque<>();
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

  private FunctionType funcType(Expression expression) {
    return TypeUtils.asFunctionType(typeFetcher.getType(expression)).orElseThrow();
  }

  private FunctionType funcType(Identifier identifier) {
    return TypeUtils.asFunctionType(typeFetcher.getType(identifier)).orElseThrow();
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
      var endLabel = new Label();
      methodVisitor.visitLabel(endLabel);
      for (var localVarLabel : localVarLabels) {
        var varDecl = localVarLabel.varDecl();
        var idx = nameResolutionResult.getVarIndex(varDecl);
        var varType = type(varDecl.expression());
        methodVisitor.visitLocalVariable(varDecl.name(),
            varType.descriptor(),
            signatureDescriptor(varType),
            localVarLabel.label(),
            endLabel,
            idx);
      }
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
        var localVarLabel = new LocalVarLabel(varDecl);
        localVarLabels.push(localVarLabel);
        methodVisitor.visitLabel(localVarLabel.label());
        generate(varDeclExpr);
        var varType = type(varDeclExpr);
        generateStoreVar(methodVisitor, varType, idx);
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
        var callTarget = nameResolutionResult.getLocalFunction(funcCall);
        switch (callTarget) {
          case QualifiedFunction qualifiedFunc -> {
            var arguments = funcCall.arguments();
            var funcType = funcType(qualifiedFunc.id());
            generateArgs(arguments, funcType.parameterTypes());
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                qualifiedFunc.ownerId().name(),
                funcCall.name(),
                funcType.funcDescriptor(),
                false);
          }
          case LocalVariable localVar -> {
            var idx = nameResolutionResult.getVarIndex(localVar);
            var type = switch (localVar) {
              case VariableDeclaration varDecl -> funcType(varDecl.expression());
              case FunctionParameter funcParam -> funcType(funcParam.id());
            };
            generateLoadVar(methodVisitor, type, idx);
            generateArgs(funcCall.arguments(), type.parameterTypes());
            generateMemberCall(type, "_invoke");
          }
        }
      }
      case BinaryExpression binExpr -> {
        switch (binExpr) {
          case BinaryExpression.MathExpression mathExpr -> {
            generate(binExpr.left());
            generate(binExpr.right());
            int opcode = switch (mathExpr.operator()) {
              case PLUS -> Opcodes.IADD;
              case MINUS -> Opcodes.ISUB;
              case TIMES -> Opcodes.IMUL;
              case DIVIDE -> Opcodes.IDIV;
            };
            methodVisitor.visitInsn(opcode);
          }
          case BinaryExpression.CompareExpression cmpExpr -> {
            generate(binExpr.left());
            generate(binExpr.right());
            int opCode = switch (cmpExpr.operator()) {
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
          case BooleanExpression boolExpr -> {
            int opCode = switch (boolExpr.operator()) {
              case AND -> Opcodes.IFEQ;
              case OR -> Opcodes.IFNE;
            };
            var labelEnd = new Label();
            generate(binExpr.left());
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitJumpInsn(opCode, labelEnd);
            methodVisitor.visitInsn(Opcodes.POP);
            generate(binExpr.right());
            methodVisitor.visitLabel(labelEnd);
          }
        }
      }
      case IfExpression ifExpr -> {
        generate(ifExpr.condition());
        var hasFalseExpr = ifExpr.falseExpression() != null;
        var falseLabel = new Label();
        var endLabel = new Label();
        var ifEqLabel = hasFalseExpr ? falseLabel : endLabel;
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, ifEqLabel);
        generate(ifExpr.trueExpression());
        if (hasFalseExpr) {
          methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
          methodVisitor.visitLabel(falseLabel);
          generate(ifExpr.falseExpression());
        } else {
          generatePop(ifExpr.trueExpression());
        }
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
              fieldAccessType.get().fieldType(fieldAccess.fieldName()).descriptor();
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

        var structType = TypeUtils.asStructType(type(structFuncCall.structExpression())).get();
        var funcType =
            TypeUtils.asFunctionType(Objects.requireNonNull(structType.fieldType(structFuncCall.name()))).get();
        var arguments = structFuncCall.arguments();

        generateArgs(arguments, funcType.parameterTypes());
        generateMemberCall(funcType, structFuncCall.name());
        var funcCallType = type(structFuncCall);
        tryUnbox(funcCallType, funcType.returnType());
      }
      case Loop loop -> {
        loop.varDeclarations().forEach(this::generate);
        var recurPoint = new Label();
        loopLabels.addLast(recurPoint);
        methodVisitor.visitLabel(recurPoint);
        generate(loop.expression());
        loopLabels.removeLast();
      }
      case Recur recur -> {
        var recurPoint = nameResolutionResult.getRecurPoint(recur);
        var localVars = switch (recurPoint) {
          case Loop loop -> loop.varDeclarations();
          case Function func -> func.parameters();
        };

        for (int i = 0; i < recur.arguments().size(); i++) {
          var arg = recur.arguments().get(i);
          var varDecl = localVars.get(i);
          generate(arg);
          generateStoreVar(methodVisitor, type(arg), nameResolutionResult.getVarIndex(varDecl));
        }
        methodVisitor.visitJumpInsn(Opcodes.GOTO, loopLabels.getLast());
      }
      case Function func -> {
        var classGen = new ClassGenerator(nameResolutionResult, typeFetcher);
        var name = classGen.generateFunctionStruct(func, List.of());

        generatedClasses.putAll(classGen.getGeneratedClasses());
        generateFuncInit(name, List.of());
      }
      case ApplyOperator applyOperator -> generate(applyOperator.toFunctionCall());
      case default -> throw new IllegalStateException("Unrecognized expression: " + expression);
    }
  }

  private void generateArgs(List<Expression> arguments, List<Type> paramTypes) {
    for (int i = 0; i < arguments.size(); i++) {
      var expr = arguments.get(i);
      generate(expr);
      tryBox(type(expr), paramTypes.get(i));
    }
  }

  void generateMemberCall(FunctionType funcType, String funcCallName) {
    var handle = new Handle(Opcodes.H_INVOKESTATIC,
        new ClassType(StructDispatch.class).internalName(),
        "bootstrapMethod",
        BOOTSTRAP_DESCRIPTOR,
        false);
    methodVisitor.visitInvokeDynamicInsn(funcCallName,
        funcType.funcDescriptorWith(0, new ClassType(StructBase.class)),
        handle);
  }

  void generateFuncInit(String funcStructInternalName, List<VarReference> captures) {
    methodVisitor.visitTypeInsn(Opcodes.NEW, funcStructInternalName);
    methodVisitor.visitInsn(Opcodes.DUP);
    captures.forEach(this::generateExpr);
    var initDescriptor =
        ClassGenerator.constructorType(captures, typeFetcher);
    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        funcStructInternalName,
        "<init>",
        initDescriptor,
        false);
  }

  void generateStructInit(Struct struct) {
    var structName = type(struct).internalName();
    methodVisitor.visitTypeInsn(Opcodes.NEW, structName);
    methodVisitor.visitInsn(Opcodes.DUP);
    struct.fields().forEach(field -> generateExpr(field.value()));
    var initDescriptor = constructorType(struct.fields(), typeFetcher);
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

  static void generateStoreVar(MethodVisitor methodVisitor, Type type, int idx) {
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

  private void generateBlock(Block block) {
    var expressions = block.expressions();
    for (int i = 0; i < expressions.size(); i++) {
      Expression expr = expressions.get(i);
      generate(expr);
      if (i != expressions.size() - 1) {
        generatePop(expr);
      }
    }
  }

  private void generatePop(Expression expr) {
    if (!(expr instanceof VariableDeclaration) && !type(expr).equals(BuiltinType.VOID)) {
      methodVisitor.visitInsn(Opcodes.POP);
    }
  }

  /**
   * Convert a primitive type into its boxed type.
   * <p>This method should be used when providing a primitive to a function call with type
   * parameters, as the parameter type is {@link Object}.</p>
   */
  private void tryBox(Type actualType, Type expectedType) {
    if (actualType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT
        && expectedType instanceof TypeVariable) {
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          "java/lang/Integer",
          "valueOf",
          "(I)Ljava/lang/Integer;",
          false);
    }
  }

  /** Convert a boxed type into its primitive type. */
  private void tryUnbox(Type expectedType, Type actualType) {
    if (expectedType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT
        && actualType instanceof TypeVariable) {
      methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
      methodVisitor.visitInsn(Opcodes.DUP);
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL,
          "java/lang/Integer",
          "intValue",
          "()I",
          false);
    }
  }

  private record LocalVarLabel(VariableDeclaration varDecl, Label label) {
    public LocalVarLabel(VariableDeclaration varDecl) {
      this(varDecl, new Label());
    }
  }
}
