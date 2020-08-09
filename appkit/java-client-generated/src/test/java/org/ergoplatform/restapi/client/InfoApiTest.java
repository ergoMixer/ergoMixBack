package org.ergoplatform.restapi.client;

import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;

import java.io.IOException;

/**
 * API tests for InfoApi
 */
public class InfoApiTest {

    private InfoApi api;

    @Before
    public void setup() {
        api = new ApiClient("http://localhost:9052/").createService(InfoApi.class);
    }


    /**
     * Get the information about the Node
     *
     * 
     */
    @Test
    public void getNodeInfoTest() {
        try {
            Response<NodeInfo> response = api.getNodeInfo().execute();
            System.out.println(response.body().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
