/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.store.ReadableAddrs;

public final class AllocateTokensRoutine implements ConstraintRoutine {
	@Override
	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(
				VoidParticle.class,
				TokensParticle.class,
				TypeToken.of(VoidReducerState.class)
			),
			new TransitionProcedure<>() {
				@Override
				public Result precondition(
					SubstateWithArg<VoidParticle> in,
					TokensParticle outputParticle,
					VoidReducerState inputUsed,
					ReadableAddrs readableAddrs
				) {
					var p = readableAddrs.loadAddr(null, outputParticle.getResourceAddr());
					if (p.isEmpty()) {
						return Result.error("Token does not exist.");
					}

					var particle = p.get();
					if (!(particle instanceof TokenDefinitionParticle)) {
						return Result.error("Rri is not a token");
					}

					var tokenDef = (TokenDefinitionParticle) particle;

					if (!tokenDef.isMutable()) {
						return Result.error("Can only mint mutable tokens.");
					}

					return Result.success();
				}

				@Override
				public PermissionLevel requiredPermissionLevel(
					SubstateWithArg<VoidParticle> i, TokensParticle o, ReadableAddrs index
				) {
					return o.getResourceAddr().isNativeToken() ? PermissionLevel.SYSTEM : PermissionLevel.USER;
				}

				@Override
				public InputOutputReducer<VoidParticle, TokensParticle, VoidReducerState> inputOutputReducer() {
					return (i, o, index, outputUsed) ->
						ReducerResult.complete(new MintToken(o.getResourceAddr(), o.getHoldingAddr(), o.getAmount()));
				}

				@Override
				public SignatureValidator<VoidParticle, TokensParticle> signatureValidator() {
					return (i, o, index, publicKey) -> {
						var tokenDef = (TokenDefinitionParticle) index.loadAddr(null, o.getResourceAddr()).orElseThrow();
						return publicKey.flatMap(p -> tokenDef.getMinter().map(p::equals)).orElse(false);
					};
				}
			}
		);
	}
}
