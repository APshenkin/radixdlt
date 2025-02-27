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
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class UnstakePositionsDTO {
	private final UInt256 amount;
	private final String validator;
	private final int epochsUntil;
	private final AID withdrawTxID;

	private UnstakePositionsDTO(UInt256 amount, String validator, int epochsUntil, AID withdrawTxID) {
		this.amount = amount;
		this.validator = validator;
		this.epochsUntil = epochsUntil;
		this.withdrawTxID = withdrawTxID;
	}

	@JsonCreator
	public UnstakePositionsDTO create(
		@JsonProperty(value = "amount", required = true) UInt256 amount,
		@JsonProperty(value = "validator", required = true) String validator,
		@JsonProperty(value = "epochsUntil", required = true) int epochsUntil,
		@JsonProperty(value = "withdrawTxID", required = true) AID withdrawTxID
	) {
		requireNonNull(amount);
		requireNonNull(validator);
		requireNonNull(withdrawTxID);

		return new UnstakePositionsDTO(amount, validator, epochsUntil, withdrawTxID);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof UnstakePositionsDTO)) {
			return false;
		}

		var that = (UnstakePositionsDTO) o;
		return epochsUntil == that.epochsUntil
			&& amount.equals(that.amount)
			&& validator.equals(that.validator)
			&& withdrawTxID.equals(that.withdrawTxID);
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, validator, epochsUntil, withdrawTxID);
	}

	@Override
	public String toString() {
		return "UnstakePositionsDTO("
			+ "amount=" + amount +	", validator=" + validator
			+ ", epochsUntil=" + epochsUntil + ", withdrawTxID=" + withdrawTxID + ')';
	}

	public UInt256 getAmount() {
		return amount;
	}

	public String getValidator() {
		return validator;
	}

	public int getEpochsUntil() {
		return epochsUntil;
	}

	public AID getWithdrawTxID() {
		return withdrawTxID;
	}
}
