package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.ergoplatform.explorer.client.model.InlineResponse20010;
import org.ergoplatform.explorer.client.model.InlineResponse2003;
import org.ergoplatform.explorer.client.model.InlineResponse2004;
import org.ergoplatform.explorer.client.model.InlineResponse2005;
import org.ergoplatform.explorer.client.model.InlineResponse2006;
import org.ergoplatform.explorer.client.model.InlineResponse2007;
import org.ergoplatform.explorer.client.model.InlineResponse2008;
import org.ergoplatform.explorer.client.model.InlineResponse2009;
import org.ergoplatform.explorer.client.model.Timespan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ChartsApi {
  /**
   * Get average block size per date
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse20010&gt;&gt;
   */
  @GET("charts/block-size")
  Call<List<InlineResponse20010>> chartsBlockSizeGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * The total size of all block headers and transactions per day.
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2004&gt;&gt;
   */
  @GET("charts/blockchain-size")
  Call<List<InlineResponse2004>> chartsBlockchainSizeGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * Difficulty per day.
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2007&gt;&gt;
   */
  @GET("charts/difficulty")
  Call<List<InlineResponse2007>> chartsDifficultyGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * An estimation of hashrate distribution amongst the largest mining pools
   * 
   * @return Call&lt;List&lt;InlineResponse2009&gt;&gt;
   */
  @GET("charts/hash-rate-distribution")
  Call<List<InlineResponse2009>> chartsHashRateDistributionGet();
    

  /**
   * Hash Rate per day.
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2006&gt;&gt;
   */
  @GET("charts/hash-rate")
  Call<List<InlineResponse2006>> chartsHashRateGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * Total value of coinbase block rewards and transaction fees paid to miners per day.
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2008&gt;&gt;
   */
  @GET("charts/miners-revenue")
  Call<List<InlineResponse2008>> chartsMinersRevenueGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * Get total sum of coins mined per day
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2003&gt;&gt;
   */
  @GET("charts/total")
  Call<List<InlineResponse2003>> chartsTotalGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

  /**
   * Transactions per block per day.
   * 
   * @param timespan Timespan (optional)
   * @return Call&lt;List&lt;InlineResponse2005&gt;&gt;
   */
  @GET("charts/transactions-per-block")
  Call<List<InlineResponse2005>> chartsTransactionsPerBlockGet(
        @retrofit2.http.Query("timespan") Timespan timespan                
  );

}
