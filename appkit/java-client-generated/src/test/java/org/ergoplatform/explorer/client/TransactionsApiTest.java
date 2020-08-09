package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.model.FullTransaction;
import org.junit.Before;
import org.junit.Test;

/**
 * API tests for TransactionsApi
 */
public class TransactionsApiTest {

    private TransactionsApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("http://localhost:9052").createService(TransactionsApi.class);
    }


    /**
     * Send an Ergo transaction
     *
     * 
     */
    @Test
    public void sendTransactionTest() {
        FullTransaction body = null;
        // TransactionIdResponse response = api.sendTransaction(body);

        // TODO: test validations
    }

    /**
     * Get all boxes containing given address
     *
     * 
     */
    @Test
    public void transactionsBoxesByAddressIdGetTest() {
        String id = null;
        // TransactionOutput response = api.transactionsBoxesByAddressIdGet(id);

        // TODO: test validations
    }

    /**
     * Get unspent boxes containing given address
     *
     * 
     */
    @Test
    public void transactionsBoxesByAddressUnspentIdGetTest() {
        String id = null;
        // TransactionOutput response = api.transactionsBoxesByAddressUnspentIdGet(id);

        // TODO: test validations
    }

    /**
     * Get all boxes containing given ergoTree
     *
     * 
     */
    @Test
    public void transactionsBoxesByErgoTreeErgoTreeGetTest() {
        String ergoTree = null;
        // TransactionOutput response = api.transactionsBoxesByErgoTreeErgoTreeGet(ergoTree);

        // TODO: test validations
    }

    /**
     * Get unspent boxes containing given ergoTree
     *
     * 
     */
    @Test
    public void transactionsBoxesByErgoTreeUnspentErgoTreeGetTest() {
        String ergoTree = null;
        // TransactionOutput response = api.transactionsBoxesByErgoTreeUnspentErgoTreeGet(ergoTree);

        // TODO: test validations
    }

    /**
     * Get box by id
     *
     * 
     */
    @Test
    public void transactionsBoxesIdGetTest() {
        String id = null;
        // TransactionOutput response = api.transactionsBoxesIdGet(id);

        // TODO: test validations
    }

    /**
     * Get transction by id
     *
     * 
     */
    @Test
    public void transactionsIdGetTest() {
        String id = null;
        // FullTransaction response = api.transactionsIdGet(id);

        // TODO: test validations
    }

    /**
     * Get all transactions appeared in the main-chain after a given {height}
     *
     * 
     */
    @Test
    public void transactionsSinceHeightGetTest() {
        Integer height = null;
        // Transaction response = api.transactionsSinceHeightGet(height);

        // TODO: test validations
    }

    /**
     * Get unconfirmed transactions containing outputs to a specified address
     *
     * 
     */
    @Test
    public void transactionsUnconfirmedByAddressIdGetTest() {
        String id = null;
        // List<UnconfirmedTransaction> response = api.transactionsUnconfirmedByAddressIdGet(id);

        // TODO: test validations
    }

    /**
     * Get unconfirmed transactions
     *
     * 
     */
    @Test
    public void transactionsUnconfirmedGetTest() {
        // List<UnconfirmedTransaction> response = api.transactionsUnconfirmedGet();

        // TODO: test validations
    }

    /**
     * Get unconfirmed transaction by id
     *
     * 
     */
    @Test
    public void transactionsUnconfirmedIdGetTest() {
        String id = null;
        // UnconfirmedTransaction response = api.transactionsUnconfirmedIdGet(id);

        // TODO: test validations
    }
}
