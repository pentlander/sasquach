package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.tfunc;
import static com.pentlander.sasquach.TestUtils.invokeFirst;
import static com.pentlander.sasquach.TestUtils.invokeMain;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.ast.expression.Value;
import com.pentlander.sasquach.tast.expression.TBlock;
import com.pentlander.sasquach.tast.expression.TVarReference;
import com.pentlander.sasquach.tast.expression.TVarReference.RefDeclaration;
import com.pentlander.sasquach.tast.expression.TVariableDeclaration;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.BuiltinType;
import com.pentlander.sasquach.type.Type;
import java.util.List;
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
}
