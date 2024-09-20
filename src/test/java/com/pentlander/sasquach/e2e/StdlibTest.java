package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.BaseTest.DefaultOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DefaultOptions(BaseTest.INCLUDE_STD)
public class StdlibTest extends BaseTest {
  @Test
  void test() throws Exception {
    var clazz = compile("""
        Main {
           main = (): Int -> 1
        }
        """);

  }

  @Nested
  class IntTest extends BaseTest {
    @ParameterizedTest
    @CsvSource({"3, true", "4, false"})
    void testEquals(int i, boolean expected) throws Exception {
      var clazz = compile("""
        Main {
          use std/Int,
        
          main = (): Boolean -> Int.equals(3, %s)
        }
        """.formatted(i));

      boolean isEqual = invokeMain(clazz);
      assertThat(isEqual).isEqualTo(expected);
    }
  }

  @Nested
  class StringTest extends BaseTest {
    @ParameterizedTest
    @CsvSource({"foo, true", "bar, false"})
    void testEquals(String str, boolean expected) throws Exception {
      var clazz = compile("""
        Main {
          use std/String,
        
          main = (): Boolean -> String.equals("foo", "%s")
        }
        """.formatted(str));

        boolean isEqual = invokeMain(clazz);
        assertThat(isEqual).isEqualTo(expected);
    }
  }

  @Nested
  class MapTest extends BaseTest {
    @Test
    void assocAndGet() throws Exception {
      var clazz = compileDebug("""
        Main {
          use std/Hash,
          use std/Map,
          use std/Option,
        
          main = (): Int -> {
            let hash = Hash.new((value) -> 0)
            let map = Map.new(hash)
            
            let mapWithKey = map |> Map.assoc("foo", 1)
            
            mapWithKey
              |> Map.get("foo")
              |> Option.unwrap()
          }
        }
        """);

      int value = invokeMain(clazz);
      assertThat(value).isEqualTo(1);
    }

    @Test
    void assocAndGet_structKey() throws Exception {
      var clazz = compileDebug("""
        Main {
          use std/Hash,
          use std/Map,
          use std/Option,
          
          type User = { id: Int, name: String },
        
          main = (): Int -> {
            let hash = Hash.new((user: User) -> user.id)
            let map = Map.new(hash)
            
            let user = { id = 1, name = "Bob", }
            let mapWithKey = Map.assoc(map, user, 10)
            
            let userCopy = { id = 1, name = "Bob", }
            mapWithKey
              |> Map.get(userCopy)
              |> Option.unwrap()
          }
        }
        """);

      int value = invokeMain(clazz);
      assertThat(value).isEqualTo(10);
    }
  }
}
