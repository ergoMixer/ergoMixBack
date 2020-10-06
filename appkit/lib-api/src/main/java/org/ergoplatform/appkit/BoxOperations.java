package org.ergoplatform.appkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ergoplatform.appkit.Parameters.MinFee;

/**
 * A collection of utility operations implemented in terms of abstract Appkit interfaces.
 */
public class BoxOperations {

    public static List<InputBox> selectTop(
            List<InputBox> unspentBoxes,
            long amountToSpend) {
        return selectTop(unspentBoxes, amountToSpend, Optional.empty());
    }

    public static List<InputBox> selectTop(
            List<InputBox> unspentBoxes,
            long amountToSpend,
            Optional<ErgoToken> tokenOpt) {
        if (amountToSpend == 0 && !tokenOpt.isPresent()) {
            // all unspent boxes are requested
            return unspentBoxes;
        }

        // collect boxes to cover requested amount of coins and tokens
        ArrayList<InputBox> res = new ArrayList<InputBox>();
        long collected = 0;
        long collectedTokens = 0;
        long tokenAmount = tokenOpt.map(ErgoToken::getValue).orElse(0L);
        for (int i = 0;
             i < unspentBoxes.size() &&
                     (collected < amountToSpend ||
                             (tokenOpt.isPresent() && collectedTokens < tokenAmount)
                     );
             ++i) {
            InputBox box = unspentBoxes.get(i);
            if (!tokenOpt.isPresent() && !box.getTokens().isEmpty()) {
                // skip boxes with tokens if token is not asked
                continue;
            }
            collected += box.getValue();
            long tokenAmountInBox = box.getTokens().stream()
                    .filter(t -> tokenOpt.isPresent() && t.getId().equals(tokenOpt.get().getId()))
                    .map(ErgoToken::getValue)
                    .reduce(0L, Long::sum);
            collectedTokens += tokenAmountInBox;
            res.add(box);
        }
        if (collected < amountToSpend)
            throw new RuntimeException("Not enough coins in boxes to pay " + amountToSpend);
        if (tokenOpt.isPresent() && collectedTokens < tokenAmount)
            throw new RuntimeException("Not enough tokens (id " + tokenOpt.get().getId().toString() + ") in" +
                    " boxes to pay " + tokenAmount + ", found only " + collectedTokens);
        return res;
    }

    public static ErgoProver createProver(BlockchainContext ctx, Mnemonic mnemonic) {
        ErgoProver prover = ctx.newProverBuilder()
                .withMnemonic(mnemonic.getPhrase(), mnemonic.getPassword())
                .build();
        return prover;
    }

    public static ErgoProverBuilder createProver(BlockchainContext ctx, String storageFile, SecretString storagePass) {
        return createProver(ctx, storageFile, storagePass.toStringUnsecure());
    }

    public static ErgoProverBuilder createProver(
            BlockchainContext ctx, String storageFile, String storagePass) {
        SecretStorage storage = SecretStorage.loadFrom(storageFile);
        storage.unlock(storagePass);
        ErgoProverBuilder proverB = ctx.newProverBuilder()
                .withSecretStorage(storage);
        return proverB;
    }

    public static String send(
            BlockchainContext ctx, ErgoProver senderProver, Address recipient, long amountToSend) {

        ErgoContract pkContract = ErgoContracts.sendToPK(ctx, recipient);
        SignedTransaction signed = putToContractTx(ctx, senderProver, pkContract, amountToSend);
        ctx.sendTransaction(signed);
        return signed.toJson(true);
    }

    public static List<InputBox> loadTop(BlockchainContext ctx, Address sender, long amount) {
        List<InputBox> unspent = ctx.getUnspentBoxesFor(sender);
        List<InputBox> selected = selectTop(unspent, amount);
        return selected;
    }

    public static SignedTransaction putToContractTx(
            BlockchainContext ctx, ErgoProver senderProver, ErgoContract contract, long amountToSend) {
        Address sender = senderProver.getAddress();
        List<InputBox> boxesToSpend = loadTop(ctx, sender, amountToSend + MinFee);

        UnsignedTransactionBuilder txB = ctx.newTxBuilder();
        OutBox newBox = txB.outBoxBuilder()
                .value(amountToSend)
                .contract(contract)
                .build();
        UnsignedTransaction tx = txB.boxesToSpend(boxesToSpend)
                .outputs(newBox)
                .fee(Parameters.MinFee)
                .sendChangeTo(senderProver.getP2PKAddress())
                .build();

        SignedTransaction signed = senderProver.sign(tx);
        return signed;
    }

    public static SignedTransaction spendBoxesTx(
            BlockchainContext ctx,
            UnsignedTransactionBuilder txB,
            List<InputBox> boxes,
            ErgoProver sender, Address recipient, long amount, long fee) {
        OutBox aliceBox = txB.outBoxBuilder()
                .value(amount)
                .contract(ErgoContracts.sendToPK(ctx, recipient))
                .build();

        UnsignedTransaction tx = txB.boxesToSpend(boxes)
                .outputs(aliceBox)
                .fee(fee)
                .sendChangeTo(sender.getP2PKAddress())
                .build();
        SignedTransaction signed = sender.sign(tx);
        return signed;
    }


}
