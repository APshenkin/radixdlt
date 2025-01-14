/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application;

import com.google.inject.Inject;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces radix engine state to validator info
 */
public final class ValidatorInfoReducer implements StateReducer<ValidatorInfo, ValidatorParticle> {
	private final ECPublicKey self;

	@Inject
	public ValidatorInfoReducer(@Self ECPublicKey self) {
		this.self = Objects.requireNonNull(self);
	}

	@Override
	public Class<ValidatorInfo> stateClass() {
		return ValidatorInfo.class;
	}

	@Override
	public Class<ValidatorParticle> particleClass() {
		return ValidatorParticle.class;
	}

	@Override
	public Supplier<ValidatorInfo> initial() {
		return () -> new ValidatorInfo("", "", false);
	}

	@Override
	public BiFunction<ValidatorInfo, ValidatorParticle, ValidatorInfo> outputReducer() {
		return (i, r) -> {
			if (r.getKey().equals(self)) {
				return new ValidatorInfo(
					r.getName(),
					r.getUrl(),
					r.isRegisteredForNextEpoch()
				);
			}
			return i;
		};
	}

	@Override
	public BiFunction<ValidatorInfo, ValidatorParticle, ValidatorInfo> inputReducer() {
		return (i, r) -> i;
	}
}
