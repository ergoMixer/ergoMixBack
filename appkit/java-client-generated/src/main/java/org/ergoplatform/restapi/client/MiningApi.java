package org.ergoplatform.restapi.client;

import retrofit2.Call;
import retrofit2.http.*;

public interface MiningApi {
  /**
   * Read miner reward address
   * 
   * @return Call&lt;InlineResponse2004&gt;
   */
  @GET("mining/rewardAddress")
  Call<InlineResponse2004> miningReadMinerRewardAddress();
    

  /**
   * Request block candidate
   * 
   * @return Call&lt;ExternalCandidateBlock&gt;
   */
  @GET("mining/candidate")
  Call<ExternalCandidateBlock> miningRequestBlockCandidate();
    

  /**
   * Submit solution for current candidate
   * 
   * @param body  (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("mining/solution")
  Call<Void> miningSubmitSolution(
                    @retrofit2.http.Body PowSolutions body    
  );

}
