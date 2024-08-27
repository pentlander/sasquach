package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.type.BuiltinType;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BasicsE2ETest extends BaseTest {

  @ParameterizedTest
  @CsvSource({"true, true", "false, false"})
  void booleanValue(String boolStr, boolean actualResult) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> %s
        }
        """.formatted(boolStr));
    boolean result = invokeMain(clazz);
    assertThat(result).isEqualTo(actualResult);
  }

  @Nested
  class VariableDeclarations extends BaseTest {
    @Test
    void booleanDecl() throws Exception {
      boolean result = declResult(BuiltinType.BOOLEAN, "true");
      assertThat(result).isTrue();
    }

    @Test
    void intDecl() throws Exception {
      int result = declResult(BuiltinType.INT, "10");
      assertThat(result).isEqualTo(10);
    }

    @Test
    void longDecl() throws Exception {
      long result = declResult(BuiltinType.LONG, "10");
      assertThat(result).isEqualTo(10L);
    }

    @Test
    void floatDecl() throws Exception {
      float result = declResult(BuiltinType.FLOAT, "3.14");
      assertThat(result).isEqualTo(3.14F);
    }

    @Test
    void doubleDecl() throws Exception {
      double result = declResult(BuiltinType.DOUBLE, "3.14");
      assertThat(result).isEqualTo(3.14D);
    }

    @Test
    void stringDecl() throws Exception {
      String result = declResult(BuiltinType.STRING, "\"hello\"");
      assertThat(result).isEqualTo("hello");
    }

    private <T> T declResult(BuiltinType type, String value) throws Exception {
      var typeName = type.typeNameStr();
      var clazz = compile( """
        Main {
          main = (): %s -> {
            let var: %s = %s
            var
          }
        }
        """.formatted(typeName, typeName, value));
       return invokeMain(clazz);
    }
  }

  @Nested
  class ForeignFunctionHandleCalls extends BaseTest {
    @Test
    void constructor() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/lang/StringBuilder,
        
          main = (): StringBuilder -> StringBuilder#new("hi")
        }
        """);
      StringBuilder result = invokeMain(clazz);

      assertThat(result.toString()).isEqualTo("hi");
    }

    @Test
    void staticFunc_varargsNone() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/nio/file/Path,
          use foreign java/nio/file/Paths,
        
          main = (): Path -> Paths#get("/etc")
        }
        """);
      Path result = invokeMain(clazz);


      assertThat(result).isEqualTo(Paths.get("/etc"));
    }

    @Test
    void staticFunc_varargsSingle() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/nio/file/Path,
          use foreign java/nio/file/Paths,
        
          main = (): Path -> Paths#get("/etc", "hi.txt")
        }
        """);
      Path result = invokeMain(clazz);


      assertThat(result).isEqualTo(Paths.get("/etc", "hi.txt"));
    }

    @Test
    void staticFunc_varargsMultiple() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/nio/file/Path,
          use foreign java/nio/file/Paths,
        
          main = (): Path -> Paths#get("/etc", "foo", "hi.txt")
        }
        """);
      Path result = invokeMain(clazz);


      assertThat(result).isEqualTo(Paths.get("/etc",  "foo", "hi.txt"));
    }

    @Test
    void virtualFunc() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/lang/String,
        
          main = (): String -> String#concat("he", "llo")
        }
        """);
      String result = invokeMain(clazz);

      assertThat(result).isEqualTo("hello");
    }
  }

  @Nested
  class ForeignMemberAccessTest extends BaseTest {
    @Test @Disabled("Is broken and hasn't been needed yet")
    void field() throws Exception {
      cl.linkClass(IntBox.class);

      var clazz = compile( """
        Main {
          use foreign com/pentlander/sasquach/e2e/BasicsE2ETest$ForeignMemberAccessTest$IntBox,
        
          main = (): Int -> {
            let box = IntBox#new(11)
            box#i
          }
        }
        """);
      int ps = invokeMain(clazz);

      assertThat(ps).isEqualTo(11);
    }

    @Test
    void staticField() throws Exception {
      var clazz = compile( """
        Main {
          use foreign java/io/PrintStream,
          use foreign java/lang/System,
        
          main = (): PrintStream -> System#out
        }
        """);
      PrintStream ps = invokeMain(clazz);

      assertThat(ps).isEqualTo(System.out);
    }
    public static class IntBox {
      public final int i;

      public IntBox(int i) {
        this.i = i;
      }
    }
  }
}
