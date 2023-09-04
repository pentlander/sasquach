package com.pentlander.sasquach.backend;

import static org.objectweb.asm.Type.getType;

import java.util.Map;
import org.objectweb.asm.ClassWriter;

public class SasqClassWriter extends ClassWriter {
  private final Map<String, ?> classMap;

  public SasqClassWriter(Map<String, ?> classMap, int flags) {
    super(flags);
    this.classMap = classMap;
  }

  @Override
  protected String getCommonSuperClass(String type1, String type2) {
    if (classMap.containsKey(type1.replace('/', '.')) || classMap.containsKey(type2.replace('/',
        '.'))) {
      return getType(Object.class).getInternalName();
    }
    return super.getCommonSuperClass(type1, type2);
  }
}
