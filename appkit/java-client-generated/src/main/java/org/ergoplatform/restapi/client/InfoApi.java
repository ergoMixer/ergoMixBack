package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

public interface InfoApi {
  /**
   * Get the information about the Node
   * 
   * @return Call&lt;NodeInfo&gt;
   */
  @GET("info")
  Call<NodeInfo> getNodeInfo();
    

}
