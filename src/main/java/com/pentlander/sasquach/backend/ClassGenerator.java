package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.backend.GeneratorUtil.internalClassDesc;
import static com.pentlander.sasquach.type.TypeUtils.asFunctionType;
import static com.pentlander.sasquach.type.TypeUtils.asType;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
import static com.pentlander.sasquach.type.TypeUtils.internalName;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TModuleStruct;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeVariable;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ClassGenerator {
  static final String STRUCT_BASE_INTERNAL_NAME = internalName(StructBase.class);
  static final ClassDesc STRUCT_BASE_CLASS_DESC = classDesc(StructBase.class);
  static final String INSTANCE_FIELD = "INSTANCE";
  private static final int CLASS_VERSION = Opcodes.V19;
  private final Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
  private final ClassWriter classWriter = new SasqClassWriter(generatedClasses,
      ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
  private TypedNode contextNode;

  public Map<String, byte[]> generate(TModuleDeclaration moduleDeclaration) {
    try {
      generateStruct(moduleDeclaration.struct());
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
    return generatedClasses;
  }

  private void setContext(TypedNode node) {
    contextNode = node;
  }

  static MethodTypeDesc constructorTypeDesc(Collection<Type> fieldTypes) {
    var paramDescriptors = fieldTypes.stream().map(Type::classDesc).toArray(ClassDesc[]::new);
    return MethodTypeDesc.of(ConstantDescs.CD_void, paramDescriptors);
  }

  static ClassDesc[] fieldParamDescs(List<? extends TypedNode> fields) {
    return fields.stream().map(TypedNode::type).map(Type::classDesc).toArray(ClassDesc[]::new);
  }

  private void generateStructStart(ClassBuilder clb, ClassDesc structDesc, Range range,
      Map<String, Type> fields) {
    generateStructStart(clb, structDesc, range, fields, List.of());
  }

  private MethodVisitor generateStructStart(String internalName, Range range,
      Map<String, Type> fields) {
    var entries = fields.entrySet()
        .stream()
        .filter(entry -> asFunctionType(entry.getValue()).isEmpty())
        .toList();

    classWriter.visit(
        CLASS_VERSION,
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
        internalName,
        null,
        internalName(Object.class),
        new String[]{STRUCT_BASE_INTERNAL_NAME});
    var sourcePath = range.sourcePath().filepath();
    classWriter.visitSource(sourcePath, null);
    // Generate fields
    for (var entry : entries) {
      var name = entry.getKey();
      var type = entry.getValue();
      var fv = classWriter.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
          name,
          type.classDesc().descriptorString(),
          null,
          null);
      fv.visitEnd();
    }

    // Generate constructor
    var initDescriptor = constructorTypeDesc(entries.stream().map(Entry::getValue).toList());
    var mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC,
        "<init>",
        initDescriptor.descriptorString(),
        null,
        null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    GeneratorUtil.generate(mv, MethodHandleDesc.ofConstructor(classDesc(Object.class)));

    // Set fields in constructor
    int i = 0;
    for (var entry : entries) {
      var name = entry.getKey();
      var type = entry.getValue();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      ExpressionGenerator.generateLoadVar(mv, type, i + 1);
      mv.visitFieldInsn(Opcodes.PUTFIELD, internalName, name, type.classDesc().descriptorString());
      i++;
    }
    mv.visitInsn(Opcodes.RETURN);

    return mv;
  }


  private void generateStructStart(ClassBuilder clb, ClassDesc structDesc, Range range,
      Map<String, Type> fields, List<ClassDesc> interfaceDescs) {
    var entries = fields.entrySet()
        .stream()
        .filter(entry -> asFunctionType(entry.getValue()).isEmpty())
        .toList();

    var allInterfaces = new ArrayList<ClassDesc>(interfaceDescs.size() + 1);
    allInterfaces.add(STRUCT_BASE_CLASS_DESC);
    allInterfaces.addAll(interfaceDescs);

    var sourcePath = range.sourcePath().filepath();
    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL)
        .withInterfaceSymbols(allInterfaces)
        .with(SourceFileAttribute.of(sourcePath));

    // Generate fields
    var fieldClasses = new ArrayList<ClassDesc>();
    for (var entry : entries) {
      var name = entry.getKey();
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
            var fieldName = entry.getKey();
            var fieldType = entry.getValue().classDesc();
            cob.aload(0)
                .loadInstruction(TypeKind.from(fieldType), slot++)
                .putfield(structDesc, fieldName, fieldType);
          }
          cob.return_();
        });
  }

  void generateSumType(SumType sumType) {
    buildAddClass(sumType, cob -> generateSumType(cob, sumType));
  }

  private void buildAddClass(Type type, Consumer<? super ClassBuilder> handler) {
     var bytes = buildClass(type, handler);
     generatedClasses.put(type.internalName().replace('/', '.'), bytes);
  }

  private static byte[] buildClass(Type type, Consumer<? super ClassBuilder> handler) {
    var bytes = ClassFile.of().build(internalClassDesc(type), clb -> {
      clb.withVersion(ClassFile.JAVA_19_VERSION, 0);
      handler.accept(clb);
    });
    ClassFile.of().verify(bytes).stream().findFirst().ifPresent(err -> {
      throw err;
    });
    return bytes;
  }

  // Generate an interface to act as the parent to the sum type variants
  void generateSumType(ClassBuilder clb, SumType sumType) {
    var permittedSubclassDescs = sumType.types()
        .stream()
        .map(type -> ClassDesc.ofInternalName(type.internalName()))
        .toList();
    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.ABSTRACT, AccessFlag.INTERFACE)
        .withInterfaceSymbols(STRUCT_BASE_CLASS_DESC)
        .with(PermittedSubclassesAttribute.ofSymbols(permittedSubclassDescs));
  }

  void generateSingleton(SingletonType singleton, SumType sumType, Range range) {
    buildAddClass(singleton, clb -> generateSingleton(clb, singleton, sumType, range));
  }

  void generateSingleton(ClassBuilder clb, SingletonType singleton, SumType sumType, Range range) {
    var structDesc = ClassDesc.ofInternalName(singleton.internalName());
    generateStructStart(clb, structDesc, range, Map.of(), List.of(internalClassDesc(sumType)));

    generateStaticInstance(clb, singleton.classDesc(),
        structDesc,
        cob -> {
          cob.new_(structDesc)
              .dup();
          var constructorDesc = MethodHandleDesc.ofConstructor(structDesc);
          GeneratorUtil.generate(cob, constructorDesc);
        });
  }

  void generateVariantStruct(StructType structType, SumType sumType, Range range) {
    buildAddClass(structType, clb -> generateVariantStruct(clb, structType, sumType, range));
  }

  void generateVariantStruct(ClassBuilder clb, StructType structType, SumType sumType, Range range) {
    generateStructStart(clb,
        internalClassDesc(structType),
        range,
        structType.fieldTypes(),
        List.of(internalClassDesc(sumType)));
  }

  void generateStruct(TStruct struct) {
    setContext(struct);
    // Generate class header
    var structType = struct.structType();
    var internalName = structType.internalName();
    var mv = generateStructStart(internalName,
        struct.range(),
        structType.fieldTypes());

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct instanceof TModuleStruct moduleStruct) {
      moduleStruct.typeAliases()
          .stream()
          .map(TypeAlias::typeNode)
          .flatMap(type -> type instanceof SumTypeNode sumTypeNode ? Stream.of(sumTypeNode)
              : Stream.empty())
          .forEach(sumTypeNode -> {
            var sumType = sumTypeNode.type();
            var sumTypeGenerator = new ClassGenerator();
            sumTypeGenerator.generateSumType(sumType);
            generatedClasses.putAll(sumTypeGenerator.getGeneratedClasses());
            sumTypeNode.variantTypeNodes().forEach(variantTypeNode -> {
              var variantGenerator = new ClassGenerator();
              switch (variantTypeNode.type()) {
                case StructType variantStructType -> variantGenerator.generateVariantStruct(
                    variantStructType,
                    sumType,
                    variantTypeNode.range());
                case SingletonType singletonType ->
                    variantGenerator.generateSingleton(singletonType,
                        sumType,
                        variantTypeNode.range());
              }
              generatedClasses.putAll(variantGenerator.getGeneratedClasses());
            });
          });

      generateStaticInstance(structType.classDesc().descriptorString(),
          internalName,
          methodVisitor -> new ExpressionGenerator(methodVisitor, List.of()).generateStructInit(
              struct));
    }

    // Generate methods
    for (var function : struct.functions()) {
      setContext(function);
      generateFunction(function.name(), function.function());
    }

    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
    generatedClasses.put(internalName.replace('/', '.'), classWriter.toByteArray());
  }

  void generateStruct(ClassBuilder clb, TStruct struct) {
    setContext(struct);
    // Generate class header
    var structType = struct.structType();
    var structDesc = internalClassDesc(structType);
    generateStructStart(clb, structDesc, struct.range(), structType.fieldTypes());

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct instanceof TModuleStruct moduleStruct) {
      moduleStruct.typeAliases()
          .stream()
          .map(TypeAlias::typeNode)
          .flatMap(type -> type instanceof SumTypeNode sumTypeNode ? Stream.of(sumTypeNode)
              : Stream.empty())
          .forEach(sumTypeNode -> {
            var sumType = sumTypeNode.type();
            var sumTypeGenerator = new ClassGenerator();
            sumTypeGenerator.generateSumType(sumType);
            generatedClasses.putAll(sumTypeGenerator.getGeneratedClasses());
            sumTypeNode.variantTypeNodes().forEach(variantTypeNode -> {
              var variantGenerator = new ClassGenerator();
              switch (variantTypeNode.type()) {
                case StructType variantStructType -> variantGenerator.generateVariantStruct(
                    variantStructType,
                    sumType,
                    variantTypeNode.range());
                case SingletonType singletonType ->
                    variantGenerator.generateSingleton(singletonType,
                        sumType,
                        variantTypeNode.range());
              }
              generatedClasses.putAll(variantGenerator.getGeneratedClasses());
            });
          });

      generateStaticInstance(structType.classDesc().descriptorString(),
          structType.internalName(),
          methodVisitor -> new ExpressionGenerator(methodVisitor, List.of()).generateStructInit(
              struct));
    }

    // Generate methods
    for (var function : struct.functions()) {
      setContext(function);
      generateFunction(function.name(), function.function());
    }

    // Class footer
    classWriter.visitEnd();
  }

  void generateStaticInstance(String descriptor, String ownerInternalName,
      Consumer<MethodVisitor> structInitGenerator) {
    // Generate INSTANCE field
    var field = classWriter.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC,
        INSTANCE_FIELD,
        descriptor,
        null,
        null);
    field.visitEnd();

    // Initialize INSTANCE with field expressions
    var smv = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    smv.visitCode();
    structInitGenerator.accept(smv);
    smv.visitFieldInsn(Opcodes.PUTSTATIC, ownerInternalName, INSTANCE_FIELD, descriptor);
    smv.visitInsn(Opcodes.RETURN);

    // Method footer
    smv.visitMaxs(-1, -1);
    smv.visitEnd();
  }

  void generateStaticInstance(ClassBuilder clb, ClassDesc fieldClassDesc, ClassDesc ownerClassDesc,
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

  String generateFunctionStruct(TFunction function, List<TVarReference> captures) {
    // Generate class header
    var name = "Lambda$" + Integer.toHexString(function.hashCode());
    var captureTypes = captures.stream().collect(toMap(TVarReference::name, TypedNode::type));
    var mv = generateStructStart(name, function.range(), captureTypes);

    // Generate methods
    generateFunction("_invoke", function);

    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
    generatedClasses.put(name.replace('/', '.'), classWriter.toByteArray());
    return name;
  }

  static String signatureDescriptor(Type type) {
    return switch (type) {
      case TypeVariable typeVar -> "T" + typeVar.typeName() + ";";
      case BuiltinType builtinType when builtinType == BuiltinType.VOID -> null;
      default -> type.classDesc().descriptorString();
    };
  }

  private void generateFunction(String funcName, TFunction function) {
    var funcType = function.type();
    String signature = null;
    if (!funcType.typeParameters().isEmpty()) {
      signature = funcType.typeParameters()
          .stream()
          .map(typeParameter -> typeParameter.typeName() + ":Ljava/lang/Object;")
          .collect(joining("", "<", ">")) + funcType.parameterTypes()
          .stream()
          .map(ClassGenerator::signatureDescriptor)
          .collect(joining("", "(", ")")) + signatureDescriptor(funcType.returnType());
    }
    var methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
        funcName,
        funcType.functionDesc().descriptorString(),
        signature,
        null);
    var lineNum = function.range().start().line();
    methodVisitor.visitLineNumber(lineNum, new Label());
    methodVisitor.visitCode();
    var exprGenerator = new ExpressionGenerator(methodVisitor, function.parameters());
    var returnExpr = function.expression();
    if (returnExpr != null) {
      exprGenerator.generateExpr(returnExpr);
      var type = asType(BuiltinType.class, returnExpr.type());
      if (type.isPresent()) {
        int opcode = switch (type.get()) {
          case BOOLEAN, INT, CHAR, BYTE, SHORT -> Opcodes.IRETURN;
          case LONG -> Opcodes.LRETURN;
          case FLOAT -> Opcodes.FRETURN;
          case DOUBLE -> Opcodes.DRETURN;
          case STRING, STRING_ARR -> Opcodes.ARETURN;
          case VOID -> Opcodes.RETURN;
        };
        methodVisitor.visitInsn(opcode);
      } else {
        methodVisitor.visitInsn(Opcodes.ARETURN);
      }
    } else {
      methodVisitor.visitInsn(Opcodes.RETURN);
    }
    methodVisitor.visitMaxs(-1, -1);
    methodVisitor.visitEnd();
    generatedClasses.putAll(exprGenerator.getGeneratedClasses());
  }

  public Map<String, byte[]> getGeneratedClasses() {
    return generatedClasses;
  }
}
