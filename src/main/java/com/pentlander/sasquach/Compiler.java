package com.pentlander.sasquach;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.backend.BytecodeResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.atteo.classindex.ClassIndex;

public class Compiler {
  private final CompilationUnitParser parser = new CompilationUnitParser();

  Map<String, Source> findFiles(Path sourcePath) throws IOException {
    if (Files.isDirectory(sourcePath)) {
      var sources = new HashMap<String, Source>();
      Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          var source = Source.fromPath(file);
          sources.put(source.packageName(), source);
          return FileVisitResult.CONTINUE;
        }
      });
      return sources;
    } else {
      var source = Source.fromPath(sourcePath);
      return Map.of(source.packageName(), source);
    }
  }


  public BytecodeResult compile(Source source) throws CompilationException {
    var compilationUnit = parser.getCompilationUnit(source);
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

  void compile(Path sourcePath, Path outputPath) {
    try {
      var sources = findFiles(sourcePath);

      var bytecodeResults = new HashMap<String, byte[]>();
      for (Entry<String, Source> entry : sources.entrySet()) {
        Source source = entry.getValue();
        try {
          var bytecodeResult = compile(source);
          bytecodeResults.putAll(bytecodeResult.generatedBytecode());
        } catch (CompilationException e) {
          printErrors(source, e.errors());
          return;
        }
      }

      clearDestDir(outputPath);
      writeRuntimeFiles(outputPath);
      for (var entry : bytecodeResults.entrySet()) {
        String name = entry.getKey();
        byte[] byteCode = entry.getValue();
        saveBytecodeToFile(outputPath, name, byteCode);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void printErrors(Source source, List<? extends Error> errors) {
    if (!errors.isEmpty()) {
      for (Error compileError : errors) {
        System.out.printf("error: %s\n", compileError.toPrettyString(source));
      }
    }
  }

  public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode) throws IOException {
      var filepath = outputDir.resolve(className.replace('.', '/') + ".class");
      Files.createDirectories(filepath.getParent());
      Files.write(filepath, byteCode);
  }

  void clearDestDir(Path outputPath) throws IOException {
    Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  void writeRuntimeFiles(Path outputPath) throws IOException {
    var outPath = outputPath.resolve("com/pentlander/sasquach/runtime/");
    for (var clazz : ClassIndex.getPackageClasses("com.pentlander.sasquach.runtime")) {
      var resourcePath = Paths.get(clazz.getName().replace('.', '/') + ".class");
      var classFileBytes = requireNonNull(Main.class.getClassLoader()
          .getResourceAsStream(resourcePath.toString())).readAllBytes();
      Files.write(outPath.resolve(resourcePath.getFileName()), classFileBytes);
    }
  }
}
