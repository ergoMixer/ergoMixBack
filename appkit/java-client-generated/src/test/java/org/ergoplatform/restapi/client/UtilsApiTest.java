package org.ergoplatform.restapi.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for UtilsApi
 */
public class UtilsApiTest {

    private UtilsApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052/").createService(UtilsApi.class);
    }


    /**
     * Convert Pay-To-Public-Key Address to raw representation (hex-encoded serialized curve point)
     *
     * 
     */
    @Test
    public void addressToRawTest() {
        String address = null;
        // String response = api.addressToRaw(address);

        // TODO: test validations
    }

    /**
     * Check address validity
     *
     * 
     */
    @Test
    public void checkAddressValidityTest() {
        String address = null;
        // AddressValidity response = api.checkAddressValidity(address);

        // TODO: test validations
    }

    /**
     * Generate Ergo address from hex-encoded ErgoTree
     *
     * 
     */
    @Test
    public void ergoTreeToAddressTest() {
        String ergoTreeHex = null;
        // String response = api.ergoTreeToAddress(ergoTreeHex);

        // TODO: test validations
    }

    /**
     * Get random seed of 32 bytes
     *
     * 
     */
    @Test
    public void getRandomSeedTest() {
        // String response = api.getRandomSeed();

        // TODO: test validations
    }

    /**
     * Generate random seed of specified length in bytes
     *
     * 
     */
    @Test
    public void getRandomSeedWithLengthTest() {
        String length = null;
        // String response = api.getRandomSeedWithLength(length);

        // TODO: test validations
    }

    /**
     * Return Blake2b hash of specified message
     *
     * 
     */
    @Test
    public void hashBlake2bTest() {
        String body = null;
        // String response = api.hashBlake2b(body);

        // TODO: test validations
    }

    /**
     * Generate Pay-To-Public-Key address from hex-encoded raw pubkey (secp256k1 serialized point)
     *
     * 
     */
    @Test
    public void rawToAddressTest() {
        String pubkeyHex = null;
        // String response = api.rawToAddress(pubkeyHex);

        // TODO: test validations
    }
}
