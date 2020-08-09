package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface PeersApi {
  /**
   * Add address to peers list
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("peers/connect")
  Call<Void> connectToPeer(
                    @retrofit2.http.Body String body    
  );

  /**
   * Get all known peers
   * 
   * @return Call&lt;List&lt;Peer&gt;&gt;
   */
  @GET("peers/all")
  Call<List<Peer>> getAllPeers();
    

  /**
   * Get blacklisted peers
   * 
   * @return Call&lt;List&lt;String&gt;&gt;
   */
  @GET("peers/blacklisted")
  Call<List<String>> getBlacklistedPeers();
    

  /**
   * Get current connected peers
   * 
   * @return Call&lt;List&lt;Peer&gt;&gt;
   */
  @GET("peers/connected")
  Call<List<Peer>> getConnectedPeers();
    

}
