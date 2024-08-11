package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.Util.seqMap;
import static com.pentlander.sasquach.backend.GeneratorUtil.internalClassDesc;
import static com.pentlander.sasquach.type.TypeUtils.classDesc;
import static java.lang.classfile.Signature.ClassTypeSig;
import static java.lang.classfile.Signature.TypeVarSig;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.backend.AnonFunctions.NamedAnonFunc;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.backend.ExpressionGenerator.Context;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedNode;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TModuleStruct;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.UniversalType;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFile.ClassHierarchyResolverOption;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
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
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

class ClassGenerator {
  static final ClassDesc CD_STRUCT_BASE = classDesc(StructBase.class);
  static final String INSTANCE_FIELD = "INSTANCE";
  private final Map<String, byte[]> generatedClasses = new LinkedHashMap<>();
  private final SasqClassHierarchyResolver resolver = new SasqClassHierarchyResolver();
  @Nullable private TypedNode contextNode;

  public Map<String, byte[]> generate(TModuleDeclaration moduleDeclaration) {
    try {
      generateStruct(moduleDeclaration.struct());
    } catch (RuntimeException e) {
      throw new CodeGenerationException(contextNode, e);
    }
    return generatedClasses;
  }

  public Map<String, byte[]> generate(TStruct struct) {
    generateStruct(struct);
    return generatedClasses;
  }

  private void setContext(TypedNode node) {
    contextNode = node;
  }

  private void generateStructStart(ClassBuilder clb, ClassDesc structDesc, Range range,
      SequencedMap<UnqualifiedName, Type> fields, ClassDesc... interfaceDescs) {
    var entries = List.copyOf(fields.entrySet());

    var allInterfaces = new ArrayList<ClassDesc>(interfaceDescs.length + 1);
    allInterfaces.add(CD_STRUCT_BASE);
    allInterfaces.addAll(Arrays.asList(interfaceDescs));

    var sourcePath = range.sourcePath().filepath();
    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL)
        .withInterfaceSymbols(allInterfaces)
        .with(SourceFileAttribute.of(sourcePath));

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

  private static byte[] buildClass(ClassDesc type, Consumer<? super ClassBuilder> handler) {
    var opt = ClassHierarchyResolverOption.of(ClassHierarchyResolver.defaultResolver()
        .orElse(new SasqClassHierarchyResolver()));
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
        .map(GeneratorUtil::internalClassDesc)
        .toList();
    clb.withFlags(AccessFlag.PUBLIC, AccessFlag.ABSTRACT, AccessFlag.INTERFACE)
        .withInterfaceSymbols(CD_STRUCT_BASE)
        .with(PermittedSubclassesAttribute.ofSymbols(permittedSubclassDescs));
    resolver.addSumType(internalClassDesc(sumType));
  }

  private void generateSingleton(SingletonType singleton, SumType sumType, Range range) {
    buildAddClass(singleton, clb -> generateSingleton(clb, singleton, sumType, range));
  }

  private void generateSingleton(ClassBuilder clb, SingletonType singleton, SumType sumType, Range range) {
    var structDesc = internalClassDesc(singleton);
    generateStructStart(clb, structDesc, range, seqMap(), internalClassDesc(sumType));

    generateStaticInstance(clb, singleton.classDesc(),
        structDesc,
        cob -> {
          cob.new_(structDesc)
              .dup();
          var constructorDesc = MethodHandleDesc.ofConstructor(structDesc);
          GeneratorUtil.generate(cob, constructorDesc);
        });
  }

  private void generateVariantStruct(StructType structType, SumType sumType, Range range) {
    buildAddClass(structType, clb -> generateVariantStruct(clb, structType, sumType, range));
  }

  private void generateVariantStruct(ClassBuilder clb, StructType structType, SumType sumType, Range range) {
    generateStructStart(
        clb,
        internalClassDesc(structType),
        range,
        structType.fieldTypes(),
        internalClassDesc(sumType));
  }

  private void generateStruct(TStruct struct) {
    buildAddClass(struct.type(), clb -> generateStruct(clb, struct));
  }

  /** Generate classes for the named structs defined in the type aliases. */
  private void generateNamedTypes(List<TypeAlias> typeAliases) {
    for (var typeAlias : typeAliases) {
      if (typeAlias.typeNode() instanceof SumTypeNode sumTypeNode) {
          var sumType = sumTypeNode.type();
          generateSumType(sumType);
          sumTypeNode.variantTypeNodes().forEach(variantTypeNode -> {
            switch (variantTypeNode.type()) {
              case StructType variantStructType ->
                  generateVariantStruct(variantStructType, sumType, variantTypeNode.range());
              case SingletonType singletonType ->
                  generateSingleton(singletonType, sumType, variantTypeNode.range());
            }
          });
      }
    }
  }

  private void generateStruct(ClassBuilder clb, TStruct struct) {
    setContext(struct);
    // Generate class header
    var structType = struct.structType();
    var structDesc = internalClassDesc(structType);

    var fieldTypes = new LinkedHashMap<UnqualifiedName, Type>();
    struct.fields().forEach(field -> fieldTypes.put(field.name(), field.type()));

    generateStructStart(clb, structDesc, struct.range(), fieldTypes);

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct instanceof TModuleStruct moduleStruct) {
      generateNamedTypes(moduleStruct.typeAliases());
      generateStaticInstance(clb, structType.classDesc(),
          internalClassDesc(structType),
          cob -> new ExpressionGenerator(cob, Context.INIT, "modInit", List.of()).generateStructInit(
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
    generateFunction(clb, funcName, function, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL);
  }

  public Signature typeSignature(Type type) {
    return switch (type) {
      case UniversalType t -> TypeVarSig.of(t.name());
      case BuiltinType t when t != BuiltinType.STRING && t != BuiltinType.STRING_ARR ->
          BaseTypeSig.of(t.classDesc());
      default -> ClassTypeSig.of(type.classDesc());
    };
  }

  private void generateFunction(ClassBuilder clb, String funcName, TFunction function,
      int methodFlags) {
    var funcType = function.typeWithCaptures();
    MethodSignature signature;
    if (!funcType.typeParameters().isEmpty()) {
      var typeParams = funcType.typeParameters()
          .stream()
          .map(typeParam -> TypeParam.of(typeParam.typeNameStr(),
              ClassTypeSig.of(ConstantDescs.CD_Object)))
          .toList();
      var paramTypeSigs = funcType.parameterTypes()
          .stream()
          .map(this::typeSignature)
          .toArray(Signature[]::new);
      signature = MethodSignature.of(typeParams,
          List.of(),
          typeSignature(funcType.returnType()),
          paramTypeSigs);
    } else {
      signature = null;
    }

    var namedAnonFuncs = new ArrayList<NamedAnonFunc>();
    clb.withMethod(funcName, funcType.functionTypeDesc(), methodFlags, mb -> {
      if (signature != null) {
        mb.with(SignatureAttribute.of(signature));
      }

      mb.withCode(cob -> {
        var lineNum = function.range().start().line();
        cob.lineNumber(lineNum);

        var captures = function.captures().stream().map(localVar -> new TFunctionParameter(localVar.id(), localVar.variableType(),
            localVar.range()));
        var params = Stream.concat(captures, function.parameters().stream()).toList();

        var context = Context.NAMED_FUNC;
        if ((methodFlags & ClassFile.ACC_STATIC) != 0) {
          context = Context.ANON_FUNC;
        }
        var exprGenerator = new ExpressionGenerator(cob, context, funcName, params).initParams();
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
        ClassFile.ACC_PUBLIC + ClassFile.ACC_STATIC + ClassFile.ACC_SYNTHETIC));
  }
}
