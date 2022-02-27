package com.pentlander.sasquach;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class EndToEndTest {
  @Test
  void addition() throws Exception {
    var source = Source.fromString("main",
        """
        Main {
          plus = (): Int -> 3 + 4
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "plus", null);

    assertThat(sum).isEqualTo(7);
  }

  @Test
  void typeAliasStruct() throws Exception {
    var source = Source.fromString("main",
        """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> { x = x, y = 4 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  // Add test for local named type where the type name doesn't exist

  @Test
  void typeAliasStruct_importModule() throws Exception {
    var source = Source.fromString("main",
        """
        Point {
          type T = { x: Int, y: Int },
          
          new = (x: Int): T -> { x = x, y = 4 }
        }
        
        Main {
          use main/Point,
          
          getX = (point: Point.T): Int -> point.x,
          main = (): Int -> {
            getX(Point.new(5))
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void complexParameterizedExpression() throws Exception {
    var source = Source.fromString("main",
        """
        Main {
          map = [T, U](value: T, mapper: { map: (inp: T) -> U }): { value: U } ->
              { value = mapper.map(value) },
          main = (): { value: Int } -> map("foobar", { map = (inp: String): Int -> 10 })
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParameterizedExpression_withAlias() throws Exception {
    var source = Source.fromString("main",
        """
        Main {
          type T[A] = { value: A },
          type Mapper[A, B] = { map: (inp: A) -> B },
        
          map = [A, B](value: A, mapper: Mapper[A, B]): T[B] ->
              { value = mapper.map(value) },
          main = (): T[Int] -> map("foo", { map = (inp: String): Int -> 10 })
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  private Class<?> compileClass(Source source, String qualifiedName)
      throws ClassNotFoundException, CompilationException {
    return compileClass(source, qualifiedName, false);
  }

  private Class<?> debugCompileClass(Source source, String qualifiedName)
      throws ClassNotFoundException, CompilationException {
    return compileClass(source, qualifiedName, true);
  }

  private Class<?> compileClass(Source source, String qualifiedName, boolean dumpClasses)
      throws ClassNotFoundException, CompilationException {
    var bytecode = new Compiler().compile(source);
    var cl = new SasquachClassloader();
    if (dumpClasses) {
      BytecodeGeneratorTest.dumpGeneratedClasses(bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadClass(qualifiedName.replace('/', '.'));
  }

  @SuppressWarnings("unchecked")
  private <T> T invokeName(Class<?> clazz, String name, Object obj, Object... args) throws Exception {
    for (var method : clazz.getMethods()) {
      if (method.getName().equals(name)) {
        return (T) method.invoke(obj, args);
      }
    }
    throw new NoSuchMethodException();
  }
}
