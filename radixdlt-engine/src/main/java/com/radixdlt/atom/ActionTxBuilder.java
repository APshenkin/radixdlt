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

package com.radixdlt.atom;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ActionTxBuilder {
	private final AtomBuilder atomBuilder;
	private final Map<ParticleId, Particle> upParticles = new HashMap<>();
	private final Set<ParticleId> downVirtualParticles;
	private final RadixAddress address;
	// TODO: remove
	private final Random random = new Random();

	private ActionTxBuilder(
		RadixAddress address,
		Set<ParticleId> downVirtualParticles,
		List<Particle> upParticlesList
	) {
		this.address = address;
		this.atomBuilder = Atom.newBuilder();
		this.downVirtualParticles = new HashSet<>(downVirtualParticles);
		upParticlesList.forEach(p -> upParticles.put(ParticleId.of(p), p));
	}

	public static ActionTxBuilder newBuilder(RadixAddress address, List<Particle> upParticleList) {
		return new ActionTxBuilder(address, Set.of(), upParticleList);
	}

	public static ActionTxBuilder newBuilder(RadixAddress address) {
		return new ActionTxBuilder(address, Set.of(), List.of());
	}

	private void particleGroup() {
		atomBuilder.particleGroup();
	}

	private void up(Particle particle) {
		atomBuilder.spinUp(particle);
	}

	private void virtualDown(Particle particle) {
		atomBuilder.virtualSpinDown(particle);
		downVirtualParticles.add(ParticleId.ofVirtualParticle(particle));
	}

	private void down(ParticleId particleId) {
		atomBuilder.spinDown(particleId);
		upParticles.remove(particleId);
	}

	private <T extends Particle> T find(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws ActionTxException {
		var substateRead = Stream.concat(upParticles.values().stream(), atomBuilder.localUpParticles())
			.filter(particleClass::isInstance)
			.map(particleClass::cast)
			.filter(particlePredicate)
			.findFirst();
		if (substateRead.isEmpty()) {
			throw new ActionTxException(errorMessage);
		}

		return substateRead.get();
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws ActionTxException {
		return down(particleClass, particlePredicate, Optional.empty(), errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<T> virtualParticle,
		String errorMessage
	) throws ActionTxException {
		var allUpParticles = Stream.concat(atomBuilder.localUpParticles(), upParticles.values().stream())
			.filter(particleClass::isInstance)
			.map(particleClass::cast)
			.filter(particlePredicate)
			.collect(Collectors.toSet());
		var substateDown = allUpParticles.stream()
			.peek(p -> this.down(ParticleId.of(p)))
			.findFirst()
			.or(() -> {
				if (virtualParticle.isPresent()) {
					var p = virtualParticle.get();
					this.virtualDown(p);
				}
				return virtualParticle;
			});
		if (substateDown.isEmpty()) {
			throw new ActionTxException(errorMessage);
		}

		return substateDown.get();
	}

	public ActionTxBuilder validatorRegister() throws ActionTxException {
		var substateDown =
			down(
				UnregisteredValidatorParticle.class,
				p -> p.getAddress().equals(address),
				Optional.of(new UnregisteredValidatorParticle(address, 0L)),
				"Already a validator"
			);

		var substateUp = new RegisteredValidatorParticle(address, ImmutableSet.of(), substateDown.getNonce() + 1);
		up(substateUp);
		particleGroup();
		return this;
	}

	public ActionTxBuilder validatorUnregister() throws ActionTxException {
		var substateDown =
			down(
				RegisteredValidatorParticle.class,
				p -> p.getAddress().equals(address),
				"Already unregistered."
			);
		var substateUp = new UnregisteredValidatorParticle(address, substateDown.getNonce() + 1);
		up(substateUp);
		particleGroup();
		return this;
	}

	private <T extends Particle> UInt256 downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		UInt256 amount,
		String errorMessage
	) throws ActionTxException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			var substateDown = down(
				particleClass,
				particlePredicate,
				errorMessage
			);

			spent = spent.add(amountMapper.apply(substateDown));
		}

		return spent.subtract(amount);
	}

	public ActionTxBuilder createMutableToken(TokenDefinition tokenDefinition) throws ActionTxException {
		final var tokenRRI = RRI.of(address, tokenDefinition.getSymbol());
		down(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		);
		final var factory = TokDefParticleFactory.create(
			tokenRRI, tokenDefinition.getTokenPermissions(), UInt256.ONE
		);
		up(factory.createUnallocated(UInt256.MAX_VALUE));
		up(new MutableSupplyTokenDefinitionParticle(
			tokenRRI,
			tokenDefinition.getName(),
			tokenDefinition.getDescription(),
			tokenDefinition.getGranularity(),
			tokenDefinition.getIconUrl(),
			tokenDefinition.getTokenUrl(),
			tokenDefinition.getTokenPermissions()
		));
		particleGroup();

		return this;
	}

	public ActionTxBuilder mint(RRI rri, RadixAddress to, UInt256 amount) throws ActionTxException {
		var tokenDefSubstate = find(
			MutableSupplyTokenDefinitionParticle.class,
			p -> p.getRRI().equals(rri),
			"Could not find token rri " + rri
		);

		final var factory = TokDefParticleFactory.create(
			rri, tokenDefSubstate.getTokenPermissions(), tokenDefSubstate.getGranularity()
		);
		up(factory.createTransferrable(to, amount, random.nextLong()));
		var remainder = downFungible(
			UnallocatedTokensParticle.class,
			p -> p.getTokDefRef().equals(rri),
			UnallocatedTokensParticle::getAmount,
			amount,
			"Not enough balance to for transfer."
		);
		if (!remainder.isZero()) {
			var substateUp = factory.createUnallocated(remainder);
			up(substateUp);
		}
		particleGroup();

		return this;
	}

	public ActionTxBuilder transfer(RRI rri, RadixAddress to, UInt256 amount) throws ActionTxException {
		var tokenDefSubstate = find(
			MutableSupplyTokenDefinitionParticle.class,
			p -> p.getRRI().equals(rri),
			"Could not find token rri " + rri
		);

		final var factory = TokDefParticleFactory.create(
			rri, tokenDefSubstate.getTokenPermissions(), tokenDefSubstate.getGranularity()
		);
		up(factory.createTransferrable(to, amount, random.nextLong()));
		var remainder = downFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amount,
			"Not enough balance to for transfer."
		);
		if (!remainder.isZero()) {
			var substateUp = factory.createTransferrable(address, remainder, random.nextLong());
			up(substateUp);
		}
		particleGroup();

		return this;
	}

	public ActionTxBuilder burnForFee(RRI rri, UInt256 amount) throws ActionTxException {
		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);
		up(factory.createUnallocated(amount));
		var remainder = downFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amount,
			"Not enough balance to burn for fees."
		);
		if (!remainder.isZero()) {
			var substateUp = factory.createTransferrable(address, remainder, random.nextLong());
			up(substateUp);
		}

		particleGroup();
		return this;
	}

	public Atom signAndBuild(Function<HashCode, ECDSASignature> signer) {
		return atomBuilder.signAndBuild(signer);
	}

	public Stream<Particle> upParticles() {
		return Stream.concat(upParticles.values().stream(), atomBuilder.localUpParticles());
	}
}