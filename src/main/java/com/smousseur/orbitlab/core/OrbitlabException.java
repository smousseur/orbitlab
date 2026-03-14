package com.smousseur.orbitlab.core;

/**
 * Base unchecked exception for OrbitLab application errors.
 *
 * <p>Extends {@link RuntimeException} to avoid cluttering method signatures with checked
 * exceptions for domain-specific error conditions.
 */
public class OrbitlabException extends RuntimeException {
  /**
   * Creates a new OrbitLab exception with the specified detail message.
   *
   * @param message the detail message describing the error
   */
  public OrbitlabException(String message) {
    super(message);
  }
}
