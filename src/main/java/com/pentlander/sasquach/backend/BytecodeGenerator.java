package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.tast.TModuleDeclaration;
import com.pentlander.sasquach.tast.TypedNode;
import java.util.Collection;
import java.util.HashMap;
import org.objectweb.asm.Opcodes;

public class BytecodeGenerator implements Opcodes {

  public BytecodeGenerator() {
  }

  public BytecodeResult generateBytecode(Collection<TModuleDeclaration> moduleDeclarations) {
    var generatedBytecode = new HashMap<String, byte[]>();
    for (var moduleDeclaration : moduleDeclarations) {
      var classGen = new ClassGenerator();
      classGen.generate(moduleDeclaration)
          .forEach((name, cw) -> generatedBytecode.put(name, cw.toByteArray()));
    }

    return new BytecodeResult(generatedBytecode);
  }

  static class CodeGenerationException extends RuntimeException {
    CodeGenerationException(Node node, Exception cause) {
      super(node.toPrettyString(), cause);
    }

    CodeGenerationException(TypedNode node, Exception cause) {
      super(node.toPrettyString(), cause);
    }
  }
}
