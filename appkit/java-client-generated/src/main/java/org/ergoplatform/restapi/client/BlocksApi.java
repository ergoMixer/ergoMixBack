package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

import java.math.BigDecimal;

import java.util.List;

public interface BlocksApi {
  /**
   * Get the block header info by a given signature
   * 
   * @param headerId ID of a wanted block header (required)
   * @return Call&lt;BlockHeader&gt;
   */
  @GET("blocks/{headerId}/header")
  Call<BlockHeader> getBlockHeaderById(
            @retrofit2.http.Path("headerId") String headerId            
  );

  /**
   * Get the block transactions info by a given signature
   * 
   * @param headerId ID of a wanted block transactions (required)
   * @return Call&lt;BlockTransactions&gt;
   */
  @GET("blocks/{headerId}/transactions")
  Call<BlockTransactions> getBlockTransactionsById(
            @retrofit2.http.Path("headerId") String headerId            
  );

  /**
   * Get headers in a specified range
   * 
   * @param fromHeight Min header height (optional)
   * @param toHeight Max header height (best header height by default) (optional)
   * @return Call&lt;List&lt;BlockHeader&gt;&gt;
   */
  @GET("blocks/chainSlice")
  Call<List<BlockHeader>> getChainSlice(
        @retrofit2.http.Query("fromHeight") Integer fromHeight                ,     @retrofit2.http.Query("toHeight") Integer toHeight                
  );

  /**
   * Get the header ids at a given height
   * 
   * @param blockHeight Height of a wanted block (required)
   * @return Call&lt;List&lt;String&gt;&gt;
   */
  @GET("blocks/at/{blockHeight}")
  Call<List<String>> getFullBlockAt(
            @retrofit2.http.Path("blockHeight") Integer blockHeight            
  );

  /**
   * Get the full block info by a given signature
   * 
   * @param headerId ID of a wanted block (required)
   * @return Call&lt;FullBlock&gt;
   */
  @GET("blocks/{headerId}")
  Call<FullBlock> getFullBlockById(
            @retrofit2.http.Path("headerId") String headerId            
  );

  /**
   * Get the Array of header ids
   * 
   * @param limit The number of items in list to return (optional)
   * @param offset The number of items in list to skip (optional)
   * @return Call&lt;List&lt;String&gt;&gt;
   */
  @GET("blocks")
  Call<List<String>> getHeaderIds(
        @retrofit2.http.Query("limit") Integer limit                ,     @retrofit2.http.Query("offset") Integer offset                
  );

  /**
   * Get the last headers objects
   * 
   * @param count count of a wanted block headers (required)
   * @return Call&lt;List&lt;BlockHeader&gt;&gt;
   */
  @GET("blocks/lastHeaders/{count}")
  Call<List<BlockHeader>> getLastHeaders(
            @retrofit2.http.Path("count") BigDecimal count            
  );

  /**
   * Get the persistent modifier by its id
   * 
   * @param modifierId ID of a wanted modifier (required)
   * @return Call&lt;Void&gt;
   */
  @GET("blocks/modifier/{modifierId}")
  Call<Void> getModifierById(
            @retrofit2.http.Path("modifierId") String modifierId            
  );

  /**
   * Send a mined block
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("blocks")
  Call<Void> sendMinedBlock(
                    @retrofit2.http.Body FullBlock body    
  );

}
