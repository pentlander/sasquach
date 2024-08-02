package com.pentlander.sasquach.backend;

import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.HashSet;
import java.util.Set;

public final class SasqClassHierarchyResolver implements ClassHierarchyResolver {
  private final Set<ClassDesc> sumTypeDescs = new HashSet<>();

  public void addSumType(ClassDesc classDesc) {
    sumTypeDescs.add(classDesc);
  }

  @Override
  public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
    if (sumTypeDescs.contains(classDesc)) {
      return ClassHierarchyInfo.ofInterface();
    }
    return ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object);
  }
}
