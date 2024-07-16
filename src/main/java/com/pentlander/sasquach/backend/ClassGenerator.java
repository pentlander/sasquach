package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.backend.GeneratorUtil.internalClassDesc;
import static com.pentlander.sasquach.type.TypeUtils.asFunctionType;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
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
import java.lang.classfile.ClassFile.ClassHierarchyResolverOption;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.components.ClassPrinter;
import java.lang.classfile.components.ClassPrinter.Verbosity;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

class ClassGenerator {
  static final ClassDesc CD_STRUCT_BASE = classDesc(StructBase.class);
  static final String INSTANCE_FIELD = "INSTANCE";
  private final Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
  private final SasqClassHierarchyResolver resolver = new SasqClassHierarchyResolver();
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

  static ClassDesc[] fieldParamDescs(List<? extends TypedNode> fields) {
    return fields.stream().map(TypedNode::type).map(Type::classDesc).toArray(ClassDesc[]::new);
  }

  private void generateStructStart(ClassBuilder clb, ClassDesc structDesc, Range range,
      Map<String, Type> fields) {
    generateStructStart(clb, structDesc, range, fields, List.of());
  }

  private void generateStructStart(ClassBuilder clb, ClassDesc structDesc, Range range,
      Map<String, Type> fields, List<ClassDesc> interfaceDescs) {
    var entries = fields.entrySet()
        .stream()
        .filter(entry -> asFunctionType(entry.getValue()).isEmpty())
        .toList();

    var allInterfaces = new ArrayList<ClassDesc>(interfaceDescs.size() + 1);
    allInterfaces.add(CD_STRUCT_BASE);
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

  private void buildAddClass(String className, ClassDesc type, Consumer<? super ClassBuilder> handler) {
     var bytes = buildClass(type, handler);
     generatedClasses.put(className.replace('/', '.'), bytes);
  }

  private void buildAddClass(Type type, Consumer<? super ClassBuilder> handler) {
    buildAddClass(type.internalName(), internalClassDesc(type), handler);
  }

  private static byte[] buildClass(ClassDesc type, Consumer<? super ClassBuilder> handler) {
    var opt = ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver()
        .orElse(new SasqClassHierarchyResolver()));
    var classFile = ClassFile.of(opt);
    var bytes = classFile.build(type, clb -> {
      clb.withVersion(ClassFile.JAVA_19_VERSION, 0);
      handler.accept(clb);
    });
    classFile.verify(bytes).stream().findFirst().ifPresent(err -> {
      ClassPrinter.toJson(classFile.parse(bytes), Verbosity.CRITICAL_ATTRIBUTES, System.err::printf);
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
        .withInterfaceSymbols(CD_STRUCT_BASE)
        .with(PermittedSubclassesAttribute.ofSymbols(permittedSubclassDescs));
    resolver.addSumType(internalClassDesc(sumType));
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
    buildAddClass(struct.type(), clb -> generateStruct(clb, struct));
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

      generateStaticInstance(clb, structType.classDesc(),
          internalClassDesc(structType),
          cob -> new ExpressionGenerator(cob, List.of()).generateStructInit(
              struct));
    }

    // Generate methods
    for (var function : struct.functions()) {
      setContext(function);
      generateFunction(clb, function.name(), function.function());
    }
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
    var name = "Lambda$" + Integer.toHexString(function.hashCode());
    var structDesc = ClassDesc.of(name);
    buildAddClass(name, structDesc, clb -> generateFunctionStruct(clb, structDesc, function, captures));
    return name;
  }

  void generateFunctionStruct(ClassBuilder clb, ClassDesc structDesc, TFunction function, List<TVarReference> captures) {
    // Generate class header
    var captureTypes = captures.stream().collect(toMap(TVarReference::name, TypedNode::type));
    generateStructStart(clb, structDesc, function.range(), captureTypes);

    // Generate methods
    generateFunction(clb, "_invoke", function);
  }

  static String signatureDescriptor(Type type) {
    return switch (type) {
      case TypeVariable typeVar -> "T" + typeVar.typeName() + ";";
      case BuiltinType builtinType when builtinType == BuiltinType.VOID -> null;
      default -> type.classDesc().descriptorString();
    };
  }

  private void generateFunction(ClassBuilder clb, String funcName, TFunction function) {
    var funcType = function.type();
    MethodSignature signature;
    if (!funcType.typeParameters().isEmpty()) {
      var sigStr = funcType.typeParameters()
          .stream()
          .map(typeParameter -> typeParameter.typeName() + ":Ljava/lang/Object;")
          .collect(joining("", "<", ">")) + funcType.parameterTypes()
          .stream()
          .map(ClassGenerator::signatureDescriptor)
          .collect(joining("", "(", ")")) + signatureDescriptor(funcType.returnType());
      signature = MethodSignature.parseFrom(sigStr);
    } else
      signature = null;

    clb.withMethod(funcName, funcType.functionDesc(), ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC, mb -> {
      if (signature != null) {
        mb.with(SignatureAttribute.of(signature));
      }

      mb.withCode(cob -> {
        var lineNum = function.range().start().line();
        cob.lineNumber(lineNum);
        var exprGenerator = new ExpressionGenerator(cob, function.parameters());
        var returnExpr = function.expression();
        if (returnExpr != null) {
          exprGenerator.generateExpr(returnExpr);
          cob.returnInstruction(TypeKind.from(returnExpr.type().classDesc()));
        } else {
          cob.return_();
        }
        generatedClasses.putAll(exprGenerator.getGeneratedClasses());
      });
    });
  }

  public Map<String, byte[]> getGeneratedClasses() {
    return generatedClasses;
  }
}
