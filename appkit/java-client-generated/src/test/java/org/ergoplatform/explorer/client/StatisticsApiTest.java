package org.ergoplatform.explorer.client;

import org.junit.Before;
import org.junit.Test;

/**
 * API tests for StatisticsApi
 */
public class StatisticsApiTest {

    private StatisticsApi api;

    @Before
    public void setup() {
        api = new ExplorerApiClient("http://localhost:9052").createService(StatisticsApi.class);
    }


    /**
     * Get blockchain info
     *
     * 
     */
    @Test
    public void infoGetTest() {
        // BlockchainInfo response = api.infoGet();

        // TODO: test validations
    }

    /**
     * Get current supply
     *
     * 
     */
    @Test
    public void infoSupplyGetTest() {
        // BigDecimal response = api.infoSupplyGet();

        // TODO: test validations
    }

    /**
     * Forks info summary
     *
     * 
     */
    @Test
    public void statsForksGetTest() {
        Integer fromHeight = null;
        // ForksInfo response = api.statsForksGet(fromHeight);

        // TODO: test validations
    }

    /**
     * Get blockchain stats
     *
     * 
     */
    @Test
    public void statsGetTest() {
        // BlockchainStats response = api.statsGet();

        // TODO: test validations
    }
}
