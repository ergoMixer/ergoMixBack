package org.ergoplatform.appkit;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.ergoplatform.appkit.impl.BlockchainContextBuilderImpl;
import org.ergoplatform.explorer.client.ExplorerApiClient;
import org.ergoplatform.restapi.client.ApiClient;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * MockedRunner using given files to provide BlockchainContext information.
 */
public class FileMockedErgoClient implements MockedErgoClient {

    private final List<String> _nodeResponses;
    private final List<String> _explorerResponses;

    public FileMockedErgoClient(List<String> nodeResponses, List<String> explorerResponses) {
        _nodeResponses = nodeResponses;
        _explorerResponses = explorerResponses;
    }

    @Override
    public List<String> getNodeResponses() {
        return _nodeResponses;
    }

    @Override
    public List<String> getExplorerResponses() {
        return _explorerResponses;
    }

    void enqueueResponses(MockWebServer server, List<String> rs) {
        for (String r : rs) {
            server.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(r));
        }
    }

    @Override
    public <T> T execute(Function<BlockchainContext, T> action) {
        MockWebServer node = new MockWebServer();
        enqueueResponses(node, _nodeResponses);

        MockWebServer explorer = new MockWebServer();
        enqueueResponses(explorer, _explorerResponses);

        try {
            node.start();
            explorer.start();
        } catch (IOException e) {
            throw new ErgoClientException("Cannot start server " + node.toString(), e);
        }

        HttpUrl baseUrl = node.url("/");
        ApiClient client = new ApiClient(baseUrl.toString());
        HttpUrl explorerBaseUrl = explorer.url("/");
        ExplorerApiClient explorerClient = new ExplorerApiClient(explorerBaseUrl.toString());

        BlockchainContext ctx =
                new BlockchainContextBuilderImpl(client, explorerClient, NetworkType.MAINNET).build();

        T res = action.apply(ctx);

        try {
            explorer.shutdown();
            node.shutdown();
        } catch (IOException e) {
            throw new ErgoClientException("Cannot shutdown server " + node.toString(), e);
        }
        return res;
    }
}

