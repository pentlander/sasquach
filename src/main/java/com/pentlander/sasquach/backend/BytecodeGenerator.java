package com.pentlander.sasquach.backend;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.name.ModuleResolver;
import com.pentlander.sasquach.type.TypeFetcher;
import java.util.HashMap;
import org.objectweb.asm.Opcodes;

public class BytecodeGenerator implements Opcodes {
  private final ModuleResolver moduleResolver;
  private final TypeFetcher typeFetcher;

  public BytecodeGenerator(ModuleResolver moduleResolver, TypeFetcher typeFetcher) {
    this.moduleResolver = moduleResolver;
    this.typeFetcher = typeFetcher;
  }

  public BytecodeResult generateBytecode(CompilationUnit compilationUnit) {
    var generatedBytecode = new HashMap<String, byte[]>();
    for (var moduleDeclaration : compilationUnit.modules()) {
      var moduleScopedResolver = moduleResolver.resolveModule(moduleDeclaration.name());
      var resolutionResult = moduleScopedResolver.resolve();
      var classGen = new ClassGenerator(resolutionResult, typeFetcher);
      classGen.generate(moduleDeclaration)
          .forEach((name, cw) -> generatedBytecode.put(name, cw.toByteArray()));
    }

    return new BytecodeResult(generatedBytecode);
  }

  static class CodeGenerationException extends RuntimeException {
    CodeGenerationException(Node node, Exception cause) {
      super(node.toPrettyString(), cause);
    }
  }
}
