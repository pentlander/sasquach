package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.id.QualifiedModuleId;

public interface ModuleTypeProvider {
  StructType getModuleType(QualifiedModuleId qualifiedModuleId);
}
