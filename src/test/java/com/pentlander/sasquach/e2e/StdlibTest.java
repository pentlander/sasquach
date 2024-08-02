package com.pentlander.sasquach.e2e;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.CompilationException;
import com.pentlander.sasquach.Compiler;
import com.pentlander.sasquach.SasquachClassloader;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.Sources;
import com.pentlander.sasquach.TestUtils;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.backend.BytecodeResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class StdlibTest {
  @Test
  void test() throws Exception {
    var clazz = compileSource("""
        Main {
           main = (): Int -> 1
        }
        """);

  }


  private Class<?> compileSource(String source)
      throws ClassNotFoundException, CompilationException {
    return compileClass(Source.fromString("main", source), false);
  }

  private Class<?> compileClassDebug(String source)
      throws ClassNotFoundException, CompilationException {
    return compileClass(Source.fromString("main", source), true);
  }

  private Class<?> compileClass(Source source, boolean dumpClasses)
      throws ClassNotFoundException, CompilationException {

    var url = requireNonNull(getClass().getResource("/stdlib"));
    BytecodeResult bytecode;
    try {
      var path = Paths.get(url.toURI());
      var compiler = new Compiler();
      var sources = compiler.findFiles(path).merge(Sources.single(source));
      bytecode = compiler.compile(sources);
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }

    var cl = new SasquachClassloader();
    if (dumpClasses) {
      TestUtils.dumpGeneratedClasses(bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadModule(new QualifiedModuleName("main", "Main"));
  }
}
