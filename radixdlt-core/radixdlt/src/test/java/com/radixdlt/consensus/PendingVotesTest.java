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

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewVotingResult;
import com.radixdlt.consensus.bft.VoteProcessingResult;
import com.radixdlt.consensus.bft.VoteProcessingResult.VoteRejected.VoteRejectedReason;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.utils.RandomHasher;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PendingVotesTest {
	private PendingVotes pendingVotes;
	private Hasher hasher;

	@Before
	public void setup() {
		this.hasher = new RandomHasher();
		this.pendingVotes = new PendingVotes(hasher);
	}

	@Test
	public void equalsContractForPreviousVote() {
		EqualsVerifier.forClass(PendingVotes.PreviousVote.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void when_inserting_valid_but_unaccepted_votes__then_no_qc_is_returned() {
		HashCode vertexId = HashUtils.random256();
		Vote vote1 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId);
		Vote vote2 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId);

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(vote1.getAuthor(), UInt256.ONE))
		);
		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote1.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		assertEquals(
			VoteProcessingResult.rejected(VoteRejectedReason.INVALID_AUTHOR),
			this.pendingVotes.insertVote(vote2, validatorSet));
	}

	@Test
	public void when_inserting_valid_and_accepted_votes__then_qc_is_formed() {
		BFTNode author = mock(BFTNode.class);
		Vote vote = makeSignedVoteFor(author, View.genesis(), HashUtils.random256());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		assertTrue(
			this.pendingVotes.insertVote(vote, validatorSet) instanceof VoteProcessingResult.QuorumReached);
	}

	@Test
	public void when_inserting_valid_timeout_votes__then_tc_is_formed() {
		HashCode vertexId1 = HashUtils.random256();
		HashCode vertexId2 = HashUtils.random256();
		Vote vote1 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId1);
		when(vote1.getTimeoutSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(vote1.isTimeout()).thenReturn(true);
		Vote vote2 = makeSignedVoteFor(mock(BFTNode.class), View.genesis(), vertexId2);
		when(vote2.getTimeoutSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(vote2.isTimeout()).thenReturn(true);

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Arrays.asList(
				BFTValidator.from(vote1.getAuthor(), UInt256.ONE),
				BFTValidator.from(vote2.getAuthor(), UInt256.ONE))
		);

		assertTrue(
			this.pendingVotes.insertVote(vote1, validatorSet) instanceof VoteProcessingResult.VoteAccepted);

		VoteProcessingResult result2 = this.pendingVotes.insertVote(vote2, validatorSet);

		assertTrue(result2 instanceof VoteProcessingResult.QuorumReached);

		assertTrue(((VoteProcessingResult.QuorumReached) result2).getViewVotingResult()
				instanceof ViewVotingResult.FormedTC);
	}

	@Test
	public void when_voting_again__previous_vote_is_removed() {
		BFTNode author = mock(BFTNode.class);
		Vote vote = makeSignedVoteFor(author, View.genesis(), HashUtils.random256());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.signatures()).thenReturn(signatures);
		when(validationState.isEmpty()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = vote.getVoteData().getProposed();
		when(voteData.getProposed()).thenReturn(proposed);

		// Preconditions
		assertEquals(
			VoteProcessingResult.accepted(),
			this.pendingVotes.insertVote(vote, validatorSet));
		assertEquals(1, this.pendingVotes.voteStateSize());
		assertEquals(1, this.pendingVotes.previousVotesSize());

		Vote vote2 = makeSignedVoteFor(author, View.of(1), HashUtils.random256());
		// Need a different hash for this (different) vote
		assertEquals(
			VoteProcessingResult.accepted(),
			this.pendingVotes.insertVote(vote2, validatorSet));
		assertEquals(1, this.pendingVotes.voteStateSize());
		assertEquals(1, this.pendingVotes.previousVotesSize());
	}

	private Vote makeSignedVoteFor(BFTNode author, View parentView, HashCode vertexId) {
		Vote vote = makeVoteWithoutSignatureFor(author, parentView, vertexId);
		when(vote.getSignature()).thenReturn(new ECDSASignature());
		return vote;
	}

	private Vote makeVoteWithoutSignatureFor(BFTNode author, View parentView, HashCode vertexId) {
		Vote vote = mock(Vote.class);
		BFTHeader proposed = new BFTHeader(parentView.next(), vertexId, mock(LedgerHeader.class));
		BFTHeader parent = new BFTHeader(parentView, HashUtils.random256(), mock(LedgerHeader.class));
		VoteData voteData = new VoteData(proposed, parent, null);
		TimestampedVoteData timestampedVoteData = new TimestampedVoteData(voteData, 123456L);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getTimestampedVoteData()).thenReturn(timestampedVoteData);
		when(vote.getAuthor()).thenReturn(author);
		when(vote.getView()).thenReturn(parentView);
		when(vote.getEpoch()).thenReturn(0L);
		return vote;
	}
}
