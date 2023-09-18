package com.pentlander.sasquach.backend;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.ast.expression.LocalVariable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Label;

class LocalVarMeta {
  private final Deque<VarMeta> varMetas = new ArrayDeque<>();
  private final Map<LocalVariable, VarMeta> varToMeta = new HashMap<>();
  private int count = 0;

  public static LocalVarMeta of(List<? extends LocalVariable> localVars) {
    var localVarMeta = new LocalVarMeta();
    for (var localVar : localVars) {
      localVarMeta.push(localVar);
    }

    return localVarMeta;
  }

  VarMeta push(LocalVariable localVar) {
    var meta = new VarMeta(localVar, count++, new Label());
    varMetas.push(meta);
    varToMeta.put(localVar, meta);
    return meta;
  }

  int pushHidden() {
    return count++;
  }

  VarMeta get(LocalVariable localVar) {
    return requireNonNull(varToMeta.get(localVar));
  }

  Collection<VarMeta> varMeta() {
    return varMetas;
  }

  record VarMeta(LocalVariable localVar, int idx, Label label) {}
}
