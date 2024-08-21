package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.QualifiedModuleId;

public interface ModuleTypeProvider {
  StructType getModuleType(QualifiedModuleId qualifiedModuleId);
}
