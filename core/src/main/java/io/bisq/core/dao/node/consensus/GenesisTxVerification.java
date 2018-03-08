/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxType;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Verifies if a given transaction is a BSQ genesis transaction.
 */
public class GenesisTxVerification {

    private final BsqBlockChain bsqBlockChain;
    private final String genesisTxId;
    private final int genesisBlockHeight;

    @Inject
    public GenesisTxVerification(BsqBlockChain bsqBlockChain,
                                 @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                                 @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.bsqBlockChain = bsqBlockChain;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
    }

    public boolean isGenesisTx(Tx tx, int blockHeight) {
        if (tx.getId().equals(genesisTxId) && blockHeight == genesisBlockHeight) {
            tx.getOutputs().forEach(txOutput -> {
                txOutput.setUnspent(true);
                txOutput.setVerified(true);
                bsqBlockChain.addUnspentTxOutput(txOutput);
            });
            tx.setTxType(TxType.GENESIS);

            bsqBlockChain.setGenesisTx(tx);
            bsqBlockChain.addTxToMap(tx);
            return true;
        } else {
            return false;
        }
    }
}