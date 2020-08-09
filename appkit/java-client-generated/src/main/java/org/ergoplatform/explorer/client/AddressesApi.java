package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.ergoplatform.explorer.client.model.FullAddress;
import org.ergoplatform.explorer.client.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AddressesApi {
  /**
   * Get addresses holding an asset with a given id
   * 
   * @param id Asset ID (required)
   * @param offset Offset (optional)
   * @param limit Limit (optional)
   * @return Call&lt;List&lt;String&gt;&gt;
   */
  @GET("addresses/assetHolders/{id}")
  Call<List<String>> addressesAssetHoldersIdGet(
            @retrofit2.http.Path("id") String id            ,     @retrofit2.http.Query("offset") Integer offset                ,     @retrofit2.http.Query("limit") Integer limit                
  );

  /**
   * Get address by id
   * 
   * @param id Address ID (required)
   * @return Call&lt;FullAddress&gt;
   */
  @GET("addresses/{id}")
  Call<FullAddress> addressesIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get transactions related to address
   * 
   * @param id Address ID (required)
   * @param offset Offset (optional)
   * @param limit Limit (optional)
   * @return Call&lt;List&lt;Transaction&gt;&gt;
   */
  @GET("addresses/{id}/transactions")
  Call<List<Transaction>> addressesIdTransactionsGet(
            @retrofit2.http.Path("id") String id            ,     @retrofit2.http.Query("offset") Integer offset                ,     @retrofit2.http.Query("limit") Integer limit                
  );

}
