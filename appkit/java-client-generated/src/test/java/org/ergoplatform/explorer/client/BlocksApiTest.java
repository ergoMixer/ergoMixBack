package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.model.InlineResponse200;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;

import java.io.IOException;

/**
 * API tests for BlocksApi
 */
public class BlocksApiTest {

    private BlocksApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("https://api.ergoplatform.com").createService(BlocksApi.class);
    }


    /**
     * Get block by d
     *
     * 
     */
    @Test
    public void blocksByDDGetTest() {
        Integer d = null;
        // InlineResponse2001 response = api.blocksByDDGet(d);

        // TODO: test validations
    }

    /**
     * Get block by id
     *
     * 
     */
    @Test
    public void blocksIdGetTest() {
        String id = null;
        // InlineResponse2001 response = api.blocksIdGet(id);

        // TODO: test validations
    }

    /**
     * Get list of blocks
     *
     * Get list of blocks sorted by height
     */
    @Test
    public void listBlocksTest() {
        Integer offset = 0;
        Integer limit = 10;
        String sortBy = null;
        String sortDirection = null;
        Integer startDate = null;
        Integer endDate = null;
        Call<InlineResponse200> response = api.listBlocks(offset, limit, sortBy, sortDirection, startDate, endDate);
        try {
            InlineResponse200 res = response.execute().body();
            System.out.println(res);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: test validations
    }
}
