package com.pentlander.sasquach.backend;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TLocalVarMeta {
  private final Deque<TVarMeta> varMetas = new ArrayDeque<>();
  private final Map<Id, TVarMeta> varToMeta = new HashMap<>();
  private int count = 0;

  public static TLocalVarMeta of(List<? extends TLocalVariable> localVars, ExpressionGenerator.Context context) {
    var localVarMeta = new TLocalVarMeta();
    localVarMeta.count = switch (context) {
      case INIT, ANON_FUNC -> 0;
      case NAMED_FUNC -> 1;

    };
    for (var localVar : localVars) {
      localVarMeta.push(localVar);
    }

    return localVarMeta;
  }

  TVarMeta push(TLocalVariable localVar) {
    var meta = new TVarMeta(localVar, count++);
    varMetas.push(meta);
    varToMeta.put(localVar.id(), meta);
    return meta;
  }

  int pushHidden() {
    return count++;
  }

  TVarMeta get(TLocalVariable localVar) {
    return requireNonNull(varToMeta.get(localVar.id()));
  }

  Collection<TVarMeta> varMeta() {
    return varMetas;
  }

  record TVarMeta(TLocalVariable localVar, int idx) {}
}
