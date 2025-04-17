package com.pentlander.sasquach.e2e;

import com.pentlander.sasquach.BaseTest;
import static com.pentlander.sasquach.TestUtils.invokeMain;
import com.pentlander.sasquach.type.BuiltinType;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;
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
      int result = invokeMain(clazz);

      assertThat(result).isEqualTo(11);
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

  @Test
  void functionCall() throws Exception {
    var clazz = compile( """
        Main {
          foo = (): Int -> 5,
        
          main = (): Int -> foo()
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(5);
  }

  @Test
  void functionCall_withArg() throws Exception {
    var clazz = compile( """
        Main {
          foo = (n: Int): Int -> 5,
        
          main = (): Int -> foo(4)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(5);
  }

  @Test
  void functionCall_withArgs() throws Exception {
    var clazz = compile( """
        Main {
          foo = (str: String, n: Int): Int -> 5,
        
          main = (): Int -> foo("foo", 7)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(5);
  }

  @Test
  void functionCall_withArgsBinary() throws Exception {
    var clazz = compile( """
        Main {
          foo = (str: String, n: Int): Int -> 5,
        
          main = (): Int -> foo("foo", 7 + 2)
        }
        """);
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(5);
  }

  @Test
  void structLiteralFields() throws Exception {
    var clazz = compile( """
        Main {
          main = (): { boolField: Boolean, intField: Int, strField: String, dblField: Double} -> {
            boolField = true,
            intField = 3,
            strField = "hi",
            dblField = 5.0
          }
        }
        """);
    Object result = invokeMain(clazz);

    assertThat(result).hasFieldOrPropertyWithValue("boolField", true)
        .hasFieldOrPropertyWithValue("intField", 3)
        .hasFieldOrPropertyWithValue("strField", "hi")
        .hasFieldOrPropertyWithValue("dblField", 5.0D);
  }

  @Test
  void structLiteralFunctions() throws Exception {
    var clazz = compile( """
        Main {
          main = (): String ->
            { func = (): String -> "hi" }.func()
        }
        """);
    String result = invokeMain(clazz);

    assertThat(result).isEqualTo("hi");
  }

  @ParameterizedTest
  @CsvSource({"true, 10", "false, 5"})
  void ifExpression(boolean bool, int actualResult) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Int -> if (%s) 10 else 5
        }
        """.formatted(bool));
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, true"})
  void notExpression(boolean left, boolean right) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> !%s
        }
        """.formatted(left));
    boolean result = invokeMain(clazz);

    assertThat(result).isEqualTo(right);
  }

  @ParameterizedTest
  @CsvSource({"true, &&, true", "false, ||, true", "true, ||, false"})
  void booleanExpressionTrue(boolean left, String operator, boolean right) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> %s %s %s
        }
        """.formatted(left, operator, right));
    boolean result = invokeMain(clazz);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"false, &&, true", "true, &&, false", "false, ||, false"})
  void booleanExpressionFalse(boolean left, String operator, boolean right) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> %s %s %s
        }
        """.formatted(left, operator, right));
    boolean result = invokeMain(clazz);

    assertThat(result).isFalse();
  }

  @Test
  void complexBooleanExpression() throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> true && false || true
        }
        """);
    boolean result = invokeMain(clazz);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"==, false", "!=, true", ">=, true", "<=, false", "<, false", ">, true"})
  void intCompareOperatorNotEquals(String compareOp, boolean actualResult) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> 6 %s 3
        }
        """.formatted(compareOp));
    boolean result = invokeMain(clazz);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"==, true", "!=, false", ">=, true", "<=, true", "<, false", ">, false"})
  void intCompareOperatorEquals(String compareOp, boolean actualResult) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Boolean -> 3 %s 3
        }
        """.formatted(compareOp));
    boolean result = invokeMain(clazz);

    assertThat(result).isEqualTo(actualResult);
  }

  @ParameterizedTest
  @CsvSource({"+, 9", "-, 3", "*, 18", "/, 2"})
  void intMathOperator(String mathOp, int actualResult) throws Exception {
    var clazz = compile( """
        Main {
          main = (): Int -> 6 %s 3
        }
        """.formatted(mathOp));
    int result = invokeMain(clazz);

    assertThat(result).isEqualTo(actualResult);
  }
}
