package com.pentlander.sasquach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.backend.BytecodeResult;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.name.ModuleResolver;
import com.pentlander.sasquach.type.TypeResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class Main {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    Source source = null;
    try {
      var sasquachPath = Paths.get(args[0]);
      source = Source.fromPath(sasquachPath);
      var bytecode = compile(source);
      var outputPath = Paths.get("out");
      Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }
      });
      // TODO: Come up with non-hacky way to copy over the runtime files
      var runtimePath = Paths.get("build/classes/java/main/com/pentlander/sasquach/runtime/");
      Files.walkFileTree(runtimePath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          var outPath = outputPath.resolve("com/pentlander/sasquach/runtime/");
          Files.createDirectories(outPath);
          Files.copy(file, outPath.resolve(file.getFileName()));
          return FileVisitResult.CONTINUE;
        }
      });
      bytecode.generatedBytecode()
          .forEach((name, byteCode) -> saveBytecodeToFile(outputPath, name, byteCode));
    } catch (CompilationException e) {
      printErrors(Objects.requireNonNull(source), e.errors());
    } catch (Exception e) {
      System.err.println(e);
      e.printStackTrace();
    }
  }

  public static BytecodeResult compile(Source source) throws CompilationException {
    var compilationUnit = new Parser().getCompilationUnit(source);
    var validator = new AstValidator(compilationUnit, source);
    var compileErrors = validator.validate();
    if (!compileErrors.isEmpty()) throw new CompilationException(source, compileErrors);

    var nameResolver = new ModuleResolver();
    var nameErrors = nameResolver.resolveCompilationUnit(compilationUnit);
    if (!nameErrors.isEmpty()) throw new CompilationException(source, compileErrors);

    var typeResolver = new TypeResolver();
    var typeErrors = typeResolver.resolve(compilationUnit);
    if (!typeErrors.isEmpty()) throw new CompilationException(source, typeErrors);

    return new BytecodeGenerator(nameResolver, typeResolver).generateBytecode(compilationUnit);
  }

  public static class CompilationException extends Exception {
    private final List<? extends Error> errors;

    public CompilationException(Source source, List<? extends Error> errors) {
      super(errorMessage(source, errors));
      this.errors = errors;
    }

    private static String errorMessage(Source source, List<? extends Error> errors) {
      StringBuilder stringBuilder = new StringBuilder();
      for (Error compileError : errors) {
        stringBuilder.append("error: %s\n".formatted(compileError.toPrettyString(source)));
      }
      return stringBuilder.toString();
    }

    public List<? extends Error> errors() {
      return errors;
    }
  }

  static void printErrors(Source source, List<? extends Error> errors) {
    if (!errors.isEmpty()) {
      for (Error compileError : errors) {
        System.out.printf("error: %s\n", compileError.toPrettyString(source));
      }
      System.exit(1);
    }
  }

  public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode) {
    try {
      var filepath = outputDir.resolve(className.replace('.', '/') + ".class");
      Files.createDirectories(filepath.getParent());
      Files.write(filepath, byteCode);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static class SasquachTreeWalkErrorListener extends BaseErrorListener {
    private final List<BasicError> compileErrors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
        int charPositionInLine, String msg, RecognitionException e) {
      compileErrors.add(new BasicError(msg));
    }

    public List<BasicError> getCompileErrors() {
      return List.copyOf(compileErrors);
    }
  }

  public static class Parser {
    public CompilationUnit getCompilationUnit(Source source) {
      CharStream charStream = new ANTLRInputStream(String.join("\n", source.sourceLines()));
      var lexer = new SasquachLexer(charStream);
      var errorListener = new SasquachTreeWalkErrorListener();
      lexer.addErrorListener(errorListener);

      var tokenStream = new CommonTokenStream(lexer);
      var parser = new SasquachParser(tokenStream);
      parser.addErrorListener(errorListener);

      var visitor = new Visitor.CompilationUnitVisitor(source.packageName());
      return parser.compilationUnit().accept(visitor);
    }
  }
}