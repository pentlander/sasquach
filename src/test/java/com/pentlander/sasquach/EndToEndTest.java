package com.pentlander.sasquach;

import static com.pentlander.sasquach.Main.compile;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.Main.CompilationException;
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

  private Class<?> compileClass(Source source, String qualifiedName)
      throws ClassNotFoundException, CompilationException {
    var bytecode = compile(source);
    var cl = new SasquachClassloader();
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
