package com.pentlander.sasquach.type;

import com.pentlander.sasquach.name.QualifiedModuleName;

public interface ModuleTypeProvider {
  StructType getModuleType(QualifiedModuleName qualifiedModuleId);
}
