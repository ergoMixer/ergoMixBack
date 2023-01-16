package app;

import org.ergoplatform.appkit.ErgoClient;

import java.util.List;

/**
 * This interface is used to represent {@link ErgoClient} whose communication with
 * Ergo network node and explorer can be mocked with some pre-defined test data.
 * This interface can be implemented in different ways, depending on the source
 * of the test data.
 * This interface allows to abstract testing code from a concrete decision of how
 * to provide the test data.
 */
public interface MockedErgoClient extends ErgoClient {
    /**
     * Response content for mocked responses from Ergo node REST API.
     */
    List<String> getNodeResponses();

    /**
     * Response content for mocked responses from Ergo Explorer REST API.
     */
    List<String> getExplorerResponses();
}
