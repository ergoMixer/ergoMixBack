package org.ergoplatform.restapi.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for MiningApi
 */
public class MiningApiTest {

    private MiningApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052/").createService(MiningApi.class);
    }


    /**
     * Read miner reward address
     *
     * 
     */
    @Test
    public void miningReadMinerRewardAddressTest() {
        // InlineResponse2004 response = api.miningReadMinerRewardAddress();

        // TODO: test validations
    }

    /**
     * Request block candidate
     *
     * 
     */
    @Test
    public void miningRequestBlockCandidateTest() {
        // ExternalCandidateBlock response = api.miningRequestBlockCandidate();

        // TODO: test validations
    }

    /**
     * Submit solution for current candidate
     *
     * 
     */
    @Test
    public void miningSubmitSolutionTest() {
        PowSolutions body = null;
        // Void response = api.miningSubmitSolution(body);

        // TODO: test validations
    }
}
