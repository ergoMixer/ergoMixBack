package org.ergoplatform.appkit.impl;

import okhttp3.OkHttpClient;
import org.ergoplatform.appkit.BlockchainContext;
import org.ergoplatform.appkit.BlockchainContextBuilder;
import org.ergoplatform.appkit.ErgoClientException;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.explorer.client.ExplorerApiClient;
import org.ergoplatform.restapi.client.ApiClient;
import org.ergoplatform.restapi.client.BlockHeader;
import org.ergoplatform.restapi.client.NodeInfo;
import retrofit2.Retrofit;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class BlockchainContextBuilderImpl implements BlockchainContextBuilder {
    private final ApiClient _client;
    private ExplorerApiClient _explorer;
    private final NetworkType _networkType;
    private OkHttpClient _ok;
    private Retrofit _retrofit;
    private NodeInfo _nodeInfo;
    private List<BlockHeader> _headers;
    private OkHttpClient _okExplorer;
    private Retrofit _retrofitExplorer;

    public BlockchainContextBuilderImpl(
            ApiClient client, ExplorerApiClient explorer,
            NetworkType networkType) {
        _client = client;
        _explorer = explorer;
        _networkType = networkType;
    }

    @Override
    public BlockchainContext build() throws ErgoClientException {
        _ok = _client.getOkBuilder().build();
        _retrofit = _client.getAdapterBuilder()
                .client(_ok)
                .build();
        _okExplorer = _explorer.getOkBuilder().build();
        _retrofitExplorer = _explorer.getAdapterBuilder()
                .client(_ok)
                .build();

        _nodeInfo  = ErgoNodeFacade.getNodeInfo(_retrofit);
        _headers  = ErgoNodeFacade.getLastHeaders(_retrofit, BigDecimal.valueOf(NUM_LAST_HEADERS));
        Collections.reverse(_headers);
        
        return new BlockchainContextImpl(_client, _retrofit, _explorer, _retrofitExplorer, _networkType, _nodeInfo, _headers);
    }


}
