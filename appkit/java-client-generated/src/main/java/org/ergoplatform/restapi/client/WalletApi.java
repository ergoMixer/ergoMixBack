package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface WalletApi {
  /**
   * Get wallet status
   * 
   * @return Call&lt;InlineResponse2001&gt;
   */
  @GET("wallet/status")
  Call<InlineResponse2001> getWalletStatus();
    

  /**
   * Get wallet addresses
   * 
   * @return Call&lt;List&lt;String&gt;&gt;
   */
  @GET("wallet/addresses")
  Call<List<String>> walletAddresses();
    

  /**
   * Get total amount of confirmed Ergo tokens and assets
   * 
   * @return Call&lt;BalancesSnapshot&gt;
   */
  @GET("wallet/balances")
  Call<BalancesSnapshot> walletBalances();
    

  /**
   * Get summary amount of confirmed plus unconfirmed Ergo tokens and assets
   * 
   * @return Call&lt;BalancesSnapshot&gt;
   */
  @GET("wallet/balances/withUnconfirmed")
  Call<BalancesSnapshot> walletBalancesUnconfirmed();
    

  /**
   * Get a list of all wallet-related boxes
   * 
   * @param minConfirmations Minimal number of confirmations (optional)
   * @param minInclusionHeight Minimal box inclusion height (optional)
   * @return Call&lt;List&lt;WalletBox&gt;&gt;
   */
  @GET("wallet/boxes")
  Call<List<WalletBox>> walletBoxes(
        @retrofit2.http.Query("minConfirmations") Integer minConfirmations                ,     @retrofit2.http.Query("minInclusionHeight") Integer minInclusionHeight                
  );

  /**
   * Derive new key according to a provided path
   * 
   * @param body  (required)
   * @return Call&lt;InlineResponse2002&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/deriveKey")
  Call<InlineResponse2002> walletDeriveKey(
                    @retrofit2.http.Body Body4 body    
  );

  /**
   * Derive next key
   * 
   * @return Call&lt;InlineResponse2003&gt;
   */
  @GET("wallet/deriveNextKey")
  Call<InlineResponse2003> walletDeriveNextKey();
    

  /**
   * Get wallet-related transaction by id
   * 
   * @param id Transaction id (required)
   * @return Call&lt;List&lt;WalletTransaction&gt;&gt;
   */
  @GET("wallet/transactionById")
  Call<List<WalletTransaction>> walletGetTransaction(
        @retrofit2.http.Query("id") String id                
  );

  /**
   * Initialize new wallet with randomly generated seed
   * 
   * @param body  (required)
   * @return Call&lt;InlineResponse200&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/init")
  Call<InlineResponse200> walletInit(
                    @retrofit2.http.Body Body body    
  );

  /**
   * Lock wallet
   * 
   * @return Call&lt;Void&gt;
   */
  @GET("wallet/lock")
  Call<Void> walletLock();
    

  /**
   * Generate and send payment transaction (default fee of 0.001 Erg is used)
   * 
   * @param body  (required)
   * @return Call&lt;String&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/payment/send")
  Call<String> walletPaymentTransactionGenerateAndSend(
                    @retrofit2.http.Body List<PaymentRequest> body    
  );

  /**
   * Create new wallet from existing mnemonic seed
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/restore")
  Call<Void> walletRestore(
                    @retrofit2.http.Body Body1 body    
  );

  /**
   * Generate arbitrary transaction from array of requests.
   * 
   * @param body This API method receives a sequence of requests as an input. Each request will produce an output of the resulting transaction (with fee output created automatically). Currently supported types of requests are payment and asset issuance requests. An example for a transaction with requests of both kinds is provided below. Please note that for the payment request &quot;assets&quot; and &quot;registers&quot; fields are not needed. For asset issuance request, &quot;registers&quot; field is not needed.
You may specify boxes to spend by providing them in &quot;inputsRaw&quot;. Please note you need to have strict equality between input and output total amounts of Ergs in this case. If you want wallet to pick up the boxes, leave &quot;inputsRaw&quot; empty. (required)
   * @return Call&lt;ErgoTransaction&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/transaction/generate")
  Call<ErgoTransaction> walletTransactionGenerate(
                    @retrofit2.http.Body RequestsHolder body    
  );

  /**
   * Generate and send arbitrary transaction
   * 
   * @param body See description of /wallet/transaction/generate (required)
   * @return Call&lt;String&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/transaction/send")
  Call<String> walletTransactionGenerateAndSend(
                    @retrofit2.http.Body RequestsHolder body    
  );

  /**
   * Get a list of all wallet-related transactions
   * 
   * @param minInclusionHeight Minimal tx inclusion height (optional)
   * @param maxInclusionHeight Maximal tx inclusion height (optional)
   * @param minConfirmations Minimal confirmations number (optional)
   * @param maxConfirmations Maximal confirmations number (optional)
   * @return Call&lt;List&lt;WalletTransaction&gt;&gt;
   */
  @GET("wallet/transactions")
  Call<List<WalletTransaction>> walletTransactions(
        @retrofit2.http.Query("minInclusionHeight") Integer minInclusionHeight                ,     @retrofit2.http.Query("maxInclusionHeight") Integer maxInclusionHeight                ,     @retrofit2.http.Query("minConfirmations") Integer minConfirmations                ,     @retrofit2.http.Query("maxConfirmations") Integer maxConfirmations                
  );

  /**
   * Unlock wallet
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/unlock")
  Call<Void> walletUnlock(
                    @retrofit2.http.Body Body2 body    
  );

  /**
   * Get a list of unspent boxes
   * 
   * @param minConfirmations Minimal number of confirmations (optional)
   * @param minInclusionHeight Minimal box inclusion height (optional)
   * @return Call&lt;List&lt;WalletBox&gt;&gt;
   */
  @GET("wallet/boxes/unspent")
  Call<List<WalletBox>> walletUnspentBoxes(
        @retrofit2.http.Query("minConfirmations") Integer minConfirmations                ,     @retrofit2.http.Query("minInclusionHeight") Integer minInclusionHeight                
  );

  /**
   * Update address to be used to send change to
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("wallet/updateChangeAddress")
  Call<Void> walletUpdateChangeAddress(
                    @retrofit2.http.Body Body3 body    
  );

}
