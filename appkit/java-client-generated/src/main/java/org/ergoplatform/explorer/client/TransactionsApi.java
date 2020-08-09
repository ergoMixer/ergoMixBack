package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.ergoplatform.explorer.client.model.FullTransaction;
import org.ergoplatform.explorer.client.model.Transaction;
import org.ergoplatform.explorer.client.model.TransactionIdResponse;
import org.ergoplatform.explorer.client.model.TransactionOutput;
import org.ergoplatform.explorer.client.model.UnconfirmedTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface TransactionsApi {
  /**
   * Send an Ergo transaction
   * 
   * @param body  (required)
   * @return Call&lt;TransactionIdResponse&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("transactions")
  Call<TransactionIdResponse> sendTransaction(
                    @retrofit2.http.Body FullTransaction body    
  );

  /**
   * Get all boxes containing given address
   * 
   * @param id  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/byAddress/{id}")
  Call<List<TransactionOutput>> transactionsBoxesByAddressIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get unspent boxes containing given address
   * 
   * @param id  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/byAddress/unspent/{id}")
  Call<List<TransactionOutput>> transactionsBoxesByAddressUnspentIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get all boxes containing given ergoTree
   * 
   * @param ergoTree  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/byErgoTree/{ergoTree}")
  Call<List<TransactionOutput>> transactionsBoxesByErgoTreeErgoTreeGet(
            @retrofit2.http.Path("ergoTree") String ergoTree            
  );

  /**
   * Get unspent boxes containing given ergoTree
   * 
   * @param ergoTree  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/byErgoTree/unspent/{ergoTree}")
  Call<List<TransactionOutput>> transactionsBoxesByErgoTreeUnspentErgoTreeGet(
            @retrofit2.http.Path("ergoTree") String ergoTree            
  );

  /**
   * Get unspent boxes protected by given ergoTreeTemplate
   *
   * @param ergoTreeTemplate  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/byErgoTreeTemplate/unspent/{ergoTreeTemplate}")
  Call<List<TransactionOutput>> transactionsBoxesByErgoTreeTemplateUnspentErgoTreeTemplateGet(
          @retrofit2.http.Path("ergoTreeTemplate") String ergoTreeTemplate
  );

  /**
   * Get box by id
   * 
   * @param id  (required)
   * @return Call&lt;TransactionOutput&gt;
   */
  @GET("transactions/boxes/{id}")
  Call<TransactionOutput> transactionsBoxesIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get transction by id
   * 
   * @param id  (required)
   * @return Call&lt;FullTransaction&gt;
   */
  @GET("transactions/{id}")
  Call<FullTransaction> transactionsIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get all transactions appeared in the main-chain after a given {height}
   * 
   * @param height  (required)
   * @return Call&lt;Transaction&gt;
   */
  @GET("transactions/since/{height}")
  Call<List<Transaction>> transactionsSinceHeightGet(
            @retrofit2.http.Path("height") Integer height            
  );

  /**
   * Get unconfirmed transactions containing outputs to a specified address
   * 
   * @param id Address ID (required)
   * @return Call&lt;List&lt;UnconfirmedTransaction&gt;&gt;
   */
  @GET("transactions/unconfirmed/byAddress/{id}")
  Call<List<UnconfirmedTransaction>> transactionsUnconfirmedByAddressIdGet(
            @retrofit2.http.Path("id") String id            
  );

  /**
   * Get unconfirmed transactions
   * 
   * @return Call&lt;List&lt;UnconfirmedTransaction&gt;&gt;
   */
  @GET("transactions/unconfirmed")
  Call<List<UnconfirmedTransaction>> transactionsUnconfirmedGet();
    

  /**
   * Get unconfirmed transaction by id
   * 
   * @param id  (required)
   * @return Call&lt;UnconfirmedTransaction&gt;
   */
  @GET("transactions/unconfirmed/{id}")
  Call<UnconfirmedTransaction> transactionsUnconfirmedIdGet(
            @retrofit2.http.Path("id") String id            
  );

}
