package com.smousseur.orbitlab.core;

import java.io.Serial;

/**
 * Base unchecked exception for OrbitLab application errors.
 *
 * <p>Extends {@link RuntimeException} to avoid cluttering method signatures with checked exceptions
 * for domain-specific error conditions.
 */
public class OrbitlabException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a new OrbitLab exception with the specified detail message.
   *
   * @param message the detail message describing the error
   */
  public OrbitlabException(String message) {
    super(message);
  }

  /**
   * Instantiates a new Orbitlab exception.
   *
   * @param message the detail message describing the error
   * @param cause the cause
   */
  public OrbitlabException(String message, Throwable cause) {
    super(message, cause);
  }
}
