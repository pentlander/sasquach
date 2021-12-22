package com.pentlander.sasquach;

/**
 * A compile time error.
 */
public sealed interface Error permits BasicError, RangedError {
  /**
   * Returns a prettified string of the error. It should contain a highlight of the affected source
   * code.
   *
   * @param source the source code for the file affected.
   * @return prettified error string.
   */
  String toPrettyString(Sources source);
}
