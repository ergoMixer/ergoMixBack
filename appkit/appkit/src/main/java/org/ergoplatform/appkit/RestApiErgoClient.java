package org.ergoplatform.appkit;

import org.ergoplatform.appkit.config.ErgoNodeConfig;
import org.ergoplatform.appkit.impl.BlockchainContextBuilderImpl;
import org.ergoplatform.explorer.client.ExplorerApiClient;
import org.ergoplatform.restapi.client.ApiClient;

import java.util.function.Function;

/**
 * This implementation of {@link ErgoClient} uses REST API of Ergo node for communication.
 */
public class RestApiErgoClient implements ErgoClient {
    private final String _nodeUrl;
    private final NetworkType _networkType;
    private final ApiClient _client;
    private final ExplorerApiClient _explorer;

    /**
     * Create and initialize a new instance.
     *
     * @param nodeUrl     http url to Ergo node REST API endpoint of the form `https://host:port/`.
     * @param networkType type of network (mainnet, testnet) the Ergo node is part of
     * @param apiKey      api key to authenticate this client
     */
    RestApiErgoClient(String nodeUrl, NetworkType networkType, String apiKey) {
        _nodeUrl = nodeUrl;
        _networkType = networkType;
        _client = new ApiClient(_nodeUrl, "ApiKeyAuth", apiKey);
        switch (networkType) {
        case MAINNET:
            _explorer = new ExplorerApiClient("https://api.ergoplatform.com/api/v0");
            break;
        default:
            _explorer = new ExplorerApiClient("https://api-testnet.ergoplatform.com/api/v0");
        }
    }

    /**
     * Create and initialize a new instance.
     *
     * @param nodeUrl     http url to Ergo node REST API endpoint of the form `https://host:port/`.
     * @param networkType type of network (mainnet, testnet) the Ergo node is part of
     * @param apiKey      api key to authenticate this client
     * @param explorerUrl HTTP URL for the explorer of network Ergo
     */
    RestApiErgoClient(String nodeUrl, NetworkType networkType, String apiKey, String explorerUrl) {
        _nodeUrl = nodeUrl;
        _networkType = networkType;
        _client = new ApiClient(_nodeUrl, "ApiKeyAuth", apiKey);
        _explorer = new ExplorerApiClient(explorerUrl);
    }

    @Override
    public <T> T execute(Function<BlockchainContext, T> action) {
        BlockchainContext ctx = new BlockchainContextBuilderImpl(_client, _explorer, _networkType).build();
        T res = action.apply(ctx);
        return res;
    }


    /**
     * Creates a new {@link ErgoClient} instance connected to a given node of the given network type.
     *
     * @param nodeUrl     http url to Ergo node REST API endpoint of the form `https://host:port/`
     * @param networkType type of network (mainnet, testnet) the Ergo node is part of
     * @param apiKey      api key to authenticate this client
     * @param explorerUrl HTTP URL for the explorer of network Ergo
     * @return a new instance of {@link ErgoClient} connected to a given node
     */
    public static ErgoClient create(String nodeUrl, NetworkType networkType, String apiKey, String explorerUrl) {
        return new RestApiErgoClient(nodeUrl, networkType, apiKey, explorerUrl);
    }

    /**
     * Creates a new {@link ErgoClient} instance connected to a given node of the given network type.
     *
     * @param nodeUrl     http url to Ergo node REST API endpoint of the form `https://host:port/`
     * @param networkType type of network (mainnet, testnet) the Ergo node is part of
     * @param apiKey      api key to authenticate this client
     * @return a new instance of {@link ErgoClient} connected to a given node
     */
    public static ErgoClient create(String nodeUrl, NetworkType networkType, String apiKey) {
        return new RestApiErgoClient(nodeUrl, networkType, apiKey);
    }

    /**
     * Create a new {@link ErgoClient} instance using node configuration parameters.
     */
    public static ErgoClient create(ErgoNodeConfig nodeConf) {
        return RestApiErgoClient.create(
                nodeConf.getNodeApi().getApiUrl(),
                nodeConf.getNetworkType(),
                nodeConf.getNodeApi().getApiKey());
    }

    /**
     * Get underlying Ergo node REST API typed client.
     */
    ApiClient getNodeApiClient() {
        return _client;
    }

    /**
     * Get underlying Ergo Network Explorer REST API typed client.
     */
    ExplorerApiClient getExplorerApiClient() {
        return _explorer;
    }

}
