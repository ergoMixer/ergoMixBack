package org.ergoplatform.restapi.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for TransactionsApi
 */
public class TransactionsApiTest {

    private TransactionsApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052/").createService(TransactionsApi.class);
    }


    /**
     * Get current pool of the unconfirmed transactions pool
     *
     * 
     */
    @Test
    public void getUnconfirmedTransactionsTest() {
        Integer limit = null;
        Integer offset = null;
        // Transactions response = api.getUnconfirmedTransactions(limit, offset);

        // TODO: test validations
    }

    /**
     * Send an Ergo transaction
     *
     * 
     */
    @Test
    public void sendTransactionTest() {
        ErgoTransaction body = null;
        // String response = api.sendTransaction(body);

        // TODO: test validations
    }
}
