package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

public interface TransactionsApi {
  /**
   * Get current pool of the unconfirmed transactions pool
   * 
   * @param limit The number of items in list to return (optional)
   * @param offset The number of items in list to skip (optional)
   * @return Call&lt;Transactions&gt;
   */
  @GET("transactions/unconfirmed")
  Call<Transactions> getUnconfirmedTransactions(
        @retrofit2.http.Query("limit") Integer limit                ,     @retrofit2.http.Query("offset") Integer offset                
  );

  /**
   * Send an Ergo transaction
   * 
   * @param body  (required)
   * @return Call&lt;String&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("transactions")
  Call<String> sendTransaction(
                    @retrofit2.http.Body ErgoTransaction body    
  );

}
