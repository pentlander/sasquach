package com.pentlander.sasquach;

import static java.util.Objects.*;

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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.atteo.classindex.ClassIndex;

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

      var outPath = outputPath.resolve("com/pentlander/sasquach/runtime/");
      for (var clazz : ClassIndex.getPackageClasses("com.pentlander.sasquach.runtime")) {
        var resourcePath = Paths.get(clazz.getName().replace('.', '/') + ".class");
        var classFileBytes = requireNonNull(Main.class.getClassLoader()
            .getResourceAsStream(resourcePath.toString())).readAllBytes();
        Files.write(outPath.resolve(resourcePath.getFileName()), classFileBytes);
      }
      bytecode.generatedBytecode()
          .forEach((name, byteCode) -> saveBytecodeToFile(outputPath, name, byteCode));
    } catch (CompilationException e) {
      printErrors(requireNonNull(source), e.errors());
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
    var nameResolutionResult = nameResolver.resolveCompilationUnit(compilationUnit);
    nameResolutionResult.errors().throwIfNotEmpty(source);

    var typeResolver = new TypeResolver(nameResolutionResult);
    typeResolver.resolve(compilationUnit).throwIfNotEmpty(source);

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
      CharStream charStream = CharStreams.fromString(String.join("\n", source.sourceLines()));
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