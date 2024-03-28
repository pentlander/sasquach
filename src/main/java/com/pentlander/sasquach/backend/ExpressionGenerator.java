package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.backend.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.backend.ClassGenerator.fieldParamDescs;
import static com.pentlander.sasquach.backend.ClassGenerator.signatureDescriptor;
import static com.pentlander.sasquach.type.TypeUtils.asStructType;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
import static com.pentlander.sasquach.type.TypeUtils.internalName;

import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.StructDispatch;
import com.pentlander.sasquach.runtime.SwitchBootstraps;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TPattern.TSingleton;
import com.pentlander.sasquach.tast.TPattern.TVariantStruct;
import com.pentlander.sasquach.tast.TPattern.TVariantTuple;
import com.pentlander.sasquach.tast.TPatternVariable;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TApplyOperator;
import com.pentlander.sasquach.tast.expression.TArrayValue;
import com.pentlander.sasquach.tast.expression.TBinaryExpression;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TBooleanExpression;
import com.pentlander.sasquach.tast.expression.TBlock;
import com.pentlander.sasquach.tast.expression.TFieldAccess;
import com.pentlander.sasquach.tast.expression.TForeignFieldAccess;
import com.pentlander.sasquach.tast.expression.TForeignFunctionCall;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TIfExpression;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall;
import com.pentlander.sasquach.tast.expression.TLocalFunctionCall.TargetKind;
import com.pentlander.sasquach.tast.expression.TLoop;
import com.pentlander.sasquach.tast.expression.TMatch;
import com.pentlander.sasquach.tast.expression.TMemberFunctionCall;
import com.pentlander.sasquach.tast.expression.TPrintStatement;
import com.pentlander.sasquach.tast.expression.TRecur;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.tast.expression.TStructWithName;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Local;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Module;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Singleton;
import com.pentlander.sasquach.tast.expression.TVariableDeclaration;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeUtils;
import com.pentlander.sasquach.type.TypeVariable;
import com.pentlander.sasquach.type.UniversalType;
import java.io.PrintStream;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ExpressionGenerator {
  private static final String DISPATCH_BOOTSTRAP_DESCRIPTOR = MethodType.methodType(CallSite.class,
      List.of(Lookup.class, String.class, MethodType.class)).descriptorString();
  private static final String MATCH_BOOTSTRAP_DESCRIPTOR = MethodType.methodType(CallSite.class,
          List.of(Lookup.class, String.class, MethodType.class, Object[].class))
      .descriptorString();
  private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
  private final Deque<Label> loopLabels = new ArrayDeque<>();
  private final MethodVisitor methodVisitor;
  private final TLocalVarMeta localVarMeta;
  private TypedNode contextNode;

  ExpressionGenerator(MethodVisitor methodVisitor, List<TFunctionParameter> params) {
    this.methodVisitor = methodVisitor;

    this.localVarMeta = TLocalVarMeta.of(params);
  }

  private org.objectweb.asm.Type asmType(String internalName) {
    return org.objectweb.asm.Type.getObjectType(internalName);
  }

  private void addContextNode(TypedNode node) {
    contextNode = node;
  }

  public Map<String, ClassWriter> getGeneratedClasses() {
    return Map.copyOf(generatedClasses);
  }

  public void generateExpr(TypedExpression expression) {
    try {
      generate(expression);
      var endLabel = new Label();
      methodVisitor.visitLabel(endLabel);
      for (var varMeta : localVarMeta.varMeta()) {
        var varType = type(varMeta.localVar().variableType());

        methodVisitor.visitLocalVariable(varMeta.localVar().name(),
            varType.classDesc().descriptorString(),
            signatureDescriptor(varType),
            varMeta.label(),
            endLabel,
            varMeta.idx());
      }
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
  }

  DirectMethodHandleDesc methodHandleDesc(Kind kind, Class<?> owner, String name,
      MethodTypeDesc typeDesc) {
    return MethodHandleDesc.ofMethod(kind, owner.describeConstable().orElseThrow(), name, typeDesc);
  }

  Type type(TypedNode expression) {
    return type(expression.type());
  }

  Type type(TStruct expression) {
    return type(expression.structType());
  }

  Type type(Type type) {
    if (type instanceof TypeVariable typeVar) {
      return typeVar.resolvedType().orElseThrow();
    }
    return type;
  }

  void generate(TypedExpression expression) {
    addContextNode(expression);
    switch (expression) {
      case TPrintStatement printStatement -> {
        generate(MethodHandleDesc.ofField(Kind.STATIC_GETTER,
            classDesc(System.class),
            "out",
            classDesc(PrintStream.class)));
        var expr = printStatement.expression();
        generate(expr);
        var methodType = MethodTypeDesc.of(ConstantDescs.CD_void, type(expr).classDesc());
        var methodDesc = methodHandleDesc(Kind.VIRTUAL, PrintStream.class, "println", methodType);
        generate(methodDesc);
      }
      case TVariableDeclaration varDecl -> {
        var varDeclExpr = varDecl.expression();
        var varMeta = localVarMeta.push(varDecl);
        methodVisitor.visitLabel(varMeta.label());
        generate(varDeclExpr);
        generateStoreVar(methodVisitor, type(varDeclExpr), varMeta.idx());
      }
      case TVarReference varReference -> {
        switch (varReference.refDeclaration()) {
          case Local(var localVar) ->
              generateLoadVar(methodVisitor, type(varReference), localVarMeta.get(localVar).idx());
          case Module(var moduleId) -> generate(MethodHandleDesc.ofField(Kind.STATIC_GETTER,
              ClassDesc.of(moduleId.javaName()),
              INSTANCE_FIELD,
              type(varReference).classDesc()));
          case Singleton(var singletonType) -> generate(MethodHandleDesc.ofField(Kind.STATIC_GETTER,
              ClassDesc.of(singletonType.internalName().replace('/', '.')),
              INSTANCE_FIELD,
              type(varReference).classDesc()));
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
      case TArrayValue arrayValue -> {
        // TODO: Support primitive arrays.
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, arrayValue.expressions().size());
        methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY,
            arrayValue.type().elementType().internalName());

        var expressions = arrayValue.expressions();
        for (int i = 0; i < expressions.size(); i++) {
          var expr = expressions.get(i);
          methodVisitor.visitInsn(Opcodes.DUP);
          methodVisitor.visitLdcInsn(i);
          generate(expr);
          methodVisitor.visitInsn(Opcodes.AASTORE);
        }
      }
      case TLocalFunctionCall funcCall -> {
        var funcType = funcCall.functionType();
        switch (funcCall.targetKind()) {
          case TargetKind.QualifiedFunction qualifiedFunc -> {
            generateArgs(funcCall.arguments(), funcType.parameterTypes());
            var methodDesc = MethodHandleDesc.ofMethod(Kind.STATIC,
                ClassDesc.of(qualifiedFunc.ownerId().javaName()),
                funcCall.name(),
                funcType.functionDesc());
            generate(methodDesc);
          }
          case TargetKind.LocalVariable(var localVar) -> {
            int idx = localVarMeta.get(localVar).idx();
            var type = TypeUtils.asFunctionType(type(localVar.variableType())).orElseThrow();
            generateLoadVar(methodVisitor, type, idx);
            generateArgs(funcCall.arguments(), type.parameterTypes());
            generateMemberCall(type, "_invoke");
          }
          case TargetKind.VariantStructConstructor(var struct) -> {
            var namedStruct = (TStructWithName) struct;
            var internalName = namedStruct.name();
            generateNewDup(internalName);
            generateArgs(funcCall.arguments(), funcType.parameterTypes());
            var methodDesc = MethodHandleDesc.ofMethod(Kind.CONSTRUCTOR,
                ClassDesc.of(internalName.replace('/', '.')),
                funcCall.name(),
                funcType.functionDesc().changeReturnType(ConstantDescs.CD_void));
            generate(methodDesc);
          }
        }
      }
      case TBinaryExpression binExpr -> {
        switch (binExpr) {
          case TBinaryExpression.TMathExpression mathExpr -> {
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
          case TBinaryExpression.TCompareExpression cmpExpr -> {
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
          case TBooleanExpression boolExpr -> {
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
      case TIfExpression ifExpr -> {
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
      case TStruct struct -> {
        var classGen = new ClassGenerator();
        classGen.generateStruct(struct);
        generatedClasses.putAll(classGen.getGeneratedClasses());

        generateStructInit(struct);
      }
      case TFieldAccess fieldAccess -> {
        generate(fieldAccess.expr());
        var structType = asStructType(fieldAccess.expr().type()).orElseThrow();
        generateFieldAccess(fieldAccess.fieldName(), structType.fieldType(fieldAccess.fieldName()));
      }
      case TBlock block -> generateBlock(block);
      case TForeignFieldAccess fieldAccess -> {
        var fieldType = fieldAccess.type();
        var opCode = switch (fieldType.accessKind()) {
          case INSTANCE -> Opcodes.GETFIELD;
          case STATIC -> Opcodes.GETSTATIC;
        };
        methodVisitor.visitFieldInsn(opCode,
            fieldType.ownerType().internalName(),
            fieldAccess.fieldName(),
            fieldType.classDesc().descriptorString());
      }
      case TForeignFunctionCall foreignFuncCall -> {
        var foreignFuncType = foreignFuncCall.foreignFunctionType();
        String owner = GeneratorUtil.internalName(foreignFuncType.ownerDesc());
        if (foreignFuncType.methodKind() == Kind.CONSTRUCTOR) {
          generateNewDup(owner);
        }
        foreignFuncCall.arguments().forEach(this::generate);

        generate(foreignFuncType.methodHandleDesc());
        var castType = foreignFuncType.castType();
        if (castType != null) {
          methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, castType.internalName());
        }
      }
      case TMemberFunctionCall structFuncCall -> {
        generate(structFuncCall.structExpression());
        var funcType = structFuncCall.functionType();

        generateArgs(structFuncCall.arguments(), funcType.parameterTypes());
        generateMemberCall(funcType, structFuncCall.name());
        var funcCallType = type(structFuncCall);
        tryUnbox(funcCallType, funcType.returnType());
      }
      case TLoop loop -> {
        loop.varDeclarations().forEach(this::generate);
        var recurPoint = new Label();
        loopLabels.addLast(recurPoint);
        methodVisitor.visitLabel(recurPoint);
        generate(loop.expression());
        loopLabels.removeLast();
      }
      case TRecur recur -> {
        for (int i = 0; i < recur.arguments().size(); i++) {
          var arg = recur.arguments().get(i);
          var varDecl = recur.localVars().get(i);
          generate(arg);
          generateStoreVar(methodVisitor, type(arg), localVarMeta.get(varDecl).idx());
        }
        methodVisitor.visitJumpInsn(Opcodes.GOTO, loopLabels.getLast());
      }
      case TFunction func -> {
        // TODO: Change generation of lambdas to be a static method and its method handle instead
        //  of a struct. Java only needs to generate a class because lambdas need to conform to
        //  an interface
        var classGen = new ClassGenerator();
        var name = classGen.generateFunctionStruct(func, List.of());

        generatedClasses.putAll(classGen.getGeneratedClasses());
        generateFuncInit(name, List.of());
      }
      case TApplyOperator applyOperator -> generate(applyOperator.functionCall());
      case TMatch match -> {
        generate(match.expr());
        methodVisitor.visitInsn(Opcodes.DUP);
        int exprVarIdx = localVarMeta.pushHidden();
        generateStoreVar(methodVisitor, type(match.expr()), exprVarIdx);

        methodVisitor.visitInsn(Opcodes.ICONST_0);
        var handle = new Handle(Opcodes.H_INVOKESTATIC,
            internalName(SwitchBootstraps.class),
            "bootstrapSwitch",
            MATCH_BOOTSTRAP_DESCRIPTOR,
            false);

        var branches = match.branches();
        var branchTypes = branches.stream()
            .map(branch -> branch.pattern().type())
            .map(type -> asmType(type.internalName()))
            .toArray();
        methodVisitor.visitInvokeDynamicInsn("match",
            SwitchBootstraps.DO_TYPE_SWITCH_TYPE.descriptorString(),
            handle,
            branchTypes);

        var defaultLabel = new Label();
        var labels = new Label[branches.size()];
        for (int i = 0; i < branches.size(); i++) {
          labels[i] = new Label();
        }
        methodVisitor.visitTableSwitchInsn(0, branches.size() - 1, defaultLabel, labels);

        methodVisitor.visitLabel(defaultLabel);
        var exInternalName = internalName(MatchException.class);
        generateNewDup(exInternalName);
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);

        var methodDesc = MethodHandleDesc.ofConstructor(classDesc(MatchException.class),
            ConstantDescs.CD_String,
            ConstantDescs.CD_Throwable);
        generate(methodDesc);
        methodVisitor.visitInsn(Opcodes.ATHROW);

        var endLabel = new Label();
        for (int i = 0; i < branches.size(); i++) {
          methodVisitor.visitLabel(labels[i]);
          var branch = branches.get(i);
          switch (branch.pattern()) {
            // No-op, don't need to load any vars
            case TSingleton singleton -> {}
            case TVariantTuple variantTuple -> {
              var variantType = variantTuple.type();
              var tupleFieldTypes = variantType.sortedFields();
              var bindings = variantTuple.bindings();
              // Need to look up the types of the bindings, allocate a variable for each field,
              // then store the value of the field in the variable
              for (int j = 0; j < bindings.size(); j++) {
                // Load the value of the field and cast it
                generateLoadVar(methodVisitor, variantType, exprVarIdx);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, variantType.internalName());

                var field = tupleFieldTypes.get(j);
                generateFieldAccess(field.getKey(), field.getValue());

                // Store the value in the pattern variable
                var binding = bindings.get(j);
                var bindType = type(binding);
                int idx = localVarMeta.push(binding).idx();
                generateStoreVar(methodVisitor, bindType, idx);
              }
            }
            case TVariantStruct variantStruct -> {
              var variantType = variantStruct.type();
              for (var binding : variantStruct.bindings()) {
                generateLoadVar(methodVisitor, variantType, exprVarIdx);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, variantType.internalName());

                var fieldType = variantType.fieldType(binding.name());
                generateFieldAccess(binding.name(), fieldType);

                var bindType = type(binding);
                int idx = localVarMeta.push(binding).idx();
                generateStoreVar(methodVisitor, bindType, idx);
              }

            }
          }
          generate(branches.get(i).expr());
          methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        methodVisitor.visitLabel(endLabel);
      }
      default -> throw new IllegalStateException("Unrecognized expression: " + expression);
    }
  }

  private void generateFieldAccess(String fieldName, Type fieldType) {
    var handle = new Handle(Opcodes.H_INVOKESTATIC,
        internalName(StructDispatch.class),
        "bootstrapField",
        DISPATCH_BOOTSTRAP_DESCRIPTOR,
        false);
    var operation = StandardOperation.GET.withNamespaces(StandardNamespace.PROPERTY)
        .named(fieldName)
        .toString();
    var typeDesc = MethodTypeDesc.of(fieldType.classDesc(), classDesc(StructBase.class));
    methodVisitor.visitInvokeDynamicInsn(operation, typeDesc.descriptorString(), handle);
  }

  private void generateArgs(List<TypedExpression> arguments, List<Type> paramTypes) {
    for (int i = 0; i < arguments.size(); i++) {
      var expr = arguments.get(i);
      generate(expr);
      tryBox(type(expr), paramTypes.get(i));
    }
  }

  void generateMemberCall(FunctionType funcType, String funcCallName) {
    var handle = new Handle(Opcodes.H_INVOKESTATIC,
        internalName(StructDispatch.class),
        "bootstrapMethod",
        DISPATCH_BOOTSTRAP_DESCRIPTOR,
        false);
    var methodTypeDesc = funcType.functionDesc()
        .insertParameterTypes(0, classDesc(StructBase.class));
    methodVisitor.visitInvokeDynamicInsn(funcCallName, methodTypeDesc.descriptorString(), handle);
  }

  void generateFuncInit(String funcStructInternalName, List<TVarReference> captures) {
    generateNewDup(funcStructInternalName);
    captures.forEach(this::generateExpr);
    var paramDescs = fieldParamDescs(captures);
    var constructorDesc = MethodHandleDesc.ofConstructor(classDesc(funcStructInternalName),
        paramDescs);
    generate(constructorDesc);
  }

  // TODO: Add test to ensure that the arguments are passed in in a consistent order. Field args
  //  should be evaluated in the order they appear, but that doesn't necessarily correspond with
  //  the order of the constructor parameters. Since we're iterating over a hashmap, the order is
  //  not consistent.
  // I need the struct name, the field types, and the expressions
  void generateStructInit(TStruct struct) {
    var structType = type(struct);
    var structName = structType.internalName();
    var structClassDesc = ClassDesc.of(structName.replace('/', '.'));
    generateNewDup(structName);
    struct.fields().forEach(field -> generateExpr(field.expr()));
    var paramDescs = ClassGenerator.fieldParamDescs(struct.fields());
    generate(MethodHandleDesc.ofConstructor(structClassDesc, paramDescs));
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

  private void generateBlock(TBlock block) {
    var expressions = block.expressions();
    for (int i = 0; i < expressions.size(); i++) {
      var expr = expressions.get(i);
      generate(expr);
      if (i != expressions.size() - 1) {
        generatePop(expr);
      }
    }
  }

  private void generatePop(TypedExpression expr) {
    if (!(expr instanceof TVariableDeclaration) && !type(expr).equals(BuiltinType.VOID)) {
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
        && (expectedType instanceof UniversalType)) {
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,
          "java/lang/Integer",
          "valueOf",
          "(I)Ljava/lang/Integer;",
          false);
    }
  }

  /**
   * Convert a boxed type into its primitive type.
   */
  private void tryUnbox(Type expectedType, Type actualType) {
    if (expectedType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT
        && (actualType instanceof UniversalType)) {
      methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
      methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
          "java/lang/Integer",
          "intValue",
          "()I",
          false);
    }
  }

  private void generate(DirectMethodHandleDesc methodHandleDesc) {
    GeneratorUtil.generate(methodVisitor, methodHandleDesc);
  }

  private void generateNewDup(String internalName) {
    methodVisitor.visitTypeInsn(Opcodes.NEW, internalName);
    methodVisitor.visitInsn(Opcodes.DUP);
  }
}
