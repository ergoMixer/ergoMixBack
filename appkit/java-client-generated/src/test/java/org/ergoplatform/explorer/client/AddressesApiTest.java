package org.ergoplatform.explorer.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for AddressesApi
 */
public class AddressesApiTest {

    private AddressesApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("http://localhost:9052").createService(AddressesApi.class);
    }


    /**
     * Get addresses holding an asset with a given id
     *
     * 
     */
    @Test
    public void addressesAssetHoldersIdGetTest() {
        String id = null;
        Integer offset = null;
        Integer limit = null;
        // List<String> response = api.addressesAssetHoldersIdGet(id, offset, limit);

        // TODO: test validations
    }

    /**
     * Get address by id
     *
     * 
     */
    @Test
    public void addressesIdGetTest() {
        String id = null;
        // FullAddress response = api.addressesIdGet(id);

        // TODO: test validations
    }

    /**
     * Get transactions related to address
     *
     * 
     */
    @Test
    public void addressesIdTransactionsGetTest() {
        String id = null;
        Integer offset = null;
        Integer limit = null;
        // List<Transaction> response = api.addressesIdTransactionsGet(id, offset, limit);

        // TODO: test validations
    }
}
