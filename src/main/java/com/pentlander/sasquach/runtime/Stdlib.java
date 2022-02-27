package com.pentlander.sasquach.runtime;

import java.util.ArrayList;

public class Stdlib {
  public <T> ArrayList<T> map(ArrayList<T> list) {
    return new ArrayList<>(list);
  }
}
