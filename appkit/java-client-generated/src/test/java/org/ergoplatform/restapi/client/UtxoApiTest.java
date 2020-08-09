package org.ergoplatform.restapi.client;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * API tests for UtxoApi
 */
public class UtxoApiTest {

    private UtxoApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052/").createService(UtxoApi.class);
    }


    /**
     * Get genesis boxes (boxes existed before the very first block)
     *
     * 
     */
    @Test
    public void genesisBoxesTest() {
        // List<ErgoTransactionOutput> response = api.genesisBoxes();

        // TODO: test validations
    }

    /**
     * Get box contents for a box by a unique identifier.
     *
     * 
     */
    @Test
    public void getBoxByIdTest() {
        String boxId = "83b94f2df7e97586a9fe8fe43fa84d252aa74ecee5fe0871f85a45663927cd9a";
        try {
            ErgoTransactionOutput response = api.getBoxById(boxId).execute().body();
            System.out.println(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get serialized box in Base16 encoding for a box with given unique identifier.
     *
     * 
     */
    @Test
    public void getBoxByIdBinaryTest() {
        String boxId = null;
        // SerializedBox response = api.getBoxByIdBinary(boxId);

        // TODO: test validations
    }
}
