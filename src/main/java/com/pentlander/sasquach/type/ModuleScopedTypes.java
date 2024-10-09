package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.StructName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;
import java.util.Map;

public interface ModuleScopedTypes {

  QualifiedModuleName getModuleName();

  StructType getThisType();

  StructName getLiteralStructName(Map<UnqualifiedName, Type> memberTypes);

  StructType getModuleType(QualifiedModuleName moduleName);

  FuncCallType getFunctionCallType(LocalFunctionCall funcCall);

  VarRefType getVarReferenceType(VarReference varRef);

  sealed interface FuncCallType {
    record Module() implements FuncCallType {}

    record LocalVar(LocalVariable localVariable) implements FuncCallType {}
  }

  sealed interface VarRefType {
    record Module(QualifiedModuleId moduleId, StructType type) implements VarRefType {}

    record Singleton() implements VarRefType {}

    record LocalVar(LocalVariable localVariable) implements VarRefType {}
  }
}
