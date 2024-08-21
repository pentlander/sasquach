package com.pentlander.sasquach;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.backend.BytecodeResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public abstract class BaseTest {
  public static int NONE = 0;
  public static int DUMP_CLASSES = 1;
  public static int INCLUDE_STD = 1 << 1;

  private String testName;

  @BeforeEach
  void setup(TestInfo testInfo) {
    testName = testInfo.getDisplayName();
  }

  protected int defaultOpts() {
    return NONE;
  }

  /** Returns the class {@code main.Main} */
  protected final Class<?> compile(String source) throws ClassNotFoundException, CompilationException {
    return compileMain(source, NONE);
  }

  @SuppressWarnings("unused")
  protected final Class<?> compileDebug(String source) throws ClassNotFoundException, CompilationException {
    return compileMain(source, DUMP_CLASSES);
  }

  private Class<?> compileMain(String source, int options)
      throws ClassNotFoundException, CompilationException {
    return compile(Source.fromString("main", source), options);
  }

  private Class<?> compile(Source source, int options)
      throws ClassNotFoundException, CompilationException {
    var url = requireNonNull(getClass().getResource("/stdlib"));

    BytecodeResult bytecode;
    try {
      var path = Paths.get(url.toURI());
      var compiler = new Compiler();
      var baseSources = hasOpt(options, INCLUDE_STD) ? compiler.findFiles(path) : Sources.empty();
      var sources = baseSources.merge(Sources.single(source));
      bytecode = compiler.compile(sources);
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }

    var cl = new SasquachClassloader();
    if (hasOpt(options, DUMP_CLASSES)) {
      var path = Path.of(Files.temporaryFolderPath(), testName.replaceAll("[()]", ""));
      TestUtils.dumpGeneratedClasses(path, bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadModule(new QualifiedModuleName(new PackageName("main"), "Main"));
  }

  private boolean hasOpt(int opts, int checkOpt) {
    return ((opts | defaultOpts()) & checkOpt) != 0;
  }
}
