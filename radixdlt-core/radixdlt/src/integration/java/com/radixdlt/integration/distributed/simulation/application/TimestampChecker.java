/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.simulation.application;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;

import java.time.Duration;

/**
 * Checks that the first time a ledger update occurs on the network that it is close
 * to the real wall clock time.
 */
public final class TimestampChecker implements TestInvariant {

	private final Duration acceptableTimeRange;

	public TimestampChecker(Duration acceptableTimeRange) {
		this.acceptableTimeRange = acceptableTimeRange;
	}

	private Maybe<TestInvariantError> checkCloseTimestamp(LedgerUpdate update) {
		final var now = System.currentTimeMillis();
		final var proof = update.getTail();
		final var timestamp = proof.timestamp();
		final var diff = now - timestamp;
		if (0 <= diff && diff < acceptableTimeRange.toMillis()) {
			return Maybe.empty();
		} else {
			return Maybe.just(
				new TestInvariantError(
					String.format(
						"Expecting timestamp to be close to %s but was %s%+d at %s:%s with %s",
						now, now, diff, proof.getEpoch(), proof.getView(), update
					)
				)
			);
		}
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return network.ledgerUpdates()
			.map(Pair::getSecond)
			.distinct() // Test on only the first ledger update in the network
			.filter(l -> !(l.getTail().getEpoch() == 1 && l.getTail().getView().equals(View.of(1))))
			.flatMapMaybe(update -> this.checkCloseTimestamp(update));
	}
}
