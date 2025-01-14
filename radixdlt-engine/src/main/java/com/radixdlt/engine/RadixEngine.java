/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.engine;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REParsedInstruction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;

import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<M> {
	private static final Logger logger = LogManager.getLogger();

	private static class ApplicationStateComputer<U, V extends Particle, M> {
		private final Class<V> particleClass;
		private final BiFunction<U, V, U> outputReducer;
		private final BiFunction<U, V, U> inputReducer;
		private final boolean includeInBranches;
		private U curValue;

		ApplicationStateComputer(
			Class<V> particleClass,
			U initialValue,
			BiFunction<U, V, U> outputReducer,
			BiFunction<U, V, U> inputReducer,
			boolean includeInBranches
		) {
			this.particleClass = particleClass;
			this.curValue = initialValue;
			this.outputReducer = outputReducer;
			this.inputReducer = inputReducer;
			this.includeInBranches = includeInBranches;
		}

		ApplicationStateComputer<U, V, M> copy() {
			return new ApplicationStateComputer<>(
				particleClass,
				curValue,
				outputReducer,
				inputReducer,
				includeInBranches
			);
		}

		void initialize(EngineStore<M> engineStore) {
			curValue = engineStore.reduceUpParticles(particleClass, curValue, outputReducer);
		}

		void processCheckSpin(Particle p, Spin checkSpin) {
			if (particleClass.isInstance(p)) {
				V particle = particleClass.cast(p);
				if (checkSpin == Spin.NEUTRAL) {
					curValue = outputReducer.apply(curValue, particle);
				} else {
					curValue = inputReducer.apply(curValue, particle);
				}
			}
		}
	}

	private static final class SubstateCache<T extends Particle> {
		private final Predicate<T> particleCheck;
		private final Cache<SubstateId, Substate> cache = CacheBuilder.newBuilder()
			.maximumSize(1000)
			.build();

		private final boolean includeInBranches;

		SubstateCache(Predicate<T> particleCheck, boolean includeInBranches) {
			this.particleCheck = particleCheck;
			this.includeInBranches = includeInBranches;
		}

		public SubstateCache<T> copy() {
			var copy = new SubstateCache<>(particleCheck, includeInBranches);
			copy.cache.putAll(cache.asMap());
			return copy;
		}

		public boolean test(Particle particle) {
			return particleCheck.test((T) particle);
		}

		public SubstateCache<T> bringUp(Substate upSubstate) {
			if (particleCheck.test((T) upSubstate.getParticle())) {
				this.cache.put(upSubstate.getId(), upSubstate);
			}
			return this;
		}

		public SubstateCache<T> shutDown(SubstateId substateId) {
			this.cache.invalidate(substateId);
			return this;
		}
	}

	private final EngineStore<M> engineStore;
	private final PostParsedChecker checker;
	private final Object stateUpdateEngineLock = new Object();
	private final Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> stateComputers = new HashMap<>();
	private final Map<Class<?>, SubstateCache<?>> substateCache = new HashMap<>();
	private final List<RadixEngineBranch<M>> branches = new ArrayList<>();
	private final BatchVerifier<M> batchVerifier;

	private ConstraintMachine constraintMachine;

	public RadixEngine(
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore
	) {
		this(constraintMachine, engineStore, null, BatchVerifier.empty());
	}

	public RadixEngine(
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore,
		PostParsedChecker checker,
		BatchVerifier<M> batchVerifier
	) {
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.engineStore = Objects.requireNonNull(engineStore);
		this.checker = checker;
		this.batchVerifier = batchVerifier;
	}

	public <T extends Particle> void addSubstateCache(SubstateCacheRegister<T> substateCacheRegister, boolean includeInBranches) {
		synchronized (stateUpdateEngineLock) {
			if (substateCache.containsKey(substateCacheRegister.getParticleClass())) {
				throw new IllegalStateException("Already added " + substateCacheRegister.getParticleClass());
			}

			var cache = new SubstateCache<>(substateCacheRegister.getParticlePredicate(), includeInBranches);
			try (var cursor = engineStore.openIndexedCursor(substateCacheRegister.getParticleClass())) {
				cursor.forEachRemaining(substate -> {
					var p = substateCacheRegister.getParticleClass().cast(substate.getParticle());
					if (substateCacheRegister.getParticlePredicate().test(p)) {
						cache.bringUp(substate);
					}
				});
			}
			substateCache.put(substateCacheRegister.getParticleClass(), cache);
		}
	}


	/**
	 * Add a deterministic computation engine which maps an ordered list of
	 * particles which have been created and destroyed to a state.
	 * Initially runs the computation with all the atoms currently in the store
	 * and then updates the state value as atoms get stored.
	 *
	 * @param stateReducer the reducer
	 * @param <U> the class of the state
	 * @param <V> the class of the particles to map
	 */
	public <U, V extends Particle> void addStateReducer(StateReducer<U, V> stateReducer, String name, boolean includeInBranches) {
		ApplicationStateComputer<U, V, M> applicationStateComputer = new ApplicationStateComputer<>(
			stateReducer.particleClass(),
			stateReducer.initial().get(),
			stateReducer.outputReducer(),
			stateReducer.inputReducer(),
			includeInBranches
		);

		synchronized (stateUpdateEngineLock) {
			applicationStateComputer.initialize(this.engineStore);
			stateComputers.put(Pair.of(stateReducer.stateClass(), name), applicationStateComputer);
		}
	}

	public <U, V extends Particle> void addStateReducer(StateReducer<U, V> stateReducer, boolean includeInBranches) {
		addStateReducer(stateReducer, null, includeInBranches);
	}

	/**
	 * Retrieves the latest state
	 * @param applicationStateClass the class of the state to retrieve
	 * @param <U> the class of the state to retrieve
	 * @return the current state
	 */
	public <U> U getComputedState(Class<U> applicationStateClass) {
		return getComputedState(applicationStateClass, null);
	}

	/**
	 * Retrieves the latest state
	 * @param applicationStateClass the class of the state to retrieve
	 * @param <U> the class of the state to retrieve
	 * @return the current state
	 */
	public <U> U getComputedState(Class<U> applicationStateClass, String name) {
		synchronized (stateUpdateEngineLock) {
			return applicationStateClass.cast(stateComputers.get(Pair.of(applicationStateClass, name)).curValue);
		}
	}

	public void replaceConstraintMachine(ConstraintMachine constraintMachine) {
		synchronized (stateUpdateEngineLock) {
			this.constraintMachine = constraintMachine;
		}
	}


	/**
	 * A cheap radix engine branch which is purely transient
	 */
	public static class RadixEngineBranch<M> {
		private final RadixEngine<M> engine;

		private RadixEngineBranch(
			ConstraintMachine constraintMachine,
			EngineStore<M> parentStore,
			PostParsedChecker checker,
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> stateComputers,
			Map<Class<?>, SubstateCache<?>> substateCache
		) {
			var transientEngineStore = new TransientEngineStore<>(parentStore);

			this.engine = new RadixEngine<>(
				constraintMachine,
				transientEngineStore,
				checker,
				BatchVerifier.empty()
			);

			engine.substateCache.putAll(substateCache);
			engine.stateComputers.putAll(stateComputers);
		}

		public List<REParsedTxn> execute(List<Txn> txns) throws RadixEngineException {
			return engine.execute(txns);
		}

		public List<REParsedTxn> execute(List<Txn> txns, PermissionLevel permissionLevel) throws RadixEngineException {
			return engine.execute(txns, null, permissionLevel);
		}

		public TxBuilder construct(TxAction action) throws TxBuilderException {
			return engine.construct(action);
		}

		public TxBuilder construct(List<TxAction> actions) throws TxBuilderException {
			return engine.construct(actions);
		}

		public <U> U getComputedState(Class<U> applicationStateClass) {
			return engine.getComputedState(applicationStateClass);
		}
	}

	public void deleteBranches() {
		synchronized (stateUpdateEngineLock) {
			branches.clear();
		}
	}

	public RadixEngineBranch<M> transientBranch() {
		synchronized (stateUpdateEngineLock) {
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> branchedStateComputers = new HashMap<>();
			this.stateComputers.forEach((c, computer) -> {
				if (computer.includeInBranches) {
					branchedStateComputers.put(c, computer.copy());
				}
			});
			var branchedCache = new HashMap<Class<?>, SubstateCache<?>>();
			this.substateCache.forEach((c, cache) -> {
				if (cache.includeInBranches) {
					branchedCache.put(c, cache.copy());
				}
			});
			RadixEngineBranch<M> branch = new RadixEngineBranch<>(
				this.constraintMachine,
				this.engineStore,
				this.checker,
				branchedStateComputers,
				branchedCache
			);

			branches.add(branch);

			return branch;
		}
	}

	private REParsedTxn verify(CMStore.Transaction dbTransaction, Txn txn, PermissionLevel permissionLevel)
		throws RadixEngineException {

		var parsedTxn = constraintMachine.verify(
			dbTransaction,
			engineStore,
			txn,
			permissionLevel
		);

		if (checker != null) {
			var hookResult = checker.check(permissionLevel, parsedTxn);
			if (hookResult.isError()) {
				throw new RadixEngineException(
					txn,
					RadixEngineErrorCode.HOOK_ERROR,
					"Checker failed: " + hookResult.getErrorMessage(),
					parsedTxn.getStatelessResult()
				);
			}
		}

		return parsedTxn;
	}

	/**
	 * Atomically stores the given atom into the store with default permission level USER.
	 * If the atom has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @throws RadixEngineException on state conflict, dependency issues or bad atom
	 */
	public List<REParsedTxn> execute(List<Txn> txns) throws RadixEngineException {
		return execute(txns, null, PermissionLevel.USER);
	}

	/**
	 * Atomically stores the given atom into the store. If the atom
	 * has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param txns transactions to execute
	 * @param permissionLevel permission level to execute on
	 * @throws RadixEngineException on state conflict or dependency issues
	 */
	public List<REParsedTxn> execute(List<Txn> txns, M meta, PermissionLevel permissionLevel) throws RadixEngineException {
		synchronized (stateUpdateEngineLock) {
			if (!branches.isEmpty()) {
				throw new IllegalStateException(
					String.format(
						"%s transient branches still exist. Must delete branches before storing additional atoms.",
						branches.size()
					)
				);
			}
			var dbTransaction = engineStore.createTransaction();
			try {
				var parsedTransactions = executeInternal(dbTransaction, txns, meta, permissionLevel);
				dbTransaction.commit();
				return parsedTransactions;
			} catch (Exception e) {
				dbTransaction.abort();
				throw e;
			}
		}
	}

	private List<REParsedTxn> executeInternal(
		CMStore.Transaction dbTransaction,
		List<Txn> txns,
		M meta,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var checker = batchVerifier.newVerifier(this::getComputedState);
		var parsedTransactions = new ArrayList<REParsedTxn>();
		for (var txn : txns) {
			// TODO: combine verification and storage
			var parsedTxn = this.verify(dbTransaction, txn, permissionLevel);
			try {
				this.engineStore.storeTxn(dbTransaction, txn, parsedTxn.stateUpdates());
			} catch (Exception e) {
				logger.error("Store of atom failed: " + parsedTxn, e);
				throw e;
			}

			// TODO Feature: Return updated state for some given query (e.g. for current validator set)
			// Non-persisted computed state
			parsedTxn.instructions().filter(REParsedInstruction::isStateUpdate).forEach(parsedInstruction -> {
				final var particle = parsedInstruction.getSubstate().getParticle();
				final var checkSpin = parsedInstruction.getCheckSpin();
				stateComputers.forEach((a, computer) -> computer.processCheckSpin(particle, checkSpin));

				var cache = substateCache.get(particle.getClass());
				if (cache != null && cache.test(particle)) {
					if (parsedInstruction.isBootUp()) {
						cache.bringUp(parsedInstruction.getSubstate());
					} else {
						cache.shutDown(parsedInstruction.getSubstate().getId());
					}
				}

				if (parsedInstruction.isBootUp()) {
					checker.test(this::getComputedState);
				}
			});
			parsedTransactions.add(parsedTxn);
		}

		checker.testMetadata(meta, this::getComputedState);

		if (meta != null) {
			this.engineStore.storeMetadata(dbTransaction, meta);
		}

		return parsedTransactions;
	}

	public TxBuilder construct(TxAction action) throws TxBuilderException {
		return construct(null, List.of(action));
	}

	public TxBuilder construct(List<TxAction> actions) throws TxBuilderException {
		return construct(null, actions);
	}

	public TxBuilder construct(ECPublicKey user, TxAction action) throws TxBuilderException {
		return construct(user, List.of(action));
	}

	public TxBuilder construct(ECPublicKey user, List<TxAction> actions) throws TxBuilderException {
		return construct(user, actions, Set.of());
	}

	public TxBuilder construct(ECPublicKey user, List<TxAction> actions, Set<SubstateId> avoid) throws TxBuilderException {
		synchronized (stateUpdateEngineLock) {
			SubstateStore substateStore = c -> {
				var cache = substateCache.get(c);
				if (cache == null) {
					return engineStore.openIndexedCursor(c);
				}

				var cacheIterator = cache.cache.asMap().values().iterator();

				return SubstateCursor.concat(
					SubstateCursor.wrapIterator(cacheIterator),
					() -> SubstateCursor.filter(
						engineStore.openIndexedCursor(c),
						next -> !cache.cache.asMap().containsKey(next.getId())
					)
				);
			};

			SubstateStore filteredStore = c -> SubstateCursor.filter(
				substateStore.openIndexedCursor(c),
				i -> !avoid.contains(i.getId())
			);

			var txBuilder = user != null
				? TxBuilder.newBuilder(user, filteredStore)
				: TxBuilder.newBuilder(filteredStore);
			for (var action : actions) {
				action.execute(txBuilder);
				txBuilder.particleGroup();
			}

			return txBuilder;
		}
	}
}
