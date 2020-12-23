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

package com.radixdlt.integration.distributed.simulation.tests.consensus_ledger_epochs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.SimulationTest.TestResults;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Test;

public class StaticValidatorsTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.numNodes(4, 2)
		.checkConsensusSafety("safety")
		.checkConsensusLiveness("liveness", 1000, TimeUnit.MILLISECONDS)
		.checkConsensusNoTimeouts("noTimeouts")
		.checkConsensusAllProposalsHaveDirectParents("directParents")
		.checkLedgerInOrder("ledgerInOrder")
		.checkLedgerProcessesConsensusCommitted("consensusToLedger");

	@Test
	public void given_correct_bft_with_changing_epochs_every_view__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.pacemakerTimeout(1000)
			.ledgerAndEpochs(View.of(1), e -> IntStream.range(0, 4))
			.checkEpochsHighViewCorrect("epochHighView", View.of(1))
			.build();

		TestResults results = bftTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}

	@Test
	public void given_correct_bft_with_changing_epochs_per_100_views__then_should_fail_incorrect_epoch_invariant() {
		SimulationTest bftTest = bftTestBuilder
			.ledgerAndEpochs(View.of(100), e -> IntStream.range(0, 4))
			.checkEpochsHighViewCorrect("epochHighView", View.of(99))
			.addTimestampChecker("timestamps")
			.build();

		TestResults results = bftTest.run();
		assertThat(results.getCheckResults()).hasEntrySatisfying("epochHighView", error -> assertThat(error).isPresent());
	}

	@Test
	public void given_correct_bft_with_changing_epochs_per_100_views__then_should_pass_bft_and_epoch_invariants() {
		SimulationTest bftTest = bftTestBuilder
			.ledgerAndEpochs(View.of(100), e -> IntStream.range(0, 4))
			.checkEpochsHighViewCorrect("epochHighView", View.of(100))
			.addTimestampChecker("timestamps")
			.build();

		TestResults results = bftTest.run();
		assertThat(results.getCheckResults()).allSatisfy((name, err) -> assertThat(err).isEmpty());
	}
}
