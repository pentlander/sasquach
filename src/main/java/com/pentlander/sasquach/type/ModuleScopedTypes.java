package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.LocalVariable;
import com.pentlander.sasquach.ast.expression.VarReference;

public interface ModuleScopedTypes {

  FuncCallType getFunctionCallType(LocalFunctionCall funcCall);

  VarRefType getVarReferenceType(VarReference varRef);

  sealed interface FuncCallType {
    record Module(FunctionType type) implements FuncCallType {}

    record LocalVar(LocalVariable localVariable) implements FuncCallType {}
  }

  sealed interface VarRefType {
    record Module(Type type) implements VarRefType {
    }

    record LocalVar(LocalVariable localVariable) implements VarRefType {}
  }
}
