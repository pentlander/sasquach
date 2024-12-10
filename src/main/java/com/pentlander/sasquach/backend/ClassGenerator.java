package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.Util.seqMap;
import static com.pentlander.sasquach.backend.GeneratorUtil.MTD_EQUALS;
import static com.pentlander.sasquach.backend.GeneratorUtil.box;
import static com.pentlander.sasquach.backend.GeneratorUtil.generateLoadVar;
import static com.pentlander.sasquach.backend.GeneratorUtil.internalClassDesc;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
import static java.lang.classfile.Signature.ClassTypeSig;
import static java.lang.classfile.Signature.TypeVarSig;

import com.pentlander.sasquach.SourcePath;
import com.pentlander.sasquach.ast.expression.Tuple;
import com.pentlander.sasquach.backend.AnonFunctions.NamedAnonFunc;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.backend.ExpressionGenerator.ExprContext;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.runtime.bootstrap.Func;
import com.pentlander.sasquach.runtime.bootstrap.StructDispatch;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionParameter.Label;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TModuleStruct;
import com.pentlander.sasquach.tast.expression.TModuleStruct.TypeDef;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.StructType.RowModifier;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import com.pentlander.sasquach.type.UniversalType;
import com.pentlander.sasquach.type.VariantType;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFile.ClassHierarchyResolverOption;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.Signature.ArrayTypeSig;
import java.lang.classfile.Signature.BaseTypeSig;
import java.lang.classfile.Signature.TypeParam;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.components.ClassPrinter;
import java.lang.classfile.components.ClassPrinter.Verbosity;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.dynalink.StandardOperation;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("preview")
class ClassGenerator {
  static final MethodTypeDesc MTD_TO_STRING = MethodTypeDesc.of(ConstantDescs.CD_String);
  static final MethodTypeDesc MTD_HASHCODE = MethodTypeDesc.of(ConstantDescs.CD_int);
  static final MethodTypeDesc MTD_HASH = MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_Object.arrayType());
  static final ClassDesc CD_STRUCT_BASE = classDesc(StructBase.class);
  static final String INSTANCE_FIELD = "INSTANCE";
  private final Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
  private final SasqClassHierarchyResolver resolver = new SasqClassHierarchyResolver();
  @Nullable private TypedNode contextNode;

  private final QualifiedModuleName parentModuleName;

  ClassGenerator(QualifiedModuleName parentModuleName) {
    this.parentModuleName = parentModuleName;
  }

  public Map<String, byte[]> generate(TModuleDeclaration moduleDeclaration) {
    try {
      generateTStruct(moduleDeclaration.struct());
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
    return generatedClasses;
  }

  public Map<String, byte[]> generate(TStruct struct) {
    generateTStruct(struct);
    return generatedClasses;
  }

  private void setContext(TypedNode node) {
    contextNode = node;
  }

  private static void generateStructStart(ClassBuilder clb, ClassDesc structDesc, @Nullable SourcePath sourcePath,
      SequencedMap<UnqualifiedName, Type> fields, ClassDesc... interfaceDescs) {
    var entries = List.copyOf(fields.entrySet());

    var allInterfaces = new ArrayList<ClassDesc>(interfaceDescs.length + 1);
    allInterfaces.add(CD_STRUCT_BASE);
    allInterfaces.addAll(Arrays.asList(interfaceDescs));

    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL)
        .withInterfaceSymbols(allInterfaces);
    if (sourcePath != null) {
      clb.with(SourceFileAttribute.of(sourcePath.filepath()));
    }

    // Generate fields
    var fieldClasses = new ArrayList<ClassDesc>();
    for (var entry : entries) {
      var name = entry.getKey().toString();
      var classDesc = entry.getValue().classDesc();
      clb.withField(name, classDesc, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL);
      fieldClasses.add(classDesc);
    }

    // Generate constructor
    clb.withMethodBody(
        ConstantDescs.INIT_NAME,
        MethodTypeDesc.of(ConstantDescs.CD_void, fieldClasses),
        ClassFile.ACC_PUBLIC,
        cob -> {
          cob.aload(0)
              .invokespecial(ConstantDescs.CD_Object,
                  ConstantDescs.INIT_NAME,
                  ConstantDescs.MTD_void);

          int slot = 1;
          for (var entry : entries) {
            var fieldName = entry.getKey().toString();
            var fieldType = entry.getValue().classDesc();
            cob.aload(0)
                .loadInstruction(TypeKind.from(fieldType), slot++)
                .putfield(structDesc, fieldName, fieldType);
          }
          cob.return_();
        });

    // Generate method wrappers for all the Func fields for Java compat
    fields.forEach((name, type) -> {
      if (type instanceof FunctionType funcType) {
        generateFunctionWrapper(clb, structDesc, name, funcType);
      }
    });

    // TODO: If there's an equals method where the param type matches the struct, change the equals
    //  impl generated to delegate to that func
    generateEquals(clb, structDesc, fields);
    // TODO: Don't generate hashCode method if the struct already has one
    generateHashCode(clb, structDesc, fields);
  }

  private void generateSumType(SumType sumType) {
    buildAddClass(sumType, cob -> generateSumType(cob, sumType));
  }

  private void buildAddClass(String className, ClassDesc type, Consumer<? super ClassBuilder> handler) {
     var bytes = buildClass(type, handler);
     generatedClasses.put(className.replace('/', '.'), bytes);
  }

  private void buildAddClass(Type type, Consumer<? super ClassBuilder> handler) {
    buildAddClass(type.internalName(), internalClassDesc(type), handler);
  }

  private byte[] buildClass(ClassDesc type, Consumer<? super ClassBuilder> handler) {
    var opt = ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver()
        .orElse(resolver));
    var classFile = ClassFile.of(opt);
    var bytes = classFile.build(type, clb -> {
      clb.withVersion(ClassFile.JAVA_19_VERSION, 0);
      handler.accept(clb);
    });
    classFile.verify(bytes).stream().findFirst().ifPresent(err -> {
      ClassPrinter.toYaml(classFile.parse(bytes), Verbosity.CRITICAL_ATTRIBUTES, System.err::printf);
      throw err;
    });
    return bytes;
  }

  // Generate an interface to act as the parent to the sum type variants
  private void generateSumType(ClassBuilder clb, SumType sumType) {
    var permittedSubclassDescs = sumType.types()
        .stream()
        .map(VariantType::internalClassDesc)
        .toList();
    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.ABSTRACT, AccessFlag.INTERFACE)
        .withInterfaceSymbols(CD_STRUCT_BASE)
        .with(PermittedSubclassesAttribute.ofSymbols(permittedSubclassDescs));
    resolver.addSumType(sumType.internalClassDesc());
  }

  private void generateSingleton(SingletonType singleton, SumType sumType, SourcePath sourcePath) {
    buildAddClass(singleton, clb -> generateSingleton(clb, singleton, sumType, sourcePath));
  }

  private void generateSingleton(ClassBuilder clb, SingletonType singleton, SumType sumType, SourcePath sourcePath) {
    var structDesc = singleton.internalClassDesc();
    generateStructStart(clb, structDesc, sourcePath, seqMap(), sumType.internalClassDesc());

    generateStaticInstance(clb, singleton.classDesc(),
        structDesc,
        cob -> {
          cob.new_(structDesc)
              .dup();
          var constructorDesc = MethodHandleDesc.ofConstructor(structDesc);
          GeneratorUtil.generate(cob, constructorDesc);
        });
  }

  private void generateVariantStruct(StructType structType, SumType sumType, SourcePath sourcePath) {
    buildAddClass(structType, clb -> generateVariantStruct(clb, structType, sumType, sourcePath));
  }

  private void generateVariantStruct(ClassBuilder clb, StructType structType, SumType sumType, SourcePath sourcePath) {
    generateStructStart(
        clb,
        structType.internalClassDesc(),
        sourcePath,
        structType.memberTypes(),
        sumType.internalClassDesc());
  }

  private void generateStruct(StructType structType, SourcePath sourcePath) {
    buildAddClass(structType, clb -> generateStruct(clb, structType, sourcePath));
  }

  private void generateStruct(ClassBuilder clb, StructType structType, SourcePath sourcePath) {
    generateStructStart(
        clb,
        structType.internalClassDesc(),
        sourcePath,
        structType.memberTypes());
  }

  public Map<String, byte[]> generateTuples() {
    for (int i = 1; i < 10; i++) {
      var memberTypes = new LinkedHashMap<UnqualifiedName, Type>();
      for (int j = 0; j < i; j++) {
        memberTypes.put(new UnqualifiedName("_" + j), new UniversalType("A" + j));
      }
      var typeParams = IntStream.range(0, i)
          .mapToObj(j -> new TypeParameter(new UnqualifiedTypeName("A" + j)))
          .toList();
      var structType = new StructType(Tuple.tupleName(i), typeParams, memberTypes, RowModifier.none());
      buildAddClass(structType, clb -> generateStructStart(clb,
          structType.internalClassDesc(), null,
          structType.memberTypes()));
    }

    return generatedClasses;
  }

  private void generateTStruct(TStruct struct) {
    buildAddClass(struct.type(), clb -> generateTStruct(clb, struct));
  }

  private void generateTypeConstructor(ClassBuilder clb, UnqualifiedTypeName typeName, ClassDesc classDesc, FunctionType constructorType) {
    var signature = generateMethodSignature(constructorType);
    clb.withMethod(typeName.toString(), constructorType.functionTypeDesc(), ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL, mb -> {
      if (signature != null) {
        mb.with(SignatureAttribute.of(signature));
      }
      mb.withCode(cob -> {
        cob.new_(classDesc).dup();
        // Start at 1 since 0 is `this`
        int idx = 1;
        for (var type : constructorType.parameterTypes()) {
          generateLoadVar(cob, type, idx++);
        }
        var methodDesc = MethodHandleDesc.ofConstructor(classDesc,
            constructorType.parameterTypes().stream().map(Type::classDesc).toArray(ClassDesc[]::new));
        GeneratorUtil.generate(cob, methodDesc);
        cob.areturn();
      });
    });
  }

  /** Generate classes for the named structs defined in the type aliases. */
  private void generateNamedTypes(ClassBuilder clb, List<TypeDef> typeDefs) {
    for (var typeDef : typeDefs) {
      var sourcePath = typeDef.sourcePath();
      switch (typeDef.type()) {
        case SumType sumType -> {
          generateSumType(sumType);
          sumType.types().forEach(variantType -> {
            var name = variantType.name().simpleName();
            var constructorType = variantType.constructorType(sumType);
            switch (variantType) {
              case StructType structType -> {
                generateVariantStruct(structType, sumType, sourcePath);
                generateTypeConstructor(clb, name, structType.internalClassDesc(), constructorType);

              }
              case SingletonType singletonType -> {
                generateSingleton(singletonType, sumType, sourcePath);
                generateTypeConstructor(clb, name, singletonType.internalClassDesc(), constructorType);
              }
            }
          });
        }
        case StructType type -> {
          generateStruct(type, sourcePath);
          var structName = type.name().simpleName();
          generateTypeConstructor(clb, structName, type.internalClassDesc(), type.constructorType());
        }
        default -> {}
      }
    }
  }

  private static void generateEquals(ClassBuilder clb, ClassDesc structDesc, SequencedMap<UnqualifiedName, Type> fields) {
    clb.withMethodBody("equals", MTD_EQUALS, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL, cob -> {
      var falseLabel = cob.newLabel();
      var thisSlot = cob.receiverSlot();
      var otherSlot = cob.parameterSlot(0);
      var castOtherSlot = cob.allocateLocal(TypeKind.ReferenceType);
      cob.aload(otherSlot).instanceof_(structDesc).ifeq(falseLabel)
          .aload(otherSlot)
          .checkcast(structDesc)
          .astore(castOtherSlot);
      fields.forEach((name, type) -> {
        var fieldName = name.toString();
        var fieldType = internalClassDesc(type);
        cob.aload(thisSlot)
            .getfield(structDesc, fieldName, fieldType)
            .aload(castOtherSlot)
            .getfield(structDesc, fieldName, fieldType);
        GeneratorUtil.generateEquals(cob, TypeKind.from(fieldType));
        cob.ifeq(falseLabel);
      });
      var returnLabel = cob.newLabel();
      cob.iconst_1().goto_(returnLabel).labelBinding(falseLabel).iconst_0().labelBinding(returnLabel).ireturn();
    });
  }

  private static void generateHashCode(ClassBuilder clb, ClassDesc structDesc, SequencedMap<UnqualifiedName, Type> fields) {
    clb.withMethodBody("hashCode", MTD_HASHCODE, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL, cob -> {
      var thisSlot = cob.receiverSlot();
      cob.ldc(fields.size());
      cob.anewarray(ConstantDescs.CD_Object);
      int i = 0;
      for (var field : fields.entrySet()) {
        var fieldName = field.getKey().toString();
        var fieldType = internalClassDesc(field.getValue());
        cob.dup().ldc(i++).aload(thisSlot).getfield(structDesc, fieldName, fieldType);
        box(cob, field.getValue());
        cob.aastore();
      }
      cob.invokestatic(classDesc(Objects.class), "hash", MTD_HASH)
          .ireturn();
    });
  }

  private void generateTStruct(ClassBuilder clb, TStruct struct) {
    setContext(struct);
    // Generate class header
    var structType = struct.structType();
    var structDesc = structType.internalClassDesc();

    // `struct.structType().memberTypes()` includes the named function types, which do not get
    // included as constructor parameters
    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    struct.fields().forEach(field -> fieldTypes.put(field.name(), field.type()));

    generateStructStart(clb, structDesc, struct.range().sourcePath(), fieldTypes);

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct instanceof TModuleStruct moduleStruct) {
      generateNamedTypes(clb, moduleStruct.typeDefs());
      generateStaticInstance(clb, structType.classDesc(),
          structType.internalClassDesc(),
          cob -> new ExpressionGenerator(parentModuleName, cob, ExprContext.INIT, "modInit", List.of()).generateStructInit(
              struct));

      // Generate methods
      for (var function : moduleStruct.functions()) {
        setContext(function);
        generateFunction(clb, function.name().toString(), function.function());
      }
    }
  }

  private void generateStaticInstance(ClassBuilder clb, ClassDesc fieldClassDesc, ClassDesc ownerClassDesc,
      Consumer<CodeBuilder> structInitGenerator) {
    // Generate INSTANCE field
    clb.withField(INSTANCE_FIELD,
        fieldClassDesc,
        fb -> fb.withFlags(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL));

    // Initialize INSTANCE with field expressions
    clb.withMethodBody(ConstantDescs.CLASS_INIT_NAME,
        ConstantDescs.MTD_void,
        ClassFile.ACC_STATIC,
        structInitGenerator.andThen(cob -> cob.putstatic(ownerClassDesc,
            INSTANCE_FIELD,
            fieldClassDesc).return_()));
  }

  private void generateFunction(ClassBuilder clb, String funcName, TFunction function) {
    // TODO Replace this with an annotation check once annotations are implemented
    generateFunction(clb, funcName, function, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL);
  }

  public static Signature typeSignature(Type type) {
    return switch (type) {
      case UniversalType t -> TypeVarSig.of(t.name());
      case BuiltinType t when t != BuiltinType.STRING ->
          BaseTypeSig.of(t.classDesc());
      case ArrayType t -> ArrayTypeSig.of(typeSignature(t.elementType()));
      default -> ClassTypeSig.of(type.classDesc());
    };
  }

  @Nullable
  private static MethodSignature generateMethodSignature(FunctionType funcType) {
    if (funcType.typeParameters().isEmpty()) {
      return null;
    }

    var typeParams = funcType.typeParameters()
        .stream()
        .map(typeParam -> TypeParam.of(typeParam.name().toString(),
            ClassTypeSig.of(ConstantDescs.CD_Object)))
        .toList();
    var paramTypeSigs = funcType.parameterTypes()
        .stream()
        .map(ClassGenerator::typeSignature)
        .toArray(Signature[]::new);
    return MethodSignature.of(typeParams,
        List.of(),
        typeSignature(funcType.returnType()),
        paramTypeSigs);

  }

  private static void generateFunctionWrapper(ClassBuilder clb, ClassDesc owner, UnqualifiedName fieldName, FunctionType funcType) {
    var signature = generateMethodSignature(funcType);
    var fieldNameStr = fieldName.toString();

    clb.withMethod(fieldNameStr, funcType.functionTypeDesc(), ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL, mb -> {
      if (signature != null) {
        mb.with(SignatureAttribute.of(signature));
      }
      mb.withCode(cob -> {
        int idx = 0;
        cob.aload(cob.receiverSlot()).dup().getfield(owner, fieldNameStr, funcType.classDesc()).swap();
        for (var param : funcType.parameters()) {
          GeneratorUtil.generateLoadVar(cob, param.type(), cob.parameterSlot(idx++));
        }

        var funcTypeDesc = funcType.functionTypeDesc().insertParameterTypes(0, Func.CD, ConstantDescs.CD_Object);
        cob.invokeDynamicInstruction(DynamicCallSiteDesc.of(
            StructDispatch.MHD_BOOTSTRAP_MEMBER,
            StandardOperation.CALL.toString(),
            funcTypeDesc));
        cob.returnInstruction(TypeKind.from(funcType.returnType().classDesc()));
      });
    });
  }


  private void generateFunction(ClassBuilder clb, String functionName, TFunction function,
      int methodFlags) {
    var funcType = function.typeWithCaptures();
    var signature = generateMethodSignature(funcType);
    // If a "main" method is found, need to generate a static method that delegates to instance
    int staticAcc = 0;
    final String funcName;
    if (functionName.equals("mainStatic")) {
      staticAcc = ClassFile.ACC_STATIC;
      funcName = "main";
    } else {
      funcName = functionName;
    }

    var namedAnonFuncs = new ArrayList<NamedAnonFunc>();
    clb.withMethod(funcName, funcType.functionTypeDesc(), methodFlags + staticAcc, mb -> {
      if (signature != null) {
        mb.with(SignatureAttribute.of(signature));
      }

      mb.withCode(cob -> {
        var lineNum = function.range().start().line();
        cob.lineNumber(lineNum);

        var captures = function.captures().stream().map(localVar -> new TFunctionParameter(localVar.id(), Label.none(), localVar.variableType(),
            localVar.range()));
        var params = Stream.concat(captures, function.parameters().stream()).toList();

        var context = ExprContext.NAMED_FUNC;
        if ((methodFlags & ClassFile.ACC_STATIC) != 0) {
          context = ExprContext.ANON_FUNC;
        }
        var exprGenerator = new ExpressionGenerator(parentModuleName, cob, context, funcName, params).initParams();
        var returnExpr = function.expression();
        if (returnExpr != null) {
          exprGenerator.generateExpr(returnExpr);
          cob.returnInstruction(TypeKind.from(returnExpr.type().classDesc()));
        } else {
          cob.return_();
        }
        generatedClasses.putAll(exprGenerator.getGeneratedClasses());
        namedAnonFuncs.addAll(exprGenerator.namedAnonFuncs());
      });
    });

    namedAnonFuncs.forEach(func -> generateFunction(
        clb,
        func.name(),
        func.function(),
        ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC));
  }
}
