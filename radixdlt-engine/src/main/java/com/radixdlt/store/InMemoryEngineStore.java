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

package com.radixdlt.store;

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.constraintmachine.REParsedInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.REAddr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<M> implements EngineStore<M>, SubstateStore {
	private final Object lock = new Object();
	private final Map<SubstateId, REParsedInstruction> storedParticles = new HashMap<>();
	private final Map<REAddr, Particle> addrParticles = new HashMap<>();

	@Override
	public void storeTxn(Transaction dbTxn, Txn txn, List<REParsedInstruction> stateUpdates) {
		synchronized (lock) {
			stateUpdates.stream()
				.filter(REParsedInstruction::isStateUpdate)
				.forEach(i -> storedParticles.put(i.getSubstate().getId(), i));
			stateUpdates.stream()
				.filter(REParsedInstruction::isBootUp)
				.map(REParsedInstruction::getParticle)
				.forEach(p -> {
					if (p instanceof TokenDefinitionParticle) {
						var tokenDef = (TokenDefinitionParticle) p;
						addrParticles.put(tokenDef.getAddr(), p);
					} else if (p instanceof SystemParticle) {
						addrParticles.put(REAddr.ofSystem(), p);
					}
				});
		}
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		 // No-op
	}

	@Override
	public <U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	) {
		V v = initial;
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (!i.isBootUp() || !particleClass.isInstance(i.getParticle())) {
					continue;
				}
				v = outputReducer.apply(v, particleClass.cast(i.getParticle()));
			}
		}
		return v;
	}

	@Override
	public SubstateCursor openIndexedCursor(Class<? extends Particle> substateClass) {
		final List<Substate> substates = new ArrayList<>();
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (!i.isBootUp() || !substateClass.isInstance(i.getParticle())) {
					continue;
				}
				substates.add(i.getSubstate());
			}
		}

		return SubstateCursor.wrapIterator(substates.iterator());
	}

	@Override
	public Transaction createTransaction() {
		return new Transaction() { };
	}

	@Override
	public boolean isVirtualDown(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst != null && inst.isShutDown();
		}
	}

	public Spin getSpin(SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst == null ? Spin.NEUTRAL : inst.getNextSpin();
		}
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			if (inst == null || inst.getNextSpin() != Spin.UP) {
				return Optional.empty();
			}

			var particle = inst.getParticle();
			return Optional.of(particle);
		}
	}

	@Override
	public Optional<Particle> loadAddr(Transaction dbTxn, REAddr rri) {
		synchronized (lock) {
			return Optional.ofNullable(addrParticles.get(rri));
		}
	}
}
