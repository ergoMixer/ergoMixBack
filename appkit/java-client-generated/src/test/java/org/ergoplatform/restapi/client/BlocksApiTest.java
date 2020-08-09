package org.ergoplatform.restapi.client;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * API tests for BlocksApi
 */
public class BlocksApiTest {

    private BlocksApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052").createService(BlocksApi.class);
    }


    /**
     * Get the block header info by a given signature
     *
     * 
     */
    @Test
    public void getBlockHeaderByIdTest() {
        String headerId = null;
        // BlockHeader response = api.getBlockHeaderById(headerId);

        // TODO: test validations
    }

    /**
     * Get the block transactions info by a given signature
     *
     * 
     */
    @Test
    public void getBlockTransactionsByIdTest() {
        String headerId = null;
        // BlockTransactions response = api.getBlockTransactionsById(headerId);

        // TODO: test validations
    }

    /**
     * Get headers in a specified range
     *
     * 
     */
    @Test
    public void getChainSliceTest() {
        Integer fromHeight = null;
        Integer toHeight = null;
        // List<BlockHeader> response = api.getChainSlice(fromHeight, toHeight);

        // TODO: test validations
    }

    /**
     * Get the header ids at a given height
     *
     * 
     */
    @Test
    public void getFullBlockAtTest() {
        Integer blockHeight = null;
        // List<String> response = api.getFullBlockAt(blockHeight);

        // TODO: test validations
    }

    /**
     * Get the full block info by a given signature
     *
     * 
     */
    @Test
    public void getFullBlockByIdTest() {
        String headerId = null;
        // FullBlock response = api.getFullBlockById(headerId);

        // TODO: test validations
    }

    /**
     * Get the Array of header ids
     *
     * 
     */
    @Test
    public void getHeaderIdsTest() {
        Integer limit = null;
        Integer offset = null;
        // List<String> response = api.getHeaderIds(limit, offset);

        // TODO: test validations
    }

    /**
     * Get the last headers objects
     *
     * 
     */
    @Test
    public void getLastHeadersTest() {
        BigDecimal count = BigDecimal.valueOf(10);
        try {
            List<BlockHeader> response = api.getLastHeaders(count).execute().body();
            System.out.println(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: test validations
    }

    /**
     * Get the persistent modifier by its id
     *
     * 
     */
    @Test
    public void getModifierByIdTest() {
        String modifierId = null;
        // Void response = api.getModifierById(modifierId);

        // TODO: test validations
    }

    /**
     * Send a mined block
     *
     * 
     */
    @Test
    public void sendMinedBlockTest() {
        FullBlock body = null;
        // Void response = api.sendMinedBlock(body);

        // TODO: test validations
    }
}
