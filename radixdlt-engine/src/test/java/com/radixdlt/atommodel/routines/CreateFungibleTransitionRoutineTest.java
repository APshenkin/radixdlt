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

package com.radixdlt.atommodel.routines;

import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.tokens.Fungible;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.store.ReadableAddrs;
import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.Objects;

import org.junit.Test;

import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine.UsedAmount;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.utils.UInt256;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class CreateFungibleTransitionRoutineTest {

	private static final class FungibleSubstate implements Fungible {
		private final UInt256 amount;
		FungibleSubstate(UInt256 amount) {
			this.amount = amount;
		}

		@Override
		public UInt256 getAmount() {
			return amount;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.amount);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof FungibleSubstate)) {
				return false;
			}
			final var that = (FungibleSubstate) obj;
			return Objects.equals(this.amount, that.amount);
		}

		@Override
		public String toString() {
			return "Fungible " + amount;
		}
	}

	interface SignatureValidatorFungible extends SignatureValidator<FungibleSubstate, FungibleSubstate> {
		// Empty
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CreateFungibleTransitionRoutine.UsedAmount.class)
				.verify();
	}

	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		TransitionProcedure<FungibleSubstate, FungibleSubstate, VoidReducerState> procedure = new CreateFungibleTransitionRoutine<>(
			FungibleSubstate.class, FungibleSubstate.class,
			(a, b, r) -> Result.success(),
			mock(SignatureValidatorFungible.class),
			(i, o, index) -> Unknown.create()
		).getProcedure0();

		assertThat(procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.ONE)),
			new FungibleSubstate(UInt256.ONE),
			mock(ReadableAddrs.class),
			null
		).isComplete()).isTrue();


		assertThat(procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.ONE)),
			new FungibleSubstate(UInt256.ONE),
			mock(ReadableAddrs.class),
			null
		).isComplete()).isTrue();
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_execution_should_pop_output_and_one_left_on_input() {
		TransitionProcedure<FungibleSubstate, FungibleSubstate, VoidReducerState> procedure = new CreateFungibleTransitionRoutine<>(
			FungibleSubstate.class, FungibleSubstate.class,
			(a, b, r) -> Result.success(),
			mock(SignatureValidatorFungible.class),
			(i, o, index) -> Unknown.create()
		).getProcedure0();

		var state = procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.TWO)),
			new FungibleSubstate(UInt256.ONE),
			mock(ReadableAddrs.class),
			null
		).getIncomplete().get();

		assertThat(state.getFirst()).isEqualTo(true);
		var usedAmount = (UsedAmount) state.getSecond();
		assertThat(usedAmount.getUsedAmount()).isEqualTo(UInt256.ONE);
		assertThat(usedAmount.isInput()).isTrue();
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		TransitionProcedure<FungibleSubstate, FungibleSubstate, VoidReducerState> procedure = new CreateFungibleTransitionRoutine<>(
			FungibleSubstate.class, FungibleSubstate.class,
			(a, b, r) -> Result.success(),
			mock(SignatureValidatorFungible.class),
			(i, o, index) -> Unknown.create()
		).getProcedure0();

		var state = procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.ONE)),
			new FungibleSubstate(UInt256.TWO),
			mock(ReadableAddrs.class),
			null
		).getIncomplete().get();

		assertThat(state.getFirst()).isFalse();
		var usedAmount = (UsedAmount) state.getSecond();
		assertThat(usedAmount.getUsedAmount()).isEqualTo(UInt256.ONE);
		assertThat(usedAmount.isInput()).isFalse();
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		TransitionProcedure<FungibleSubstate, FungibleSubstate, VoidReducerState> procedure = new CreateFungibleTransitionRoutine<>(
			FungibleSubstate.class, FungibleSubstate.class,
			(a, b, r) -> Result.success(),
			mock(SignatureValidatorFungible.class),
			(i, o, index) -> Unknown.create()
		).getProcedure0();

		assertThat(procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.TWO)),
			new FungibleSubstate(UInt256.TWO),
			mock(ReadableAddrs.class),
			null
		).getIncomplete()).isEmpty();
	}

	@Test
	public void when_validating_a_one_to_two_one_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		TransitionProcedure<FungibleSubstate, FungibleSubstate, UsedAmount> procedure = new CreateFungibleTransitionRoutine<>(
			FungibleSubstate.class, FungibleSubstate.class,
			(a, b, r) -> Result.success(),
			mock(SignatureValidatorFungible.class),
			(i, o, index) -> Unknown.create()
		).getProcedure1();

		assertThat(procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.ONE)),
			new FungibleSubstate(UInt256.TWO),
			mock(ReadableAddrs.class),
			new UsedAmount(false, UInt256.ONE, Unknown.create())
		).getIncomplete()).isEmpty();

		assertThat(procedure.inputOutputReducer().reduce(
			SubstateWithArg.noArg(new FungibleSubstate(UInt256.TWO)),
			new FungibleSubstate(UInt256.ONE),
			mock(ReadableAddrs.class),
			new UsedAmount(true, UInt256.ONE, Unknown.create())
		).getIncomplete()).isEmpty();
	}
}