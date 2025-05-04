package com.pentlander.sasquach;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.backend.BytecodeResult;
import com.pentlander.sasquach.nameres.ModuleResolver;
import com.pentlander.sasquach.rdparser.SasquachParser;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.atteo.classindex.ClassIndex;

public class Compiler {
  private final Set<Option> options;

  public Compiler(Set<Option> options) {
    this.options = options;
  }

  Sources findFiles(List<Path> sourcePaths) throws IOException {
    if (sourcePaths.isEmpty()) {
      throw new IllegalStateException("At least one source path is required.");
    }

    var sources = Sources.empty();
    for (var sourcePath : sourcePaths) {
      sources = sources.merge(findFiles(sourcePath));
    }
    return sources;
  }

  public Sources findFiles(Path sourcePath) throws IOException {
    if (Files.isDirectory(sourcePath)) {
      var sources = new HashMap<SourcePath, Source>();
      Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs)
            throws IOException {
          if (Files.isDirectory(filePath)) {
            return FileVisitResult.CONTINUE;
          }

          var relPath = sourcePath.relativize(filePath);
          var packageName = simplifyPackageName(relPath);
          var source = new Source(SourcePath.fromPath(filePath),
              packageName,
              Files.readAllLines(filePath));
          sources.put(source.path(), source);
          return FileVisitResult.CONTINUE;
        }
      });
      return Sources.fromMap(sources);
    } else {
      if (sourcePath.isAbsolute()) {
        throw new IOException("File source must be a relative path: " + sourcePath);
      }

      var source = new Source(SourcePath.fromPath(sourcePath),
          simplifyPackageName(sourcePath),
          Files.readAllLines(sourcePath));
      return Sources.single(source);
    }
  }

  private static String simplifyPackageName(Path relPath) {
    // Need to collapse the package name, i.e. foo/std/std/Foo -> foo/std/Foo
    var parentPath = relPath.getParent();
    var strippedFileName = strippedFileName(relPath);
    return switch (parentPath) {
      case null -> strippedFileName;
      default -> {
        var parentName = parentPath.getFileName().toString();
        if (parentName.equals(strippedFileName)) {
          yield parentPath.toString();
        } else {
          yield String.join("/", parentPath.toString(), strippedFileName);
        }
      }
    };
  }

  public static String strippedFileName(Path path) {
    return path.getFileName().toString().split("\\.")[0];
  }

  public BytecodeResult compile(Source source) throws CompilationException {
    return compile(Sources.single(source));
  }

  public BytecodeResult compile(Sources sources) throws CompilationException {
    Sources combinedSources;
    try {
      combinedSources = options.contains(Option.NO_STD) ? sources
          : findFiles(Path.of("src/main/sasquach/sasquach")).merge(sources);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    var compUnits = new ArrayList<CompilationUnit>();
    for (Source source : combinedSources.values()) {
      var result = SasquachParser.parse(source);
      result.errors().throwIfNotEmpty(combinedSources);

      var compilationUnit = result.item();
      compUnits.add(compilationUnit);

      var validator = new AstValidator(compilationUnit);
      var compileErrors = validator.validate();
      if (!compileErrors.isEmpty()) throw new CompilationException(source, compileErrors);
    }

    var nameResolver = new ModuleResolver();
    var nameResolutionResult = nameResolver.resolveCompilationUnits(compUnits);
    nameResolutionResult.errors().throwIfNotEmpty(combinedSources);

    var typeResolver = new TypeResolver(nameResolutionResult);
    var typeResolutionResult = typeResolver.resolve(compUnits);
    typeResolutionResult.errors().throwIfNotEmpty(combinedSources);

    var bytecodeGenerator = new BytecodeGenerator();
    return bytecodeGenerator.generateBytecode(typeResolutionResult.getModuleDeclarations());
  }

  public Result compile(List<Path> sourcePaths, Path outputPath) {
    try {
      var sasqSources = options.contains(Option.NO_STD) ? Sources.empty()
          : findFiles(Path.of("src/main/sasquach/sasquach"));
      var sources = sasqSources.merge(findFiles(sourcePaths));
      Map<String, byte[]> bytecodeResults;
      try {
        bytecodeResults = compile(sources).generatedClasses();
      } catch (CompilationException e) {
        printErrors(sources, e.errors());
        return Result.FAILURE;
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
    return Result.SUCCESS;
  }

  static void printErrors(Sources sources, List<? extends Error> errors) {
    if (!errors.isEmpty()) {
      for (Error compileError : errors) {
        var message = switch (compileError) {
          case BasicError basicError -> "error: %s\n".formatted(basicError.toPrettyString(sources));
          case RangedError rangedError ->
              "%s:\nerror: %s\n".formatted(rangedError.range(),
                  rangedError.toPrettyString(sources));
        };
        System.out.print(message);
      }
    }
  }

  public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode)
      throws IOException {
    var filepath = outputDir.resolve(className.replace('.', '/') + ".class");
    Files.createDirectories(filepath.getParent());
    Files.write(filepath, byteCode);
  }

  void clearDestDir(Path outputPath) throws IOException {
    if (Files.notExists(outputPath)) return;
    Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  void writeRuntimeFiles(Path outputPath) throws IOException {
    var packageClassList = Stream.of("com.pentlander.sasquach.runtime", "com.pentlander.sasquach.runtime.bootstrap")
        .map(ClassIndex::getPackageClasses)
        .toList();
    for (var packageClasses : packageClassList) {
      for (var clazz : packageClasses) {
        var resourcePath = Paths.get(clazz.getName().replace('.', '/') + ".class");
        var classFileBytes = requireNonNull(Main.class.getClassLoader()
            .getResourceAsStream(resourcePath.toString())).readAllBytes();
        var outputFilePath = outputPath.resolve(resourcePath);
        if (Files.notExists(outputFilePath.getParent())) {
          Files.createDirectories(outputFilePath.getParent());
        }
        Files.write(outputFilePath, classFileBytes);
      }
    }
  }

  public enum Option {
    NO_STD,
  }

  public enum Result {
    SUCCESS, FAILURE
  }
}
