/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.api.ActionType;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.Optional;

public class ActionDTO {
	@JsonProperty("type")
	private final ActionType type;

	@JsonProperty("from")
	private final REAddr from;

	@JsonProperty("to")
	private final REAddr to;

	@JsonProperty("validator")
	private final REAddr validator;

	@JsonProperty("amount")
	private final UInt256 amount;

	@JsonProperty("rri")
	private final String rri;

	private ActionDTO(ActionType type, REAddr from, REAddr to, REAddr validator, UInt256 amount, String rri) {
		this.type = type;
		this.from = from;
		this.to = to;
		this.validator = validator;
		this.amount = amount;
		this.rri = rri;
	}

	@JsonCreator
	public static ActionDTO create(
		@JsonProperty("type") ActionType type,
		@JsonProperty("from") REAddr from,
		@JsonProperty("to") REAddr to,
		@JsonProperty("validator") REAddr validator,
		@JsonProperty("amount") UInt256 amount,
		@JsonProperty("rri") String rri
	) {
		return new ActionDTO(type, from, to, validator, amount, rri);
	}

	//TODO: add remaining actions
	public static ActionDTO transfer(REAddr from, REAddr to, UInt256 amount, String rri) {
		return create(ActionType.TRANSFER, from, to, null, amount, rri);
	}

	public static ActionDTO stake(REAddr from, REAddr validator, UInt256 amount) {
		return create(ActionType.STAKE, from, null, validator, amount, null);
	}

	public static ActionDTO unstake(REAddr from, REAddr validator, UInt256 amount) {
		return create(ActionType.UNSTAKE, from, null, validator, amount, null);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ActionDTO)) {
			return false;
		}

		var actionDTO = (ActionDTO) o;
		return type == actionDTO.type
			&& Objects.equals(from, actionDTO.from)
			&& Objects.equals(to, actionDTO.to)
			&& Objects.equals(validator, actionDTO.validator)
			&& Objects.equals(amount, actionDTO.amount)
			&& Objects.equals(rri, actionDTO.rri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, from, to, validator, amount, rri);
	}

	@Override
	public String toString() {
		return "Action(" +
			"type=" + type +
			", from=" + from +
			", to=" + to +
			", validator=" + validator +
			", amount=" + amount +
			", rri=" + rri +
			')';
	}

	public ActionType getType() {
		return type;
	}

	public Optional<REAddr> getFrom() {
		return Optional.ofNullable(from);
	}

	public Optional<REAddr> getTo() {
		return Optional.ofNullable(to);
	}

	public Optional<REAddr> getValidator() {
		return Optional.ofNullable(validator);
	}

	public Optional<UInt256> getAmount() {
		return Optional.ofNullable(amount);
	}

	public Optional<String> getRri() {
		return Optional.ofNullable(rri);
	}
}
