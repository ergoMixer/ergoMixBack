package org.ergoplatform.explorer.client;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.ergoplatform.explorer.client.model.InlineResponse2002;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface UtilitiesApi {
  /**
   * Search block, transactions, adresses
   * 
   * @param query Search query (optional)
   * @return Call&lt;InlineResponse2002&gt;
   */
  @GET("search")
  Call<InlineResponse2002> searchGet(
        @retrofit2.http.Query("query") String query                
  );

}
