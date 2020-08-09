package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.math.BigDecimal;
import org.ergoplatform.explorer.client.model.BlockchainInfo;
import org.ergoplatform.explorer.client.model.BlockchainStats;
import org.ergoplatform.explorer.client.model.ForksInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface StatisticsApi {
  /**
   * Get blockchain info
   * 
   * @return Call&lt;BlockchainInfo&gt;
   */
  @GET("info")
  Call<BlockchainInfo> infoGet();
    

  /**
   * Get current supply
   * 
   * @return Call&lt;BigDecimal&gt;
   */
  @GET("info/supply")
  Call<BigDecimal> infoSupplyGet();
    

  /**
   * Forks info summary
   * 
   * @param fromHeight Height to display forks from. Forks from last epoch are displayed by default. (optional)
   * @return Call&lt;ForksInfo&gt;
   */
  @GET("stats/forks")
  Call<ForksInfo> statsForksGet(
        @retrofit2.http.Query("fromHeight") Integer fromHeight                
  );

  /**
   * Get blockchain stats
   * 
   * @return Call&lt;BlockchainStats&gt;
   */
  @GET("stats")
  Call<BlockchainStats> statsGet();
    

}
