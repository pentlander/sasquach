package com.pentlander.sasquach.backend;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.SumTypeNode;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.name.NameResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.SumType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
import com.pentlander.sasquach.type.TypeUtils;
import com.pentlander.sasquach.type.TypeVariable;
import com.pentlander.sasquach.type.VariantType;
import java.util.Collection;
import java.util.HashMap;
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
  static final String STRUCT_BASE_INTERNAL_NAME = new ClassType(StructBase.class).internalName();
  static final String INSTANCE_FIELD = "INSTANCE";
  private static final int CLASS_VERSION = Opcodes.V16;
  private final ClassWriter classWriter = new ClassWriter(
      ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
  private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
  private final NameResolutionResult nameResolutionResult;
  private final TypeFetcher typeFetcher;
  private Node contextNode;

  ClassGenerator(NameResolutionResult nameResolutionResult, TypeFetcher typeFetcher) {
    this.nameResolutionResult = nameResolutionResult;
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

  private void setContext(Node node) {
    contextNode = node;
  }

  private Type type(Expression expression) {
    return typeFetcher.getType(expression);
  }

  private Type type(Identifier identifier) {
    return typeFetcher.getType(identifier);
  }

  static String constructorType(Collection<Type> fieldTypes) {
    String paramDescriptor = fieldTypes.stream().map(Type::descriptor)
        .collect(joining("", "(", ")"));
    return paramDescriptor + BuiltinType.VOID.descriptor();
  }

  static String constructorType(List<? extends Expression> fields, TypeFetcher typeFetcher) {
    String paramDescriptor = fields.stream().map(typeFetcher::getType).map(Type::descriptor)
        .collect(joining("", "(", ")"));
    return paramDescriptor + BuiltinType.VOID.descriptor();
  }

  private MethodVisitor generateStructStart(String internalName, Range range,
      Map<String, Type> fields) {
    var entries = fields.entrySet().stream()
        .filter(entry -> !(entry.getValue() instanceof FunctionType)).toList();
    generatedClasses.put(internalName.replace('/', '.'), classWriter);
    classWriter.visit(CLASS_VERSION,
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
        internalName,
        null,
        "java/lang/Object",
        new String[]{STRUCT_BASE_INTERNAL_NAME});
    var sourcePath = range.sourcePath().filepath();
    classWriter.visitSource(sourcePath, null);
    // Generate fields
    for (var entry : entries) {
      var name = entry.getKey();
      var type = entry.getValue();
      var fv = classWriter.visitField(
          Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
          name,
          type.descriptor(),
          null,
          null);
      fv.visitEnd();
    }

    // Generate constructor
    var initDescriptor = constructorType(entries.stream().map(Entry::getValue).toList());
    var mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", initDescriptor, null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
        new ClassType(Object.class).internalName(),
        "<init>",
        "()V",
        false);

    // Set fields in constructor
    int i = 0;
    for (var entry : entries) {
      var name = entry.getKey();
      var type = entry.getValue();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      ExpressionGenerator.generateLoadVar(mv, type, i + 1);
      mv.visitFieldInsn(Opcodes.PUTFIELD, internalName, name, type.descriptor());
      i++;
    }
    mv.visitInsn(Opcodes.RETURN);

    return mv;
  }

  // Generate an interface to act as the parent to the sum type variants
  void generateSumType(SumType sumType) {
    var internalName = sumType.internalName();
    generatedClasses.put(internalName.replace('/', '.'), classWriter);
    classWriter.visit(CLASS_VERSION,
        Opcodes.ACC_PUBLIC +  Opcodes.ACC_ABSTRACT + Opcodes.ACC_INTERFACE,
        internalName,
        null,
        "java/lang/Object",
        new String[]{STRUCT_BASE_INTERNAL_NAME});
//    var sourcePath = range.sourcePath().filepath();
//    classWriter.visitSource(sourcePath, null);
    classWriter.visitEnd();
  }

  void generateSingleton(SingletonType singleton, Range range) {
    var internalName = singleton.internalName();
    var mv = generateStructStart(internalName, range, Map.of());
    mv.visitMaxs(-1, -1);

    generateStaticInstance(singleton.descriptor(), internalName, methodVisitor -> {
      methodVisitor.visitTypeInsn(Opcodes.NEW, internalName);
      methodVisitor.visitInsn(Opcodes.DUP);
      var initDescriptor = constructorType(List.of(), typeFetcher);
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKESPECIAL,
          internalName,
          "<init>",
          initDescriptor,
          false);
    });
    classWriter.visitEnd();
  }

  void generateStruct(StructType structType, Range range) {
    var mv = generateStructStart(structType.internalName(), range, structType.fieldTypes());
    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
  }

  void generateStruct(Struct struct) {
    setContext(struct);
    // Generate class header
    var structType = TypeUtils.asStructType(type(struct)).orElseThrow();
    var mv = generateStructStart(structType.internalName(),
        struct.range(),
        structType.fieldTypes());

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct.structKind() == StructKind.MODULE) {
      struct.typeAliases().stream().map(TypeAlias::typeNode)
          .flatMap(type -> type instanceof SumTypeNode sumTypeNode ? Stream.of(sumTypeNode)
              : Stream.empty()).forEach(sumTypeNode -> {
                var sumType = (SumType) type(sumTypeNode.id());
                var sumTypeGenerator = new ClassGenerator(nameResolutionResult, typeFetcher);
                sumTypeGenerator.generateSumType(sumType);
                generatedClasses.putAll(sumTypeGenerator.getGeneratedClasses());
                sumTypeNode.variantTypeNodes().forEach(variantTypeNode -> {
                  var variantType = (VariantType) type(variantTypeNode.id());
                  var variantGenerator = new ClassGenerator(nameResolutionResult, typeFetcher);
                  switch (variantType) {
                    case StructType variantStructType -> variantGenerator.generateStruct(variantStructType,
                        variantTypeNode.range());
                    case SingletonType singletonType -> variantGenerator.generateSingleton(singletonType,
                        variantTypeNode.range());
                  }
                  generatedClasses.putAll(variantGenerator.getGeneratedClasses());
                });
          });

      generateStaticInstance(type(struct).descriptor(),
          structType.internalName(),
          methodVisitor -> new ExpressionGenerator(methodVisitor,
              nameResolutionResult,
              typeFetcher).generateStructInit(struct));
    }

    // Generate methods
    for (var function : struct.functions()) {
      setContext(function);
      var funcType = TypeUtils.asFunctionType(type(function.id())).orElseThrow();
      generateFunction(function.name(), funcType, function.function());
    }

    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
  }

  void generateStaticInstance(String descriptor, String ownerInternalName,
      Consumer<MethodVisitor> structInitGenerator) {
    // Generate INSTANCE field
    var field = classWriter.visitField(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC,
        INSTANCE_FIELD,
        descriptor,
        null,
        null);
    field.visitEnd();

    // Initialize INSTANCE with field expressions
    var smv = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    smv.visitCode();
    structInitGenerator.accept(smv);
    smv.visitFieldInsn(
        Opcodes.PUTSTATIC,
        ownerInternalName,
        INSTANCE_FIELD,
        descriptor);
    smv.visitInsn(Opcodes.RETURN);

    // Method footer
    smv.visitMaxs(-1, -1);
    smv.visitEnd();
  }

  String generateFunctionStruct(Function function, List<VarReference> captures) {
    // Generate class header
    var name = "Lambda$" + Integer.toHexString(function.hashCode());
    var captureTypes = captures.stream().collect(toMap(VarReference::name, this::type));
    var mv = generateStructStart(name, function.range(), captureTypes);

    // Generate methods
    generateFunction("_invoke", TypeUtils.asFunctionType(type(function)).get(), function);

    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
    return name;
  }

  static String signatureDescriptor(Type type) {
    return switch (type) {
      case TypeVariable typeVar -> "T" + typeVar.typeName() + ";";
      case BuiltinType builtinType && builtinType == BuiltinType.VOID -> null;
      case default -> type.descriptor();
    };
  }

  private void generateFunction(String funcName, FunctionType funcType, Function function) {
    String signature = null;
    if (!funcType.typeParameters().isEmpty()) {
      signature =
          funcType.typeParameters().stream().map(typeParameter -> typeParameter.typeName() + ":Ljava/lang/Object;")
          .collect(joining("", "<", ">")) + funcType.parameterTypes().stream()
          .map(ClassGenerator::signatureDescriptor).collect(joining("", "(", ")")) + signatureDescriptor(
          funcType.returnType());
    }
    var methodVisitor = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
        funcName,
        funcType.funcDescriptor(),
        signature,
        null);
    var lineNum = function.range().start().line();
    methodVisitor.visitLineNumber(lineNum, new Label());
    methodVisitor.visitCode();
    var exprGenerator = new ExpressionGenerator(methodVisitor, nameResolutionResult, typeFetcher);
    var returnExpr = function.expression();
    if (returnExpr != null) {
      exprGenerator.generateExpr(returnExpr);
      Type type = type(returnExpr);
      if (type instanceof BuiltinType builtinType) {
        int opcode = switch (builtinType) {
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

  public Map<String, ClassWriter> getGeneratedClasses() {
    return generatedClasses;
  }
}
