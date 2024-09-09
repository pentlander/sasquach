package com.pentlander.sasquach.backend;

import static com.pentlander.sasquach.type.TypeUtils.classDesc;

import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import jdk.dynalink.linker.support.TypeUtilities;

public final class GeneratorUtil {
  private GeneratorUtil() {
  }

  public static ClassDesc internalClassDesc(Type type) {
    return ClassDesc.ofInternalName(type.internalName());
  }

  public static void generate(CodeBuilder cob, DirectMethodHandleDesc methodHandleDesc) {
    var kind = methodHandleDesc.kind();
    var isField = switch (kind) {
      case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> true;
      default -> false;
    };
    var opCode = switch (kind) {
      case STATIC, INTERFACE_STATIC -> Opcode.INVOKESTATIC;
      case VIRTUAL -> Opcode.INVOKEVIRTUAL;
      case INTERFACE_VIRTUAL -> Opcode.INVOKEINTERFACE;
      case SPECIAL, CONSTRUCTOR, INTERFACE_SPECIAL -> Opcode.INVOKESPECIAL;
      case GETTER -> Opcode.GETFIELD;
      case SETTER -> Opcode.PUTFIELD;
      case STATIC_GETTER -> Opcode.GETSTATIC;
      case STATIC_SETTER -> Opcode.PUTSTATIC;
    };

    if (isField) {
      cob.fieldInstruction(
          opCode,
          methodHandleDesc.owner(),
          methodHandleDesc.methodName(),
          ClassDesc.ofDescriptor(methodHandleDesc.lookupDescriptor()));
    } else {
      cob.invokeInstruction(
          opCode,
          methodHandleDesc.owner(),
          methodHandleDesc.methodName(),
          MethodTypeDesc.ofDescriptor(methodHandleDesc.lookupDescriptor()),
          methodHandleDesc.isOwnerInterface()
          );
    }
  }

  static String internalName(ClassDesc classDesc) {
    if (classDesc.isPrimitive()) {
      throw new IllegalArgumentException(classDesc.descriptorString());
    } else if (classDesc.isClassOrInterface()) {
      var descStr = classDesc.descriptorString();
      return descStr.substring(1, descStr.length() - 1);
    } else {
      throw new IllegalStateException(classDesc.descriptorString());
    }
  }

  static void box(CodeBuilder cob, Type type) {
    if (type instanceof BuiltinType builtinType) {
      switch (builtinType) {
        case BOOLEAN, INT, CHAR, BYTE, SHORT, LONG, FLOAT, DOUBLE -> {
          var wrapperTypeDesc = classDesc(TypeUtilities.getWrapperType(builtinType.typeClass()));
          var methodTypeDesc = MethodTypeDesc.of(wrapperTypeDesc, builtinType.classDesc());
          cob.invokestatic(wrapperTypeDesc, "valueOf",  methodTypeDesc);
        }
        default -> {}
      }
    }
  }

  /**
   * Convert a primitive type into its boxed type.
   * <p>This method should be used when providing a primitive to a function call with type
   * parameters, as the parameter type is {@link Object}.</p>
   */
  static void tryBox(CodeBuilder cob, Type actualType, Type expectedType) {
    var expectedTypeKind = TypeKind.from(expectedType.classDesc());
    if (expectedTypeKind.equals(TypeKind.ReferenceType)) {
      box(cob, actualType);
    }
  }

  static void generateLoadVar(CodeBuilder cob, Type type, int idx) {
    if (idx < 0) {
      return;
    }

    var typeKind = TypeKind.from(type.classDesc());
    if (typeKind != TypeKind.VoidType) {
      cob.loadInstruction(typeKind, idx);
    } else {
      cob.aconst_null();
    }
  }
}
