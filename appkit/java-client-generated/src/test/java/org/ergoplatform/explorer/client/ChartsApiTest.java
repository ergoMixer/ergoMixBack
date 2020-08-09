package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.model.Timespan;
import org.junit.Before;
import org.junit.Test;

/**
 * API tests for ChartsApi
 */
public class ChartsApiTest {

    private ChartsApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("http://localhost:9052").createService(ChartsApi.class);
    }


    /**
     * Get average block size per date
     *
     * 
     */
    @Test
    public void chartsBlockSizeGetTest() {
        Timespan timespan = null;
        // List<InlineResponse20010> response = api.chartsBlockSizeGet(timespan);

        // TODO: test validations
    }

    /**
     * The total size of all block headers and transactions per day.
     *
     * 
     */
    @Test
    public void chartsBlockchainSizeGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2004> response = api.chartsBlockchainSizeGet(timespan);

        // TODO: test validations
    }

    /**
     * Difficulty per day.
     *
     * 
     */
    @Test
    public void chartsDifficultyGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2007> response = api.chartsDifficultyGet(timespan);

        // TODO: test validations
    }

    /**
     * An estimation of hashrate distribution amongst the largest mining pools
     *
     * 
     */
    @Test
    public void chartsHashRateDistributionGetTest() {
        // List<InlineResponse2009> response = api.chartsHashRateDistributionGet();

        // TODO: test validations
    }

    /**
     * Hash Rate per day.
     *
     * 
     */
    @Test
    public void chartsHashRateGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2006> response = api.chartsHashRateGet(timespan);

        // TODO: test validations
    }

    /**
     * Total value of coinbase block rewards and transaction fees paid to miners per day.
     *
     * 
     */
    @Test
    public void chartsMinersRevenueGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2008> response = api.chartsMinersRevenueGet(timespan);

        // TODO: test validations
    }

    /**
     * Get total sum of coins mined per day
     *
     * 
     */
    @Test
    public void chartsTotalGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2003> response = api.chartsTotalGet(timespan);

        // TODO: test validations
    }

    /**
     * Transactions per block per day.
     *
     * 
     */
    @Test
    public void chartsTransactionsPerBlockGetTest() {
        Timespan timespan = null;
        // List<InlineResponse2005> response = api.chartsTransactionsPerBlockGet(timespan);

        // TODO: test validations
    }
}
