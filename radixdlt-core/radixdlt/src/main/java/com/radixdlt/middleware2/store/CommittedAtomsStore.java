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

package com.radixdlt.middleware2.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.store.EngineAtomIndices.IndexType;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;

import com.radixdlt.store.StoreIndex.LedgerIndexType;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Longs;
import com.radixdlt.store.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.Optional;

public final class CommittedAtomsStore implements EngineStore<CommittedAtom>, CommittedReader, RadixEngineAtomicCommitManager {
	private final Serialization serialization;
	private final AtomIndexer atomIndexer;
	private final LedgerEntryStore store;
	private final PersistentVertexStore persistentVertexStore;
	private final CommandToBinaryConverter commandToBinaryConverter;
	private final ClientAtomToBinaryConverter clientAtomToBinaryConverter;
	private final EventDispatcher<AtomCommittedToLedger> committedDispatcher;
	private final Hasher hasher;
	private Transaction transaction;

	public interface AtomIndexer {
		EngineAtomIndices getIndices(LedgerAtom atom);
	}

	public CommittedAtomsStore(
		LedgerEntryStore store,
		PersistentVertexStore persistentVertexStore,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		AtomIndexer atomIndexer,
		Serialization serialization,
		Hasher hasher,
		EventDispatcher<AtomCommittedToLedger> committedDispatcher
	) {
		this.store = Objects.requireNonNull(store);
		this.persistentVertexStore = Objects.requireNonNull(persistentVertexStore);
		this.commandToBinaryConverter = Objects.requireNonNull(commandToBinaryConverter);
		this.clientAtomToBinaryConverter = Objects.requireNonNull(clientAtomToBinaryConverter);
		this.atomIndexer = Objects.requireNonNull(atomIndexer);
		this.serialization = Objects.requireNonNull(serialization);
		this.hasher = hasher;
		this.committedDispatcher = Objects.requireNonNull(committedDispatcher);
	}

	private boolean particleExists(Particle particle, boolean isInput) {
		final byte[] indexableBytes = EngineAtomIndices.toByteArray(
		isInput ? EngineAtomIndices.IndexType.PARTICLE_DOWN : EngineAtomIndices.IndexType.PARTICLE_UP,
			Particle.euidOf(particle, hasher)
		);
		return store.contains(this.transaction, StoreIndex.LedgerIndexType.UNIQUE, new StoreIndex(indexableBytes), LedgerSearchMode.EXACT);
	}

	@Override
	public void startTransaction() {
		this.transaction = store.createTransaction();
	}

	@Override
	public void commitTransaction() {
		this.transaction.commit();
		this.transaction = null;
	}

	@Override
	public void abortTransaction() {
		this.transaction.abort();
		this.transaction = null;
	}

	@Override
	public void save(VerifiedVertexStoreState vertexStoreState) {
		persistentVertexStore.save(this.transaction, vertexStoreState);
	}

	// TODO: Save proof in a separate index
	@Override
	public void storeAtom(CommittedAtom committedAtom) {
		// TODO: Remove serialization/deserialization
		byte[] payload = clientAtomToBinaryConverter.toLedgerEntryContent(committedAtom.getClientAtom());
		Command command = new Command(payload);

		final VerifiedLedgerHeaderAndProof proof = committedAtom.getStateAndProof();
		StoredCommittedCommand storedCommittedCommand = new StoredCommittedCommand(command, proof);
		byte[] binaryAtom = commandToBinaryConverter.toLedgerEntryContent(storedCommittedCommand);
		LedgerEntry ledgerEntry = new LedgerEntry(
			binaryAtom,
			committedAtom.getStateVersion(),
			committedAtom.getStateAndProof().getStateVersion(),
			committedAtom.getAID()
		);
		EngineAtomIndices engineAtomIndices = atomIndexer.getIndices(committedAtom);

		// TODO: Replace Store + Commit with a single commit
		// TODO: How it's done depends on how mempool and prepare phases are implemented
		LedgerEntryStoreResult result = store.store(
			this.transaction,
			ledgerEntry,
			engineAtomIndices.getUniqueIndices(),
			engineAtomIndices.getDuplicateIndices()
		);
		if (!result.isSuccess()) {
			throw new IllegalStateException("Unable to store atom");
		}

		final ImmutableSet<EUID> indicies = engineAtomIndices.getDuplicateIndices().stream()
			.filter(e -> e.getPrefix() == EngineAtomIndices.IndexType.DESTINATION.getValue())
			.map(e -> EngineAtomIndices.toEUID(e.asKey()))
			.collect(ImmutableSet.toImmutableSet());

		// Don't send event on genesis
		// TODO: this is a bit hacky
		if (committedAtom.getStateVersion() > 0) {
			committedDispatcher.dispatch(AtomCommittedToLedger.create(committedAtom, indicies));
		}
	}

	public static final Logger log = LogManager.getLogger();

	@Override
	public <U extends Particle, V> V compute(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer,
		BiFunction<V, U, V> inputReducer
	) {
		log.info("committed atoms store compute");
		final String idForClass = serialization.getIdForClass(particleClass);
		final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
		final byte[] indexableBytes = EngineAtomIndices.toByteArray(IndexType.PARTICLE_CLASS, numericClassId);
		final StoreIndex storeIndex = new StoreIndex(EngineAtomIndices.IndexType.PARTICLE_CLASS.getValue(), indexableBytes);

		SearchCursor cursor = store.search(LedgerIndexType.DUPLICATE, storeIndex, LedgerSearchMode.EXACT);

		V v = initial;
		while (cursor != null) {
			AID aid = cursor.get();
			Optional<LedgerEntry> ledgerEntry = store.get(aid);
			log.info("commited store compute got atom from cursor");
			if (ledgerEntry.isPresent()) {
				LedgerEntry entry = ledgerEntry.get();
				StoredCommittedCommand committedCommand = commandToBinaryConverter.toCommand(entry.getContent());
				ClientAtom clientAtom = committedCommand.getCommand().map(clientAtomToBinaryConverter::toAtom);
				for (CMMicroInstruction cmMicroInstruction : clientAtom.getCMInstruction().getMicroInstructions()) {
					if (particleClass.isInstance(cmMicroInstruction.getParticle())
						&& cmMicroInstruction.isCheckSpin()) {
						if (cmMicroInstruction.getCheckSpin() == Spin.NEUTRAL) {
							v = outputReducer.apply(v, particleClass.cast(cmMicroInstruction.getParticle()));
						} else {
							v = inputReducer.apply(v, particleClass.cast(cmMicroInstruction.getParticle()));
						}
					}
				}
			}
			cursor = cursor.next();
		}
		log.info("committed atoms store compute DONE");
		return v;
	}

	public Optional<VerifiedLedgerHeaderAndProof> getLastVerifiedHeader() {
		log.info("get last verified header");
		return store.getLastCommitted()
			.flatMap(store::get)
			.map(e -> commandToBinaryConverter.toCommand(e.getContent()).getProof());
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getEpochVerifiedHeader(long epoch) {
		SearchCursor cursor = store.search(
			StoreIndex.LedgerIndexType.UNIQUE,
			new StoreIndex(IndexType.EPOCH_CHANGE.getValue(), Longs.toByteArray(epoch)),
			LedgerSearchMode.EXACT
		);
		if (cursor != null) {
			log.info("get epoch verified header");
			return store.get(cursor.get())
				.map(e -> commandToBinaryConverter.toCommand(e.getContent()).getProof());
		} else {
			return Optional.empty();
		}
	}

	public VerifiedCommandsAndProof getNextCommittedCommands(long stateVersion, int batchSize) throws NextCommittedLimitReachedException {
		ImmutableList<StoredCommittedCommand> storedCommittedCommands = store.getNextCommittedLedgerEntries(stateVersion, batchSize).stream()
			.map(e -> commandToBinaryConverter.toCommand(e.getContent()))
			.collect(ImmutableList.toImmutableList());

		if (storedCommittedCommands.isEmpty()) {
			return null;
		}

		// Limit the batch to within a single epoch
        // TODO: Cleanup and move logic into lower layer
		int epochChangeIndex = -1;
		for (int i = 0; i < storedCommittedCommands.size(); i++) {
			var cmd = storedCommittedCommands.get(i);
			var cmdVersion = stateVersion + i + 1;
			if (cmd.getProof().getRaw().isEndOfEpoch()
				&& cmd.getProof().getStateVersion() == cmdVersion) {
				epochChangeIndex = i;
				break;
			}
		}

		final int tailPosition = epochChangeIndex < 0 ? storedCommittedCommands.size() - 1 : epochChangeIndex;
		final var nextHeader = storedCommittedCommands.get(tailPosition).getProof();
		final var commands = storedCommittedCommands.stream()
			.limit(tailPosition + 1L)
			.map(StoredCommittedCommand::getCommand)
			.collect(ImmutableList.toImmutableList());
		return new VerifiedCommandsAndProof(commands, nextHeader);
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start, int batchSize)
		throws NextCommittedLimitReachedException {
		// TODO: verify start
		long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
		return this.getNextCommittedCommands(stateVersion, batchSize);
	}

	@Override
	public Spin getSpin(Particle particle) {
		if (particleExists(particle, true)) {
			return Spin.DOWN;
		} else if (particleExists(particle, false)) {
			return Spin.UP;
		}
		return Spin.NEUTRAL;
	}
}
