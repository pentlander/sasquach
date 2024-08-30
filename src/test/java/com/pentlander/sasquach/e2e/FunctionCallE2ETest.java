package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.CompilationException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class FunctionCallE2ETest extends BaseTest {
  @Test
  void labeledArgs() throws Exception {
    var clazz = compile( """
        Main {
          sub = (num a: Int, by b: Int): Int -> a - b,
        
          main = (): Int -> sub(num=7, by=3)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(4);
  }

  @Test
  void labeledArgs_reverseOrder() throws Exception {
    var clazz = compile( """
        Main {
          sub = (num a: Int, by b: Int): Int -> a - b,
        
          main = (): Int -> sub(by=3, num=7)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(4);
  }

  @Test
  void labeledArgs_labelMissing() throws Exception {
    var ex = assertThrows(CompilationException.class, () -> compile("""
        Main {
          sub = (num a: Int, by b: Int): Int -> a - b,
                
          main = (): Int -> sub(num=3)
        }
        """));

    assertThat(ex).hasMessageContaining("Missing labeled arg");
  }

  @Test
  void labeledArgs_labelDoesNotExist() throws Exception {
    var ex = assertThrows(CompilationException.class, () -> compile("""
        Main {
          sub = (num a: Int, by b: Int): Int -> a - b,
                
          main = (): Int -> sub(num=3, by=4, nope=7)
        }
        """));

    assertThat(ex).hasMessageContaining("No param labeled");
  }

  @Test @Disabled("Implement defaults")
  void labeledArgsWithDefault() throws Exception {
    var clazz = compile( """
        Main {
          add = (a: Int, to b: Int, also c: Int = 10): Int -> a + b,
        
          main = (): Int -> add(1, to=3)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(4);
  }
}
