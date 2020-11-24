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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.FormedQC;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.consensus.liveness.ExponentialPacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.VoteSender;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncRequestProcessor;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.PendingViewTimeouts;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;

/**
 * Module responsible for running BFT validator logic
 */
public final class ConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;

	@Override
	public void configure() {
		bind(SafetyRules.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), VertexStoreEventSender.class);
	}

	@Provides
	private VertexStoreEventSender sender(Set<VertexStoreEventSender> senders) {
		return new VertexStoreEventSender() {
			@Override
			public void highQC(QuorumCertificate qc) {
				senders.forEach(s -> s.highQC(qc));
			}
		};
	}

	@Provides
	private BFTFactory bftFactory(
		Hasher hasher,
		HashVerifier verifier,
		EventDispatcher<FormedQC> formedQCEventDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier
	) {
		return (
			self,
			pacemaker,
			vertexStore,
			vertexStoreSync,
			proposerElection,
			validatorSet,
			counters,
			safetyRules
		) ->
			BFTBuilder.create()
				.self(self)
				.hasher(hasher)
				.verifier(verifier)
				.timeSupplier(timeSupplier)
				.voteSender(voteDispatcher)
				.counters(counters)
				.safetyRules(safetyRules)
				.pacemaker(pacemaker)
				.vertexStore(vertexStore)
				.formedQCEventDispatcher(formedQC -> {
					// FIXME: a hack for now until replacement of epochmanager factories
					vertexStoreSync.formedQCEventProcessor().process(formedQC);
					formedQCEventDispatcher.dispatch(formedQC);
				})
				.bftSyncer(vertexStoreSync)
				.proposerElection(proposerElection)
				.validatorSet(validatorSet)
				.build();
	}

	@ProvidesIntoSet
	public EventProcessor<BFTUpdate> bftUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return eventProcessor::processBFTUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTUpdate> bftSync(BFTSync bftSync) {
		return bftSync::processBFTUpdate;
	}

	@Provides
	@Singleton
	public BFTEventProcessor eventProcessor(
		@Self BFTNode self,
		BFTConfiguration config,
		BFTFactory bftFactory,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		BFTSync vertexStoreSync,
		ProposerElection proposerElection,
		SystemCounters counters,
		SafetyRules safetyRules
	) {
		return bftFactory.create(
			self,
			pacemaker,
			vertexStore,
			vertexStoreSync,
			proposerElection,
			config.getValidatorSet(),
			counters,
			safetyRules
		);
	}

	@Provides
	private ProposerElection proposerElection(BFTConfiguration configuration) {
		return new WeightedRotatingLeaders(
			configuration.getValidatorSet(),
			Comparator.comparing(v -> v.getNode().getKey().euid()),
			ROTATING_WEIGHTED_LEADERS_CACHE_SIZE
		);
	}

  	@Provides
	@Singleton
	private PacemakerTimeoutCalculator pacemakerTimeoutCalculator(
		@PacemakerTimeout long pacemakerTimeout,
		@PacemakerRate double pacemakerRate,
		@PacemakerMaxExponent int pacemakerMaxExponent
	) {
		return new ExponentialPacemakerTimeoutCalculator(pacemakerTimeout, pacemakerRate, pacemakerMaxExponent);
	}

	@Provides
	@Singleton
	private PacemakerState pacemakerState(EventDispatcher<ViewUpdate> viewUpdateSender) {
		return new PacemakerState(viewUpdateSender);
	}

	@Provides
	@Singleton
	private Pacemaker pacemaker(
		@Self BFTNode self,
		SafetyRules safetyRules,
		SystemCounters counters,
		BFTConfiguration configuration,
		VertexStore vertexStore,
		VoteSender voteSender,
		EventDispatcher<View> timeoutDispatcher,
		PacemakerState pacemakerState,
		PacemakerTimeoutSender timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		NextCommandGenerator nextCommandGenerator,
		ProposalBroadcaster proposalBroadcaster,
		Hasher hasher,
		ProposerElection proposerElection
	) {
		PendingViewTimeouts pendingViewTimeouts = new PendingViewTimeouts();
		BFTValidatorSet validatorSet = configuration.getValidatorSet();
		return new Pacemaker(
			self,
			counters,
			pendingViewTimeouts,
			validatorSet,
			vertexStore,
			safetyRules,
			voteSender,
			timeoutDispatcher,
			pacemakerState,
			timeoutSender,
			timeoutCalculator,
			nextCommandGenerator,
			proposalBroadcaster,
			proposerElection,
			hasher
		);
	}

	@Provides
	private BFTSyncRequestProcessor bftSyncRequestProcessor(
		VertexStore vertexStore,
		SyncVerticesResponseSender responseSender
	) {
		return new VertexStoreBFTSyncRequestProcessor(vertexStore, responseSender);
	}

	@Provides
	@Singleton
	private BFTSync bftSync(
		VertexStore vertexStore,
		PacemakerState pacemakerState,
		SyncVerticesRequestSender requestSender,
		EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
		ScheduledEventDispatcher<LocalGetVerticesRequest> timeoutDispatcher,
		@LastProof VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof,
		SystemCounters counters,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis
	) {
		return new BFTSync(
			vertexStore,
			pacemakerState,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			(node, request)  -> {
				counters.increment(CounterType.BFT_SYNC_REQUESTS_SENT);
				requestSender.sendGetVerticesRequest(node, request);
			},
			syncLedgerRequestSender,
			timeoutDispatcher,
			verifiedLedgerHeaderAndProof,
			random,
			bftSyncPatienceMillis
		);
	}

	@Provides
	@Singleton
	private VertexStore vertexStore(
		VertexStoreEventSender vertexStoreEventSender,
		EventDispatcher<BFTUpdate> updateSender,
		EventDispatcher<BFTCommittedUpdate> committedSender,
		BFTConfiguration bftConfiguration,
		SystemCounters counters,
		Ledger ledger
	) {
		return VertexStore.create(
			bftConfiguration.getGenesisVertex(),
			bftConfiguration.getGenesisQC(),
			ledger,
			updateSender,
			committedSender,
			vertexStoreEventSender,
			counters
		);
	}
}
