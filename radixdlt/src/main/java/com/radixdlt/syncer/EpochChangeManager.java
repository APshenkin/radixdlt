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

package com.radixdlt.syncer;

import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.syncer.SyncExecutor.CommittedSender;
import com.radixdlt.syncer.SyncExecutor.StateComputerExecutedCommand;

public final class EpochChangeManager implements CommittedSender {
	private final EpochChangeSender epochChangeSender;

	public EpochChangeManager(EpochChangeSender epochChangeSender) {
		this.epochChangeSender = epochChangeSender;
	}

	@Override
	public void sendCommitted(StateComputerExecutedCommand stateComputerExecutedCommand) {
		CommittedAtom atom = stateComputerExecutedCommand.getCommand();
		atom.getVertexMetadata().getValidatorSet().ifPresent(validatorSet -> {
			EpochChange epochChange = new EpochChange(atom.getVertexMetadata(), validatorSet);
			this.epochChangeSender.epochChange(epochChange);
		});
	}
}
