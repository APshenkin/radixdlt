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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Single;
import java.util.Random;

/**
 * Selects nodes from an initial bft configuration (no epochs)
 */
public class BFTValidatorSetNodeSelector implements NodeSelector {
	private final Random random = new Random();

	@Override
	public Single<BFTNode> nextNode(RunningNetwork network) {
		BFTConfiguration config = network.bftConfiguration();
		ImmutableList<BFTNode> validators = config.getValidatorSet().nodes().asList();
		int validatorSetSize = validators.size();
		BFTNode node = validators.get(random.nextInt(validatorSetSize));
		return Single.just(node);
	}
}
