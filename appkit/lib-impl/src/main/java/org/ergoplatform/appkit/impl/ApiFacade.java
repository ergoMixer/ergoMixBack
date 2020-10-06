package org.ergoplatform.appkit.impl;

import org.ergoplatform.appkit.ErgoClientException;
import retrofit2.Retrofit;

import java.io.IOException;

abstract class ApiFacade {

    /**
     * Creates a new instance of {@link ErgoClientException} with the given cause
     * using the given {@link Retrofit} instance to format error message.
     */
    static ErgoClientException clientError(Retrofit r, Throwable cause) {
        return new ErgoClientException(
                String.format("Error executing API request to %s: %s", r.baseUrl().toString(), cause.getMessage()),
                cause);
    }

    /**
     * Helper interface with necessary exceptions
     */
    interface Supplier<T> {
        T get() throws NoSuchMethodException, IOException;
    }

    /**
     * Executes the given supplier block.
     * @param r  Retrofit instance to use for connection
     * @param block to be executed
     * @return result of the block execution
     * @throws ErgoClientException with wrapped cause
     */
    static <T> T execute(Retrofit r, Supplier<T> block) throws ErgoClientException {
        try {
            return block.get();
        } catch (NoSuchMethodException e) {
            throw clientError(r, e);
        } catch (IOException e) {
            throw clientError(r, e);
        }
    }

}
