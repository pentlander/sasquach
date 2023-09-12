package com.pentlander.sasquach.backend;

import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class GeneratorUtil {
  private GeneratorUtil() {
  }


  public static void generate(MethodVisitor methodVisitor,
      DirectMethodHandleDesc methodHandleDesc) {
    var kind = methodHandleDesc.kind();
    var isField = switch (kind) {
      case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> true;
      default -> false;
    };
    var opCode = switch (kind) {
      case STATIC, INTERFACE_STATIC -> Opcodes.INVOKESTATIC;
      case VIRTUAL -> Opcodes.INVOKEVIRTUAL;
      case INTERFACE_VIRTUAL -> Opcodes.INVOKEINTERFACE;
      case SPECIAL, CONSTRUCTOR, INTERFACE_SPECIAL -> Opcodes.INVOKESPECIAL;
      case GETTER -> Opcodes.GETFIELD;
      case SETTER -> Opcodes.PUTFIELD;
      case STATIC_GETTER -> Opcodes.GETSTATIC;
      case STATIC_SETTER -> Opcodes.PUTSTATIC;
    };
    var ownerDescriptor = methodHandleDesc.owner().descriptorString();
    var ownerInternalName = ownerDescriptor.substring(1, ownerDescriptor.length() - 1);

    if (isField) {
      methodVisitor.visitFieldInsn(
          opCode,
          ownerInternalName,
          methodHandleDesc.methodName(),
          methodHandleDesc.lookupDescriptor());
    } else {
      methodVisitor.visitMethodInsn(
          opCode,
          ownerInternalName,
          methodHandleDesc.methodName(),
          methodHandleDesc.lookupDescriptor(),
          methodHandleDesc.isOwnerInterface());
    }
  }

  static String internalName(Class<?> clazz) {
    return org.objectweb.asm.Type.getInternalName(clazz);
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
}
