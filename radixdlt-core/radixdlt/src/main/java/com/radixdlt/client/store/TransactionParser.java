/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.store;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.client.api.ActionEntry;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TransactionParser {
	private UInt256 computeFeePaid(REParsedTxn radixEngineTxn) {
		return radixEngineTxn.getActions()
			.stream()
			.map(REParsedAction::getTxAction)
			.filter(BurnToken.class::isInstance)
			.map(BurnToken.class::cast)
			.filter(t -> t.resourceAddr().isNativeToken())
			.map(BurnToken::amount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	private ActionEntry mapToEntry(TxAction txAction, Function<REAddr, String> addrToRri) {
		if (txAction instanceof TransferToken) {
			return ActionEntry.transfer((TransferToken) txAction, addrToRri);
		} else if (txAction instanceof BurnToken) {
			var burnToken = (BurnToken) txAction;
			return ActionEntry.burn(burnToken, addrToRri);
		} else if (txAction instanceof StakeTokens) {
			return ActionEntry.stake((StakeTokens) txAction, addrToRri);
		} else if (txAction instanceof UnstakeTokens) {
			return ActionEntry.unstake((UnstakeTokens) txAction, addrToRri);
		} else {
			return ActionEntry.unknown();
		}
	}

	private boolean isFeeAction(TxAction action) {
		return (action instanceof BurnToken) && ((BurnToken) action).resourceAddr().isNativeToken();
	}

	public Result<TxHistoryEntry> parse(REParsedTxn parsedTxn, Instant txDate, Function<REAddr, String> addrToRri) {
		var txnId = parsedTxn.getTxn().getId();
		var fee = computeFeePaid(parsedTxn);

		var actions = parsedTxn.getActions().stream()
			.map(REParsedAction::getTxAction)
			.filter(a -> !isFeeAction(a))
			.map(a -> mapToEntry(a, addrToRri))
			.collect(Collectors.toList());

		return Result.ok(TxHistoryEntry.create(txnId, txDate, fee, null, actions));
	}
}
