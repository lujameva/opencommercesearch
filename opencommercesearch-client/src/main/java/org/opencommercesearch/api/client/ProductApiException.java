package org.opencommercesearch.api.client;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Thrown to indicate that an unexpected situation occurred while interacting with the product API.
 * @author jmendez
 */
public class ProductApiException extends Exception {

  /**
   * Constructs a new product API exception with the specified detail message.
   * @param message the detail message. The detail message is saved for
   *                later retrieval by the {@link #getMessage()} method.
   */
  public ProductApiException(String message) {
    super(message);
  }

  /**
   * Constructs a new product API exception with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this exception's detail message.
   *
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public ProductApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
