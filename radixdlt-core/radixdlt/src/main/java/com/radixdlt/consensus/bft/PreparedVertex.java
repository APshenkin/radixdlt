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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.utils.Pair;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Vertex which has been executed in the prepare phase
 */
public final class PreparedVertex {
	private final long timeOfExecution;
	private final VerifiedVertex vertex;

	private final LedgerHeader ledgerHeader;

	private final ImmutableList<PreparedCommand> preparedCommands;
	private final ImmutableMap<Command, Exception> commandExceptions;

	PreparedVertex(
		VerifiedVertex vertex,
		LedgerHeader ledgerHeader,
		ImmutableList<PreparedCommand> preparedCommands,
		ImmutableMap<Command, Exception> commandExceptions,
		long timeOfExecution
	) {
		this.vertex = Objects.requireNonNull(vertex);
		this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
		this.preparedCommands = Objects.requireNonNull(preparedCommands);
		this.commandExceptions = Objects.requireNonNull(commandExceptions);
		this.timeOfExecution = timeOfExecution;
	}

	public long getTimeOfExecution() {
		return timeOfExecution;
	}

	public HashCode getId() {
		return vertex.getId();
	}

	public HashCode getParentId() {
		return vertex.getParentId();
	}

	public View getView() {
		return vertex.getView();
	}

	public Stream<PreparedCommand> successfulCommands() {
		return preparedCommands.stream();
	}

	public Stream<Pair<Command, Exception>> errorCommands() {
		return commandExceptions.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
	}

	public Stream<Command> getCommands() {
		return Stream.concat(successfulCommands().map(PreparedCommand::command), errorCommands().map(Pair::getFirst));
	}

	/**
	 * Retrieve the resulting header which is to be persisted on ledger
	 * @return the header
	 */
	public LedgerHeader getLedgerHeader() {
		return ledgerHeader;
	}

	/**
	 * Retrieve the vertex which was executed
	 * @return the executed vertex
	 */
	public VerifiedVertex getVertex() {
		return vertex;
	}

	@Override
	public String toString() {
		return String.format("%s{vertex=%s ledgerHeader=%s}", this.getClass().getSimpleName(), this.vertex, this.ledgerHeader);
	}
}
