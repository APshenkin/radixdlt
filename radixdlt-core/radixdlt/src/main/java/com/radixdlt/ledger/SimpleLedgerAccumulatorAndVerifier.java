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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.crypto.Hasher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Hash chain accumulator and verifier
 */
@ThreadSafe
public class SimpleLedgerAccumulatorAndVerifier implements LedgerAccumulator, LedgerAccumulatorVerifier {

	private static final Logger log = LogManager.getLogger();

	private final Hasher hasher;

	@Inject
	public SimpleLedgerAccumulatorAndVerifier(Hasher hasher) {
		this.hasher = hasher;
	}

	@Override
	public AccumulatorState accumulate(AccumulatorState parent, HashCode hash) {
		byte[] concat = new byte[32 * 2];
		System.arraycopy(parent.getAccumulatorHash().asBytes(), 0, concat, 0, 32);
		System.arraycopy(hash.asBytes(), 0, concat, 32, 32);
		HashCode nextAccumulatorHash = hasher.hashBytes(concat);
		return new AccumulatorState(
			parent.getStateVersion() + 1,
			nextAccumulatorHash
		);
	}

	@Override
	public boolean verify(AccumulatorState start, ImmutableList<HashCode> hashes, AccumulatorState end) {
		AccumulatorState accumulatorState = start;
		for (HashCode hash : hashes) {
			accumulatorState = this.accumulate(accumulatorState, hash);
		}
		final var res = Objects.equals(accumulatorState, end);
		log.info("Verify initial state = {}, calculated={}, end ={}, equals?={}", start, accumulatorState, end, res);
		return res;
	}

	@Override
	public <T> Optional<ImmutableList<T>> verifyAndGetExtension(
		AccumulatorState current,
		ImmutableList<T> commands,
		Function<T, HashCode> hashCodeMapper,
		AccumulatorState tail
	) {
		log.info("verifyAndGetExtension");

		if (tail.getStateVersion() < current.getStateVersion()) {
			throw new IllegalArgumentException(String.format("Tail %s is has lower state version than current %s", tail, current));
		}

		final long firstVersion = tail.getStateVersion() - commands.size() + 1;
		if (current.getStateVersion() + 1 < firstVersion) {
			// Missing versions
			log.info("missing versions");
			return Optional.empty();
		}

		if (commands.isEmpty()) {
			log.info("empty commands  Objects.equals(current, tail)?= {}", Objects.equals(current, tail));

			return Objects.equals(current, tail) ? Optional.of(ImmutableList.of()) : Optional.empty();
		}

		final int startIndex = (int) (current.getStateVersion() + 1 - firstVersion);
		final ImmutableList<T> extension = commands.subList(startIndex, commands.size());
		final ImmutableList<HashCode> hashes = extension.stream().map(hashCodeMapper::apply).collect(ImmutableList.toImmutableList());
		if (!verify(current, hashes, tail)) {
			log.info("Does not extend");
			// Does not extend
			return Optional.empty();
		}

		return Optional.of(extension);
	}
}