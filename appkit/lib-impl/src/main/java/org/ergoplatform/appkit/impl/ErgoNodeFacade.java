package org.ergoplatform.appkit.impl;

import org.ergoplatform.appkit.ErgoClientException;
import org.ergoplatform.restapi.client.*;
import retrofit2.Retrofit;
import retrofit2.RetrofitUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

/**
 * This class implements typed facade with Ergo node API invocation methods.
 * It allows to bypass dynamic {@link java.lang.reflect.Proxy } generation which doesn't work under
 * Graal native-image.
 */
public class ErgoNodeFacade extends ApiFacade {
    /**
     * Get the information about the Node
     *
     * @return Call&lt;NodeInfo&gt;
     */
    static public NodeInfo getNodeInfo(Retrofit r) throws ErgoClientException {
        return execute(r, () -> {
            Method method = InfoApi.class.getMethod("getNodeInfo");
            NodeInfo res = RetrofitUtil.<NodeInfo>invokeServiceMethod(r, method, null).execute().body();
            return res;
        });
    }

    /**
     * Get the last headers objects
     *
     * @param count count of a wanted block headers (required)
     * @return List&lt;BlockHeader&gt;
     */
    static public List<BlockHeader> getLastHeaders(Retrofit r, BigDecimal count) throws ErgoClientException {
        return execute(r, () -> {
            Method method = BlocksApi.class.getMethod("getLastHeaders", BigDecimal.class);
            List<BlockHeader> res = RetrofitUtil.<List<BlockHeader>>invokeServiceMethod(r, method,
                    new Object[]{count}).execute().body();
            return res;
        });
    }

    /**
     * Get box contents for a box by a unique identifier.
     *
     * @param boxId ID of a wanted box (required)
     * @return ErgoTransactionOutput
     */
    static public ErgoTransactionOutput getBoxById(Retrofit r, String boxId) throws ErgoClientException {
        return execute(r, () -> {
            Method method = UtxoApi.class.getMethod("getBoxById", String.class);
            ErgoTransactionOutput res = RetrofitUtil.<ErgoTransactionOutput>invokeServiceMethod(r, method,
                    new Object[]{boxId}).execute().body();
            return res;
        });
    }

    /**
     * Get a list of unspent boxes  @GET("wallet/boxes/unspent")
     *
     * @param minConfirmations   Minimal number of confirmations (optional)
     * @param minInclusionHeight Minimal box inclusion height (optional)
     * @return List&lt;WalletBox&gt;
     */
    static public List<WalletBox> getWalletUnspentBoxes(
            Retrofit r, Integer minConfirmations, Integer minInclusionHeight) throws ErgoClientException {
        return execute(r, () -> {
            Method method = WalletApi.class.getMethod("walletUnspentBoxes", Integer.class, Integer.class);
            List<WalletBox> res = RetrofitUtil.<List<WalletBox>>invokeServiceMethod(r, method,
                    new Object[]{minConfirmations, minInclusionHeight}).execute().body();
            return res;
        });
    }

    /**
     * Send an Ergo transaction
     * Headers({ "Content-Type:application/json" })
     * POST("transactions")
     *
     * @param tx signed transaction to be posted to the blockchain
     * @return transaction id of the submitted transaction
     */
    static public String sendTransaction(
            Retrofit r, ErgoTransaction tx) throws ErgoClientException {
        return execute(r, () -> {
            Method method = TransactionsApi.class.getMethod("sendTransaction", ErgoTransaction.class);
            String txId = RetrofitUtil.<String>invokeServiceMethod(r, method,
                    new Object[]{tx}).execute().body();
            return txId;
        });
    }

}

