package org.ergoplatform.appkit.impl;

import org.ergoplatform.appkit.ErgoClientException;
import org.ergoplatform.explorer.client.TransactionsApi;
import org.ergoplatform.explorer.client.model.TransactionOutput;
import retrofit2.Retrofit;
import retrofit2.RetrofitUtil;

import java.lang.reflect.Method;
import java.util.List;

/**
 * This class implements typed facade with Ergo Explorer API invocation methods.
 * It allows to bypass dynamic {@link java.lang.reflect.Proxy } generation which doesn't work under
 * Graal native-image.
 */
public class ExplorerFacade extends ApiFacade {
    /**
     * Get unspent boxes containing given address @GET("transactions/boxes/byAddress/unspent/{id}")
     *
     * @param id  (required)
     * @return TransactionOutput
     */
    static public List<TransactionOutput> transactionsBoxesByAddressUnspentIdGet(
            Retrofit r, String id) throws ErgoClientException {
        return execute(r, () -> {
            Method method = TransactionsApi.class.getMethod("transactionsBoxesByAddressUnspentIdGet", String.class);
            List<TransactionOutput> res =
                    RetrofitUtil.<List<TransactionOutput>>invokeServiceMethod(r, method, new Object[]{id})
                            .execute().body();
            return res;
        });
    }

    /**
     * Get unspent boxes protected by given contract template @GET("transactions/boxes/byErgoTreeTemplate/unspent/{ergoTreeTemplate}")
     *
     * @param ergoTreeTemplate (required)
     * @return TransactionOutput
     */
    static public List<TransactionOutput> transactionsBoxesByErgoTreeTemplateUnspentErgoTreeTemplateGet(
            Retrofit r, String ergoTreeTemplate) throws ErgoClientException {
        return execute(r, () -> {
            Method method = TransactionsApi.class
                    .getMethod("transactionsBoxesByErgoTreeTemplateUnspentErgoTreeTemplateGet",
                            String.class);
            List<TransactionOutput> res =
                    RetrofitUtil.<List<TransactionOutput>>invokeServiceMethod(r, method, new Object[]{ergoTreeTemplate})
                            .execute().body();
            return res;
        });
    }

}
