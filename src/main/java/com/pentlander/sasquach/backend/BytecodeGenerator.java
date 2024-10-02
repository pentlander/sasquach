package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedNode;
import java.util.Collection;
import java.util.LinkedHashMap;
import org.jspecify.annotations.Nullable;

public class BytecodeGenerator {

  public BytecodeGenerator() {
  }

  public BytecodeResult generateBytecode(Collection<TModuleDeclaration> moduleDeclarations) {
    var generatedBytecode = new LinkedHashMap<String, byte[]>();

    var tupleClassGen = new ClassGenerator();
    generatedBytecode.putAll(tupleClassGen.generateTuples());
    for (var moduleDeclaration : moduleDeclarations) {
      var classGen = new ClassGenerator();
      generatedBytecode.putAll(classGen.generate(moduleDeclaration));
    }

    return new BytecodeResult(generatedBytecode);
  }

  static class CodeGenerationException extends RuntimeException {
    CodeGenerationException(Node node, Exception cause) {
      super(node.toPrettyString(), cause);
    }

    CodeGenerationException(@Nullable TypedNode node, Exception cause) {
      super(node != null ? limitString(node.toPrettyString(), 100) : null, cause);
    }

    private static String limitString(String str, int maxLength) {
      return str.length() < maxLength ? str : str.substring(0, maxLength) + "...";
    }
  }
}
