package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.backend.ClassGenerator.CD_STRUCT_BASE;
import static com.pentlander.sasquach.backend.ClassGenerator.INSTANCE_FIELD;
import static com.pentlander.sasquach.backend.GeneratorUtil.tryBox;
import static com.pentlander.sasquach.type.TypeUtils.asStructType;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.backend.AnonFunctions.NamedAnonFunc;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.backend.TLocalVarMeta.TVarMeta;
import com.pentlander.sasquach.runtime.bootstrap.Func;
import com.pentlander.sasquach.runtime.bootstrap.FuncBootstrap;
import com.pentlander.sasquach.runtime.bootstrap.StructDispatch;
import com.pentlander.sasquach.runtime.bootstrap.SwitchBootstraps;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TPattern;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.*;
import com.pentlander.sasquach.tast.expression.TBasicFunctionCall.TArgs;
import com.pentlander.sasquach.tast.expression.TBasicFunctionCall.TCallTarget.LocalVar;
import com.pentlander.sasquach.tast.expression.TBasicFunctionCall.TCallTarget.Struct;
import com.pentlander.sasquach.tast.expression.TBinaryExpression.TBooleanExpression;
import com.pentlander.sasquach.tast.expression.TForeignFunctionCall.Varargs;
import com.pentlander.sasquach.tast.expression.TStruct.TField;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Local;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Module;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration.Singleton;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.FieldAccessKind;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeUtils;
import com.pentlander.sasquach.type.TypeVariable;
import com.pentlander.sasquach.type.UniversalType;
import java.io.PrintStream;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.dynalink.Namespace;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import org.jspecify.annotations.Nullable;

final class ExpressionGenerator {
  private final Map<String, byte[]> generatedClasses = new HashMap<>();
  private final Deque<Label> loopLabels = new ArrayDeque<>();
  private final AnonFunctions anonFunctions;
  private final CodeBuilder cob;
  private final TLocalVarMeta localVarMeta;
  @Nullable private TypedNode contextNode;

  ExpressionGenerator(CodeBuilder cob, Context context, String functionName,
      List<TFunctionParameter> params) {
    this.cob = cob;
    this.anonFunctions = new AnonFunctions(functionName);
    this.localVarMeta = TLocalVarMeta.of(params, context);
  }

  private void addContextNode(TypedNode node) {
    contextNode = node;
  }

  public ExpressionGenerator initParams() {
    localVarMeta.varMeta().forEach(this::attachVarMeta);
    return this;
  }

  public Map<String, byte[]> getGeneratedClasses() {
    return Map.copyOf(generatedClasses);
  }

  public List<NamedAnonFunc> namedAnonFuncs() {
    return anonFunctions.getFunctions();
  }

  public void generateExpr(TypedExpression expression) {
    try {
      generate(expression);
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
  }

  Type type(TypedNode expression) {
    return type(expression.type());
  }

  StructType type(TStruct expression) {
    return (StructType) type(expression.structType());
  }

  Type type(Type type) {
    if (type instanceof TypeVariable typeVar) {
      return typeVar.resolvedType().orElseThrow();
    }
    return type;
  }

  void attachVarMeta(TVarMeta varMeta) {
    var localVar = varMeta.localVar();
    cob.with(LocalVariable.of(
        varMeta.idx(),
        localVar.name().toString(),
        localVar.variableType().classDesc(),
        cob.newBoundLabel(),
        cob.endLabel()));
  }

  void generate(TypedExpression expression) {
    addContextNode(expression);
    cob.lineNumber(expression.range().start().line());

    switch (expression) {
      case TNot not -> {
        generate(not.expr());
        cob.ifThenElse(CodeBuilder::iconst_0, CodeBuilder::iconst_1);
      }
      case TPrintStatement printStatement -> {
        var printStreamDesc = classDesc(PrintStream.class);
        cob.getstatic(classDesc(System.class), "out", printStreamDesc);
        var expr = printStatement.expression();
        generate(expr);
        var methodType = MethodTypeDesc.of(ConstantDescs.CD_void, type(expr).classDesc());
        cob.invokevirtual(printStreamDesc, "println", methodType);
      }
      case TVariableDeclaration varDecl -> {
        var varDeclExpr = varDecl.expression();
        var varMeta = localVarMeta.push(varDecl);
        attachVarMeta(varMeta);
        generate(varDeclExpr);
        generateStoreVar(cob, type(varDeclExpr), varMeta.idx());
      }
      case TVarReference varReference -> {
        switch (varReference.refDeclaration()) {
          case Local(var localVar) ->
              // Getting an NPE here. Need to update function signature to include the parameters
            // for the captured vars
              generateLoadVar(localVar);
          case Module(var moduleId) ->
              cob.getstatic(moduleId.toClassDesc(), INSTANCE_FIELD, type(varReference).classDesc());
          case Singleton(var singletonType) -> cob.getstatic(singletonType.internalClassDesc(),
              INSTANCE_FIELD,
              type(varReference).classDesc());
        }
      }
      case Value value -> {
        var type = type(value);
        var literal = value.value();
        if (type instanceof BuiltinType builtinType) {
          switch (builtinType) {
            case BOOLEAN -> {
              boolean boolValue = Boolean.parseBoolean(literal);
              cob.bipush(boolValue? 1 : 0);
            }
            case INT, CHAR, BYTE, SHORT -> {
              int intValue = Integer.parseInt(literal);
              cob.bipush(intValue);
            }
            case LONG -> {
              long longValue = Long.parseLong(literal);
              cob.ldc(longValue);
            }
            case FLOAT -> {
              float floatValue = Float.parseFloat(literal);
              cob.ldc(floatValue);
            }
            case DOUBLE -> {
              double doubleValue = Double.parseDouble(literal);
              cob.ldc(doubleValue);
            }
            case STRING -> cob.ldc(literal.replace("\"", ""));
            case STRING_ARR -> {
            }
            case VOID -> cob.aconst_null();
          }
        }
      }
      case TArrayValue arrayValue -> {
        // TODO: Support primitive arrays.
        cob.bipush(arrayValue.expressions().size())
            .anewarray(arrayValue.type().classDesc().componentType());

        var expressions = arrayValue.expressions();
        for (int i = 0; i < expressions.size(); i++) {
          var expr = expressions.get(i);
          cob.dup().ldc(i);
          generate(expr);
          cob.aastore();
        }
      }
      case TBinaryExpression binExpr -> {
        switch (binExpr) {
          case TBinaryExpression.TMathExpression mathExpr -> {
            generate(binExpr.left());
            generate(binExpr.right());
            var opcode = switch (mathExpr.operator()) {
              case PLUS -> Opcode.IADD;
              case MINUS -> Opcode.ISUB;
              case TIMES -> Opcode.IMUL;
              case DIVIDE -> Opcode.IDIV;
            };
            cob.operatorInstruction(opcode);
          }
          case TBinaryExpression.TCompareExpression cmpExpr -> {
            generate(binExpr.left());
            generate(binExpr.right());
            var opCode = switch (cmpExpr.operator()) {
              case EQ -> Opcode.IF_ICMPEQ;
              case NE -> Opcode.IF_ICMPNE;
              case GE -> Opcode.IF_ICMPGE;
              case LE -> Opcode.IF_ICMPLE;
              case LT -> Opcode.IF_ICMPLT;
              case GT -> Opcode.IF_ICMPGT;
            };
            cob.ifThenElse(opCode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
          }
          case TBooleanExpression boolExpr -> {
            Consumer<Label> opCode = switch (boolExpr.operator()) {
              case AND -> cob::ifeq;
              case OR -> cob::ifne;
            };
            generate(binExpr.left());
            cob.dup();
            var labelEnd = cob.newLabel();
            opCode.accept(labelEnd);
            cob.pop();
            generate(binExpr.right());
            cob.labelBinding(labelEnd);
          }
        }
      }
      case TIfExpression ifExpr -> {
        generate(ifExpr.condition());
        var falseLabel = cob.newLabel();
        var endLabel = cob.newLabel();
        var hasFalseExpr = ifExpr.falseExpression() != null;
        var ifEqLabel = hasFalseExpr ? falseLabel : endLabel;
        cob.ifeq(ifEqLabel);
        generate(ifExpr.trueExpression());
        if (hasFalseExpr) {
          cob.goto_(endLabel)
              .labelBinding(falseLabel);
          generate(ifExpr.falseExpression());
        } else {
          generatePop(ifExpr.trueExpression());
        }
        cob.labelBinding(endLabel);
      }
      case TStruct struct -> {
        if (struct instanceof TLiteralStruct) {
          var classGen = new ClassGenerator();
          generatedClasses.putAll(classGen.generate(struct));
        }

        generateStructInit(struct);
      }
      case TFieldAccess fieldAccess -> {
        generate(fieldAccess.expr());
        var structType = asStructType(fieldAccess.expr().type()).orElseThrow();
        generateFieldAccess(cob, fieldAccess.fieldName(), structType.fieldType(fieldAccess.fieldName()));
      }
      case TBlock block -> generateBlock(block);
      case TForeignFieldAccess(_, var id, var ownerType, var fieldType, var accessKind) -> {
        var opCode = switch (accessKind) {
          case FieldAccessKind.INSTANCE -> Opcode.GETFIELD;
          case FieldAccessKind.STATIC -> Opcode.GETSTATIC;
        };
        cob.fieldInstruction(
            opCode,
            ownerType.classDesc(),
            id.name().toString(),
            fieldType.classDesc());
      }
      case TFunctionCall functionCall -> generateFunctionCall(functionCall);
      case TLoop loop -> {
        loop.varDeclarations().forEach(this::generate);
        var recurPoint = cob.newBoundLabel();
        loopLabels.addLast(recurPoint);
        generate(loop.expression());
        loopLabels.removeLast();
      }
      case TRecur recur -> {
        for (int i = 0; i < recur.arguments().size(); i++) {
          var arg = recur.arguments().get(i);
          var varDecl = recur.localVars().get(i);
          generate(arg);
          generateStoreVar(cob, type(arg), localVarMeta.get(varDecl).idx());
        }
        cob.goto_(loopLabels.getLast());
      }
      case TFunction func -> {
        var funcTypeDesc = func.typeWithCaptures().functionTypeDesc();
        var anonFuncName = anonFunctions.add(func);
        var captures = func.captures();

        cob.bipush(captures.size()).anewarray(ConstantDescs.CD_Object);
        for (int i = 0; i < captures.size(); i++) {
          var capture = captures.get(i);
          cob.dup().bipush(i);
          generateLoadVar(capture);
          GeneratorUtil.box(cob, capture.variableType());
          cob.aastore();
        }
        cob.invokeDynamicInstruction(FuncBootstrap.bootstrapFuncInit(anonFuncName, funcTypeDesc));
      }
      case TApplyOperator applyOperator -> generate(applyOperator.functionCall());
      case TMatch match -> {
        generate(match.expr());
        cob.dup();
        int exprVarIdx = localVarMeta.pushHidden();
        generateStoreVar(cob, type(match.expr()), exprVarIdx);

        cob.iconst_0();

        var branches = match.branches();
        var branchTypes = branches.stream()
            .map(branch -> branch.pattern().type())
            .map(GeneratorUtil::internalClassDesc)
            .toArray(ConstantDesc[]::new);
        var callSiteDesc = SwitchBootstraps.DCSD_SWITCH.withArgs(branchTypes);
        cob.invokeDynamicInstruction(callSiteDesc);

        var switchCases = new ArrayList<SwitchCase>(branches.size());
        for (int i = 0; i < branches.size(); i++) {
          switchCases.add(SwitchCase.of(i, cob.newLabel()));
        }
        var defaultLabel = cob.newLabel();
        cob.tableSwitchInstruction(0, branches.size() - 1, defaultLabel, switchCases)
            .labelBinding(defaultLabel);

        var exDesc = classDesc(MatchException.class);
        generateNewDup(exDesc);
        cob.aconst_null()
            .aconst_null();

        var methodDesc = MethodHandleDesc.ofConstructor(exDesc,
            ConstantDescs.CD_String,
            ConstantDescs.CD_Throwable);
        generate(methodDesc);
        cob.throwInstruction();

        var endLabel = cob.newLabel();
        for (int i = 0; i < branches.size(); i++) {
          cob.labelBinding(switchCases.get(i).target());
          var branch = branches.get(i);
          switch (branch.pattern()) {
            // No-op, don't need to load any vars
            case TPattern.TSingleton _ -> {}
            case TPattern.TVariantTuple variantTuple -> {
              var variantType = variantTuple.type();
              var tupleFieldTypes = List.copyOf(variantType.memberTypes().entrySet());
              var bindings = variantTuple.bindings();
              // Need to look up the types of the bindings, allocate a variable for each field,
              // then store the value of the field in the variable
              for (int j = 0; j < bindings.size(); j++) {
                // Load the value of the field and cast it
                GeneratorUtil.generateLoadVar(cob, variantType, exprVarIdx);
                cob.checkcast(variantType.internalClassDesc());

                var field = tupleFieldTypes.get(j);
                generateFieldAccess(cob, field.getKey(), field.getValue());

                // Store the value in the pattern variable
                var binding = bindings.get(j);
                var bindType = type(binding);
                int idx = localVarMeta.push(binding).idx();
                generateStoreVar(cob, bindType, idx);
              }
            }
            case TPattern.TVariantStruct variantStruct -> {
              var variantType = variantStruct.type();
              for (var binding : variantStruct.bindings()) {
                GeneratorUtil.generateLoadVar(cob, variantType, exprVarIdx);
                cob.checkcast(variantType.internalClassDesc());

                var fieldType = variantType.fieldType(binding.name());
                generateFieldAccess(cob, binding.name(), fieldType);

                var bindType = type(binding);
                int idx = localVarMeta.push(binding).idx();
                generateStoreVar(cob, bindType, idx);
              }

            }
          }
          generate(branches.get(i).expr());
          cob.goto_(endLabel);
        }
        cob.labelBinding(endLabel);
      }
      case TypedExprWrapper _ -> throw new IllegalStateException("Unrecognized expression: " + expression);
      case TThisExpr _ -> cob.aload(cob.receiverSlot());
    }
  }

  private void generateFunctionCall(TFunctionCall functionCall) {
    var name = functionCall.name();
    var args = functionCall.arguments();
    var returnType = functionCall.returnType();

    switch (functionCall) {
      case TForeignFunctionCall foreignFuncCall -> {
        var foreignFuncType = foreignFuncCall.foreignFunctionType();
        if (foreignFuncType.isConstructor()) {
          generateNewDup(foreignFuncType.ownerDesc());
        }

        if (foreignFuncCall.varargs() instanceof Varargs.Some(var arrayType, int varargsIdx)
            && args.size() >= varargsIdx) {
          for (int i = 0; i < varargsIdx; i++) {
            generate(args.get(i));
          }

          // Load array size
          cob.ldc(args.size() - varargsIdx);

          var elementClassDesc = arrayType.elementType().classDesc();
          var typeKind = TypeKind.from(elementClassDesc);
          if (typeKind == TypeKind.ReferenceType) {
            cob.anewarray(elementClassDesc);
          } else {
            cob.newarray(typeKind);
          }
          var arrIdx = 0;
          for (int i = varargsIdx; i < args.size(); i++) {
            cob.dup().ldc(arrIdx++);
            generate(args.get(i));
            cob.arrayStoreInstruction(typeKind);
          }
        } else {
          args.forEach(this::generate);
        }

        generate(foreignFuncType.methodHandleDesc());
        var castType = foreignFuncType.castType();
        if (castType != null) {
          cob.checkcast(castType.classDesc());
        }
      }
      case TBasicFunctionCall structFuncCall -> {
        var funcType = structFuncCall.functionType();
        switch (structFuncCall.callTarget()) {
          case LocalVar(var localVar) -> {
            generateLoadVar(localVar);
            cob.aconst_null();
          }
          case Struct(var structExpr) -> {
            generate(structExpr);
            cob.dup();
            // Consume one of the struct references to create a Func object, making the stack:
            // Struct -> Func, then swap them since the func call expects the order to be Func -> Struct
            generateFieldAccess(cob, name, funcType);
            cob.swap();
          }
        }

        generateArgs(structFuncCall.typedArgs(), funcType.parameters());
        var funcTypeDesc = funcType.functionTypeDesc().insertParameterTypes(0, classDesc(Func.class), ConstantDescs.CD_Object);
        cob.invokeDynamicInstruction(DynamicCallSiteDesc.of(
            StructDispatch.MHD_BOOTSTRAP_MEMBER,
            StandardOperation.CALL.toString(),
            funcTypeDesc));
        var funcCallType = type(structFuncCall);
        tryUnbox(funcCallType, returnType);
      }
    }
  }

  private static void generateFieldAccess(
      CodeBuilder cob,
      UnqualifiedName fieldName,
      Type fieldType
  ) {
    var isFunc = TypeUtils.asFunctionType(fieldType).isPresent();
    var namespaces = isFunc ? new Namespace[]{StandardNamespace.PROPERTY, StandardNamespace.METHOD}
        : new Namespace[]{StandardNamespace.PROPERTY};
    var operation = StandardOperation.GET
        .withNamespaces(namespaces)
        .named(fieldName)
        .toString();
    var typeDesc = MethodTypeDesc.of(fieldType.classDesc(), CD_STRUCT_BASE);

    cob.invokeDynamicInstruction(DynamicCallSiteDesc.of(StructDispatch.MHD_BOOTSTRAP_MEMBER, operation, typeDesc));
  }

  private void generateArgs(TArgs typedArgs, List<FunctionType.Param> params) {
    Integer argStartIdx = null;
    var args = typedArgs.args();
    for (var expr : args) {
      // Generate the expression
      generate(expr);

      int idx = localVarMeta.pushHidden();
      if (argStartIdx == null) {
        argStartIdx = idx;
      }
      generateStoreVar(cob, type(expr), idx);
    }

    var argIndexes = typedArgs.argIndexes();
    for (int i = 0; i < argIndexes.length; i++) {
      var argIndex = argIndexes[i];
      var argType = type(args.get(argIndex));
      var param = params.get(i);

      GeneratorUtil.generateLoadVar(cob, argType, requireNonNull(argStartIdx) + argIndex);
      tryBox(cob, argType, param.type());
    }
  }

  // TODO: Add test to ensure that the arguments are passed in in a consistent order. Field args
  //  should be evaluated in the order they appear, but that doesn't necessarily correspond with
  //  the order of the constructor parameters. Since we're iterating over a hashmap, the order is
  //  not consistent.
  void generateStructInit(TStruct struct) {
    var structType = type(struct);
    var structClassDesc = structType.internalClassDesc();
    var fields = struct.fields();

    if (struct instanceof TLiteralStruct literalStruct && !literalStruct.spreads().isEmpty()) {
      var fieldNames = new ConstantDesc[struct.fields().size()];
      for (int i = 0; i < fields.size(); i++) {
        TField tField = fields.get(i);
        fieldNames[i] = tField.name().toString();
        generateExpr(tField.expr());
      }
      literalStruct.spreads().forEach(spread -> {
        switch (spread.refDeclaration()) {
          case Local(var localVar) -> {
            var spreadStructType = TypeUtils.asStructType(localVar.variableType()).orElseThrow();
            GeneratorUtil.generateLoadVar(cob, spreadStructType, localVarMeta.get(localVar).idx());
          }
          case Module module -> {
            // TODO
          }
          case Singleton _ -> throw new IllegalStateException("Cannot spread singleton");
        }
      });

      var paramClassDescs = Stream.concat(fields.stream().map(field -> field.type().classDesc()),
          literalStruct.spreads().stream().map(spread -> spread.type().classDesc())).toList();
      var typeDesc = MethodTypeDesc.of(structType.classDesc(), paramClassDescs);
      var callSiteDesc = DynamicCallSiteDesc.of(StructDispatch.MHD_BOOTSTRAP_SPREAD, typeDesc)
          .withArgs(fieldNames);
      cob.invokeDynamicInstruction(callSiteDesc);
      return;
    }
    generateNewDup(structClassDesc);
    fields.forEach(field -> generateExpr(field.expr()));
    var paramDescs = struct.constructorParams()
        .stream()
        .map(Type::classDesc)
        .toArray(ClassDesc[]::new);
    generate(MethodHandleDesc.ofConstructor(structClassDesc, paramDescs));
  }

  private void generateLoadVar(TLocalVariable localVar) {
    var varMeta = localVarMeta.get(localVar);
    GeneratorUtil.generateLoadVar(cob, varMeta.localVar().variableType(), varMeta.idx());
  }

  private static void generateStoreVar(CodeBuilder cob, Type type, int idx) {
    cob.storeInstruction(TypeKind.from(type.classDesc()), idx);
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
      cob.pop();
    }
  }

  /**
   * Convert a boxed type into its primitive type.
   */
  void tryUnbox(Type expectedType, Type actualType) {
    if (expectedType instanceof BuiltinType builtinType && builtinType == BuiltinType.INT
        && (actualType instanceof UniversalType)) {
      cob.checkcast(ConstantDescs.CD_Integer)
          .invokevirtual(ConstantDescs.CD_Integer,
              "intValue",
              MethodTypeDesc.of(ConstantDescs.CD_int));
    }
  }

  private void generate(DirectMethodHandleDesc methodHandleDesc) {
    GeneratorUtil.generate(cob, methodHandleDesc);
  }

  private void generateNewDup(ClassDesc classDesc) {
    cob.new_(classDesc).dup();
  }

  public enum Context {
    INIT, NAMED_FUNC, ANON_FUNC
  }
}
