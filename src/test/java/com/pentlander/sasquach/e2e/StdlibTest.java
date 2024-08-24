package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.BaseTest.DefaultOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
  class MapTest extends BaseTest {
    @Test
    void assocAndGet() throws Exception {
      var clazz = compile("""
        Main {
          use std/Hash,
          use std/Map,
          use std/Option,
        
          main = (): Int -> {
            let hash = Hash.new((value: String): Int -> 0)
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
  }
}
