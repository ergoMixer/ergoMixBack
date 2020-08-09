package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

public interface ScriptApi {
  /**
   * Convert an address to hex-encoded serialized ErgoTree (script)
   * 
   * @param address address to get a script from (required)
   * @return Call&lt;InlineResponse2005&gt;
   */
  @GET("script/addressToTree/{address}")
  Call<InlineResponse2005> addressToTree(
            @retrofit2.http.Path("address") String address            
  );

  /**
   * Create P2SAddress from Sigma source
   * 
   * @param body  (required)
   * @return Call&lt;AddressHolder&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("script/p2sAddress")
  Call<AddressHolder> scriptP2SAddress(
                    @retrofit2.http.Body SourceHolder body    
  );

  /**
   * Create P2SHAddress from Sigma source
   * 
   * @param body  (required)
   * @return Call&lt;AddressHolder&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("script/p2shAddress")
  Call<AddressHolder> scriptP2SHAddress(
                    @retrofit2.http.Body SourceHolder body    
  );

}
