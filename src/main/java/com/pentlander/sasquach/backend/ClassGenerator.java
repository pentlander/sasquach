package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.Struct.Field;
import com.pentlander.sasquach.ast.expression.Struct.StructKind;
import com.pentlander.sasquach.backend.BytecodeGenerator.CodeGenerationException;
import com.pentlander.sasquach.name.ResolutionResult;
import com.pentlander.sasquach.runtime.StructBase;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.ClassType;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeFetcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class ClassGenerator {
  static final String STRUCT_BASE_INTERNAL_NAME = new ClassType(StructBase.class).internalName();
  static final String INSTANCE_FIELD = "INSTANCE";
  private static final int CLASS_VERSION = Opcodes.V16;
  private final ClassWriter classWriter = new ClassWriter(
      ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
  private final Map<String, ClassWriter> generatedClasses = new HashMap<>();
  private final ResolutionResult resolutionResult;
  private final TypeFetcher typeFetcher;
  private Node contextNode;

  ClassGenerator(ResolutionResult resolutionResult, TypeFetcher typeFetcher) {
    this.resolutionResult = resolutionResult;
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

  private void addContextNode(Node node) {
    contextNode = node;
  }

  private Type type(Expression expression) {
    return typeFetcher.getType(expression);
  }

  private Type type(Identifier identifier) {
    return typeFetcher.getType(identifier);
  }

  static FunctionType constructorType(List<Field> fields, TypeFetcher typeFetcher) {
    return new FunctionType(fields.stream().map(typeFetcher::getType).toList(), BuiltinType.VOID);
  }

  void generateStruct(Struct struct) {
    addContextNode(struct);
    // Generate class header
    var structType = type(struct);
    generatedClasses.put(structType.typeName().replace('/', '.'), classWriter);
    classWriter.visit(CLASS_VERSION,
        Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
        structType.internalName(),
        null,
        "java/lang/Object",
        new String[]{STRUCT_BASE_INTERNAL_NAME});
    List<Field> fields = struct.fields();
    // Generate fields
    for (var field : fields) {
      var fv = classWriter.visitField(
          Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
          field.name(),
          type(field).descriptor(),
          null,
          null);
      fv.visitEnd();
    }

    // Generate constructor
    var initDescriptor = constructorType(fields, typeFetcher).descriptor();
    var mv = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", initDescriptor, null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
        new ClassType(Object.class).internalName(),
        "<init>",
        "()V",
        false);

    // Set fields in constructor
    for (int i = 0; i < fields.size(); i++) {
      var field = fields.get(i);
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      ExpressionGenerator.generateLoadVar(mv, type(field), i + 1);
      mv.visitFieldInsn(
          Opcodes.PUTFIELD,
          structType.internalName(),
          field.name(),
          type(field).descriptor());
    }
    mv.visitInsn(Opcodes.RETURN);

    // Add a static INSTANCE field of the struct to make a singleton class.
    if (struct.structKind() == StructKind.MODULE) {
      // Generate INSTANCE field
      var field = classWriter.visitField(
          Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC,
          INSTANCE_FIELD,
          type(struct).descriptor(),
          null,
          null);
      field.visitEnd();

      // Initialize INSTANCE with field expressions
      var smv = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
      smv.visitCode();
      new ExpressionGenerator(smv, resolutionResult, typeFetcher).generateStructInit(struct);
      smv.visitFieldInsn(
          Opcodes.PUTSTATIC,
          structType.internalName(),
          INSTANCE_FIELD,
          type(struct).descriptor());
      smv.visitInsn(Opcodes.RETURN);

      // Method footer
      smv.visitMaxs(-1, -1);
      smv.visitEnd();
    }

    // Generate methods
    for (Function func : struct.functions()) {
      generateFunction(classWriter, func, resolutionResult);
    }

    // Class footer
    mv.visitMaxs(-1, -1);
    classWriter.visitEnd();
  }


  private void generateFunction(ClassWriter classWriter, Function function,
      ResolutionResult memberResolutionResult) {
    addContextNode(function);
    var funcType = type(function.id());
    var methodVisitor = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
        function.name(),
        funcType.descriptor(),
        null,
        null);
    methodVisitor.visitCode();
    var exprGenerator = new ExpressionGenerator(methodVisitor, memberResolutionResult, typeFetcher);
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