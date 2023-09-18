package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.ast.Pattern.Singleton;
import static com.pentlander.sasquach.ast.Pattern.VariantStruct;
import static com.pentlander.sasquach.ast.Pattern.VariantTuple;
import static com.pentlander.sasquach.backend.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.backend.ClassGenerator.paramDescs;
import static com.pentlander.sasquach.backend.ClassGenerator.signatureDescriptor;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.PatternVariable;
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
import com.pentlander.sasquach.ast.expression.Match;
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
import com.pentlander.sasquach.name.MemberScopedNameResolver.VariantStructConstructor;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.StructDispatch;
import com.pentlander.sasquach.runtime.SwitchBootstraps;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.ForeignFieldType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
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
import java.util.Objects;
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
  private final NameResolutionResult nameResolutionResult;
  private final TypeFetcher typeFetcher;
  private final LocalVarMeta localVarMeta;
  private Node contextNode;

  ExpressionGenerator(MethodVisitor methodVisitor, NameResolutionResult nameResolutionResult,
      List<FunctionParameter> params, TypeFetcher typeFetcher) {
    this.methodVisitor = methodVisitor;
    this.nameResolutionResult = nameResolutionResult;
    this.typeFetcher = typeFetcher;

    this.localVarMeta = LocalVarMeta.of(params);
  }

  private Type type(Expression expression) {
    return typeFetcher.getType(expression);
  }

  private Type type(Identifier identifier) {
    return typeFetcher.getType(identifier);
  }


  private org.objectweb.asm.Type asmType(String internalName) {
    return org.objectweb.asm.Type.getObjectType(internalName);
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
      for (var varMeta : localVarMeta.varMeta()) {
        var varDecl = varMeta.localVar();
        var idx = varMeta.idx();
        var varType = switch (varDecl) {
          case FunctionParameter funcParam -> type(funcParam.id());
          case VariableDeclaration varDeclar -> type(varDeclar.expression());
          case PatternVariable patternVar -> type(patternVar.id());
        };
        if (varType == null) {continue;}

        methodVisitor.visitLocalVariable(varDecl.name(),
            varType.classDesc().descriptorString(),
            signatureDescriptor(varType),
            varMeta.label(),
            endLabel,
            idx);
      }
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
  }

  DirectMethodHandleDesc methodHandleDesc(Kind kind, Class<?> owner, String name,
      MethodTypeDesc typeDesc) {
    return MethodHandleDesc.ofMethod(kind, owner.describeConstable().orElseThrow(), name, typeDesc);
  }

  void generate(Expression expression) {
    addContextNode(expression);
    switch (expression) {
      case PrintStatement printStatement -> {
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
      case VariableDeclaration varDecl -> {
        var varDeclExpr = varDecl.expression();
        var varMeta = localVarMeta.push(varDecl);
        methodVisitor.visitLabel(varMeta.label());
        generate(varDeclExpr);
        var varType = type(varDeclExpr);
        generateStoreVar(methodVisitor, varType, varMeta.idx());
      }
      case VarReference varReference -> {
        var refDecl = nameResolutionResult.getVarReference(varReference);
        switch (refDecl) {
          case ReferenceDeclaration.Local(var localVar) ->
              generateLoadVar(methodVisitor, type(varReference), localVarMeta.get(localVar).idx());
          case ReferenceDeclaration.Module module ->
              generate(MethodHandleDesc.ofField(Kind.STATIC_GETTER,
                  ClassDesc.of(module.moduleDeclaration().id().javaName()),
                  INSTANCE_FIELD,
                  type(varReference).classDesc()));
          case ReferenceDeclaration.Singleton singleton ->
              generate(MethodHandleDesc.ofField(Kind.STATIC_GETTER,
                  ClassDesc.of(singleton.node().type().internalName().replace('/', '.')),
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
        var callTarget = nameResolutionResult.getLocalFunctionCallTarget(funcCall);
        switch (callTarget) {
          case QualifiedFunction qualifiedFunc -> {
            var arguments = funcCall.arguments();
            var funcType = funcType(qualifiedFunc.id());
            generateArgs(arguments, funcType.parameterTypes());
            var methodDesc = MethodHandleDesc.ofMethod(Kind.STATIC,
                ClassDesc.of(qualifiedFunc.ownerId().javaName()),
                funcCall.name(),
                funcType.functionDesc());
            generate(methodDesc);
          }
          case LocalVariable localVar -> {
            int idx = localVarMeta.get(localVar).idx();
            var type = switch (localVar) {
              case VariableDeclaration varDecl -> funcType(varDecl.expression());
              case FunctionParameter funcParam -> funcType(funcParam.id());
              case PatternVariable patternVariable -> funcType(patternVariable.id());
            };
            generateLoadVar(methodVisitor, type, idx);
            generateArgs(funcCall.arguments(), type.parameterTypes());
            generateMemberCall(type, "_invoke");
          }
          case VariantStructConstructor variantStructConstructor ->
              generateStructInit((StructType) type(variantStructConstructor.id()),
                  variantStructConstructor.struct());
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
          generateFieldAccess(fieldAccess.fieldName(),
              fieldAccessType.get().fieldType(fieldAccess.fieldName()));
        } else {
          throw new IllegalStateException("Failed to generate field access of type %s".formatted(
              fieldAccessType));
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
            fieldType.classDesc().descriptorString());
      }
      case ForeignFunctionCall foreignFuncCall -> {
        var foreignFuncType = typeFetcher.getType(foreignFuncCall.classAlias(),
            foreignFuncCall.functionId());
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
      case MemberFunctionCall structFuncCall -> {
        generate(structFuncCall.structExpression());

        var structType = TypeUtils.asStructType(type(structFuncCall.structExpression())).get();
        var funcType = TypeUtils.asFunctionType(Objects.requireNonNull(structType.fieldType(
            structFuncCall.name()))).get();
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
          generateStoreVar(methodVisitor, type(arg), localVarMeta.get(varDecl).idx());
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
      case Match match -> {
        generate(match.expr());
        methodVisitor.visitInsn(Opcodes.DUP);
        int exprVarIdx = localVarMeta.pushHidden();
        generateStoreVar(methodVisitor, type(match.expr()), exprVarIdx);

        methodVisitor.visitInsn(Opcodes.ICONST_0);
        var handle = new Handle(Opcodes.H_INVOKESTATIC,
            new ClassType(SwitchBootstraps.class).internalName(),
            "bootstrapSwitch",
            MATCH_BOOTSTRAP_DESCRIPTOR,
            false);

        var branches = match.branches();
        var branchTypes = branches.stream()
            .map(branch -> type((Identifier) branch.pattern().id()))
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
        var exInternalName = GeneratorUtil.internalName(MatchException.class);
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
            case Singleton singleton -> {}
            case VariantTuple variantTuple -> {
              var variantType = (StructType) type((Identifier) variantTuple.id());
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
                var bindType = type(binding.id());
                int idx = localVarMeta.push(binding).idx();
                generateStoreVar(methodVisitor, bindType, idx);
              }
            }
            case VariantStruct variantStruct -> {
              throw new IllegalStateException("Not implemented");
            }
          }
          ;
          generate(branches.get(i).expr());
          methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        methodVisitor.visitLabel(endLabel);
      }
      case default -> throw new IllegalStateException("Unrecognized expression: " + expression);
    }
  }

  private void generateFieldAccess(String fieldName, Type fieldType) {
    var handle = new Handle(Opcodes.H_INVOKESTATIC,
        GeneratorUtil.internalName(StructDispatch.class),
        "bootstrapField",
        DISPATCH_BOOTSTRAP_DESCRIPTOR,
        false);
    var typeDesc = MethodTypeDesc.of(fieldType.classDesc(), classDesc(StructBase.class));
    methodVisitor.visitInvokeDynamicInsn(fieldName, typeDesc.descriptorString(), handle);
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
        GeneratorUtil.internalName(StructDispatch.class),
        "bootstrapMethod",
        DISPATCH_BOOTSTRAP_DESCRIPTOR,
        false);
    var methodTypeDesc = funcType.functionDesc()
        .insertParameterTypes(0, classDesc(StructBase.class));
    methodVisitor.visitInvokeDynamicInsn(funcCallName, methodTypeDesc.descriptorString(), handle);
  }

  void generateFuncInit(String funcStructInternalName, List<VarReference> captures) {
    generateNewDup(funcStructInternalName);
    captures.forEach(this::generateExpr);
    var paramDescs = paramDescs(captures, typeFetcher);
    var constructorDesc = MethodHandleDesc.ofConstructor(classDesc(funcStructInternalName),
        paramDescs);
    generate(constructorDesc);
  }

  // TODO: Add test to ensure that the arguments are passed in in a consistent order. Field args
  //  should be evaluated in the order they appear, but that doesn't necessarily correspond with
  //  the order of the constructor parameters. Since we're iterating over a hashmap, the order is
  //  not consistent.
  void generateStructInit(StructType structType, Struct struct) {
    var structName = structType.internalName();
    var structClassDesc = ClassDesc.of(structType.internalName().replace('/', '.'));
    generateNewDup(structName);
    struct.fields().forEach(field -> generateExpr(field.value()));
    var paramDescs = paramDescs(structType.fieldTypes().values());
    generate(MethodHandleDesc.ofConstructor(structClassDesc, paramDescs));
  }

  void generateStructInit(Struct struct) {
    var structName = type(struct).internalName();
    var structClassDesc = ClassDesc.of(type(struct).internalName().replace('/', '.'));
    generateNewDup(structName);
    struct.fields().forEach(field -> generateExpr(field.value()));
    var paramDescs = paramDescs(struct.fields(), typeFetcher);
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
    if (actualType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT && (
        expectedType instanceof UniversalType || expectedType instanceof TypeVariable)) {
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
    if (expectedType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT && (
        actualType instanceof UniversalType || actualType instanceof TypeVariable)) {
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
