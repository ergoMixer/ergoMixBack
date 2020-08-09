package org.ergoplatform.explorer.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for UtilitiesApi
 */
public class UtilitiesApiTest {

    private UtilitiesApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("http://localhost:9052").createService(UtilitiesApi.class);
    }


    /**
     * Search block, transactions, adresses
     *
     * 
     */
    @Test
    public void searchGetTest() {
        String query = null;
        // InlineResponse2002 response = api.searchGet(query);

        // TODO: test validations
    }
}
