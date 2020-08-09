package org.ergoplatform.appkit.examples;

import org.ergoplatform.appkit.*;

import java.util.Arrays;

import static org.ergoplatform.appkit.Parameters.MinFee;

/**
 * Examples demonstrating usage of blockchain client API.
 */
public class ExampleScenarios {

    private final BlockchainContext _ctx;

    /**
     * @param ctx blockchain context to be used to create the new transaction
     */
    public ExampleScenarios(BlockchainContext ctx) {
        _ctx = ctx;
    }

    /**
     * Example scenario creating and signing transaction that spends given boxes and aggregate
     * their ERGs into single new box protected with simple deadline based contract.
     *
     * @param storageFile storage with secret key of the sender
     * @param storagePass password to access sender secret key in the storage
     * @param deadline    deadline (blockchain height) after which the newly created box can be spent
     * @param boxIds      string encoded (base16) ids of the boxes to be spent and agregated into the new box.
     */
    public SignedTransaction aggregateUtxoBoxes(
            String storageFile, SecretString storagePass, String changeAddr, int deadline, String... boxIds) {
        UnsignedTransactionBuilder txB = _ctx.newTxBuilder();
        InputBox[] boxes = _ctx.getBoxesById(boxIds);
        Long total = Arrays.stream(boxes).map(b -> b.getValue()).reduce(0L, (x, y) -> x + y);
        UnsignedTransaction tx = txB
                .boxesToSpend(Arrays.asList(boxes))
                .outputs(
                        txB.outBoxBuilder()
                                .value(total - MinFee)
                                .contract(
                                        _ctx.compileContract(
                                                ConstantsBuilder.create().item("deadline", deadline).build(),
                                                "{ HEIGHT > deadline }"))
                                .build())
                .fee(MinFee)
                .sendChangeTo(Address.create(changeAddr).getErgoAddress())
                .build();

        ErgoProver prover = BoxOperations.createProver(_ctx, storageFile, storagePass.toStringUnsecure()).build();
        SignedTransaction signed = prover.sign(tx);
        return signed;
    }

    /**
     * Example scenario which: 1) creates a mock box with the given script and
     * 2) use it as an input to a new transaction.
     * The new transaction is then signed using given seed phrase.
     *
     * @param mockTxId    string encoded id (base16) of a transaction which is used for the mock box.
     * @param outputIndex index of the mock box in the mock transaction
     * @param constants   named constants used in the script
     * @param ergoScript  source code of the script
     * @param seedPhrase  seed phrase to use for signature
     */
    public SignedTransaction prepareBox(
            String mockTxId, short outputIndex, Constants constants, String ergoScript,
            SecretString seedPhrase) {
        UnsignedTransactionBuilder mockTxB = _ctx.newTxBuilder();
        OutBox out = mockTxB.outBoxBuilder()
                .contract(_ctx.compileContract(constants, ergoScript))
                .build();
        UnsignedTransactionBuilder spendingTxB = _ctx.newTxBuilder();
        UnsignedTransaction tx = spendingTxB
                .boxesToSpend(Arrays.asList(out.convertToInputWith(mockTxId, outputIndex)))
                .outputs(
                        spendingTxB.outBoxBuilder()
                                .contract(_ctx.compileContract(ConstantsBuilder.empty(), "{ false }"))
                                .registers(ErgoValue.of(10))
                                .build())
                .build();
        ErgoProverBuilder proverB = _ctx.newProverBuilder();
        ErgoProver prover = proverB.withMnemonic(seedPhrase, null).build();
        SignedTransaction signed = prover.sign(tx);
        return signed;
    }
}
