package com.pentlander.sasquach.runtime;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class StructGenerator {
  static byte[] generateDelegateStruct(String packageName, Map<String, ClassDesc> fieldTypes) {
    var structDesc = ClassDesc.of(
        packageName,
        "AnonStruct" + ThreadLocalRandom.current().nextInt());
    var classFileBytes = ClassFile.of().build(structDesc, clb -> {
      clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SYNTHETIC)
          .withInterfaceSymbols(StructBase.class.describeConstable().orElseThrow());

      var fieldClasses = new ArrayList<ClassDesc>();
      fieldTypes.forEach((name, fieldType) -> {
        clb.withField(name, fieldType, ClassFile.ACC_PUBLIC + ClassFile.ACC_FINAL);
        fieldClasses.add(fieldType);
      });

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
            for (var entry : fieldTypes.entrySet()) {
              var fieldName = entry.getKey();
              var fieldType = entry.getValue();
              var typeKind = TypeKind.from(fieldType);
              cob.aload(0)
                  .loadInstruction(typeKind, slot++)
                  .putfield(structDesc, fieldName, fieldType);
            }
            cob.return_();
          });
    });

    var errors = ClassFile.of().verify(classFileBytes);
    if (!errors.isEmpty()) {
      throw errors.getFirst();
    }

    return classFileBytes;
  }
}
