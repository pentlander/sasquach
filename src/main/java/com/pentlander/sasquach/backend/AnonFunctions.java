package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.tast.expression.TFunction;
import java.util.ArrayList;
import java.util.List;

public final class AnonFunctions {
  private final String parentFuncName;
  private final List<TFunction> functions = new ArrayList<>();

  public AnonFunctions(String parentFuncName) {
    this.parentFuncName = parentFuncName;
  }

  private static String funcName(String parentFuncName, int n) {
    return  "lambda$%s$%s".formatted(parentFuncName, n);
  }

  public String add(TFunction func) {
    functions.add(func);
    return funcName(parentFuncName, functions.size() - 1);
  }

  public List<NamedAnonFunc> getFunctions() {
    var namedFuncs = new ArrayList<NamedAnonFunc>();
    for (int i = 0; i < functions.size(); i++) {
      var funcName = funcName(parentFuncName, i);
      namedFuncs.add(new NamedAnonFunc(funcName, functions.get(i)));
    }

    return namedFuncs;
  }

  public record NamedAnonFunc(String name, TFunction function) {}
}
