package org.ergoplatform.appkit;

/**
 * Exception class which is typically thrown from this library code.
 * Usually root cause exceptions are caught and rethrown wrapped in this class.
 */
public class ErgoClientException extends RuntimeException {
    public ErgoClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
