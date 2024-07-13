package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.type.Type;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class GeneratorUtil {
  private GeneratorUtil() {
  }

  public static ClassDesc classDesc(Class<?> clazz) {
    return clazz.describeConstable().orElseThrow();
  }

  public static ClassDesc internalClassDesc(Type type) {
    return ClassDesc.ofInternalName(type.internalName());
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
}
