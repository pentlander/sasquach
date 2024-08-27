package com.pentlander.sasquach;

import static java.util.Objects.requireNonNull;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import com.pentlander.sasquach.BaseTest.Extension;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.backend.BytecodeResult;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

@ExtendWith(Extension.class)
public abstract class BaseTest {
  private static final Lookup LOOKUP = MethodHandles.lookup();
  public static final int NONE = 0;
  public static final int DUMP_CLASSES = 1;
  public static final int INCLUDE_STD = 1 << 1;

  @SuppressWarnings("FieldMayBeFinal")
  private int defaultOpts = NONE;
  @SuppressWarnings("unused")
  private String testName;

  protected SasquachClassloader cl = new SasquachClassloader();

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

    if (hasOpt(options, DUMP_CLASSES)) {
      var path = Path.of(Files.temporaryFolderPath(), testName.replaceAll("[()]", ""));
      TestUtils.dumpGeneratedClasses(path, bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadModule(new QualifiedModuleName(new PackageName("main"), "Main"));
  }

  private boolean hasOpt(int opts, int checkOpt) {
    return ((opts | defaultOpts) & checkOpt) != 0;
  }

  static class Extension implements TestInstancePostProcessor, BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext extensionCtx) throws Exception {
      var testInstance = extensionCtx.getRequiredTestInstance();
      var vh = LOOKUP.findVarHandle(BaseTest.class, "testName", String.class);
      vh.set(testInstance, extensionCtx.getDisplayName());
    }

    @Override
    public void postProcessTestInstance(Object obj, ExtensionContext extensionCtx)
        throws Exception {
      var ann = findAnnotation(extensionCtx.getRequiredTestClass(), DefaultOptions.class, true);
      if (ann.isPresent()) {
        var vh = LOOKUP.findVarHandle(BaseTest.class, "defaultOpts", int.class);
        vh.set(obj, ann.get().value());
      }
    }
  }

  @Target({ ElementType.TYPE })
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DefaultOptions {
    int value();
  }
}
