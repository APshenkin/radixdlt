/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.fungible.FungibleTransitionMapper;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.client.application.translate.tokens.TokenUnitConversions.unitsToSubunits;

public class MintTokensActionMapper implements StatefulActionToParticleGroupsMapper<MintTokensAction> {
	public MintTokensActionMapper() {
		// Empty on purpose
	}

	private static List<SpunParticle> mapToParticles(MintTokensAction mint, List<UnallocatedTokensParticle> currentParticles)
		throws NotEnoughFungiblesException {

		final var totalAmountToBurn = unitsToSubunits(mint.getAmount());

		if (currentParticles.isEmpty()) {
			throw new NotEnoughFungiblesException(totalAmountToBurn, UInt256.ZERO);
		}

		final var token = currentParticles.get(0).getTokDefRef();
		final var granularity = currentParticles.get(0).getGranularity();
		final var permissions = currentParticles.get(0).getTokenPermissions();

		var mapper = new FungibleTransitionMapper<>(
			UnallocatedTokensParticle::getAmount,
			amt ->
				new UnallocatedTokensParticle(
					amt,
					granularity,
					System.nanoTime(),
					token,
					permissions
				),
			amt ->
				new TransferrableTokensParticle(
					amt,
					granularity,
					mint.getAddress(),
					System.nanoTime(),
					token,
					permissions
				)
		);

		return mapper.mapToParticles(currentParticles, totalAmountToBurn);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(MintTokensAction mintTokensAction) {
		var tokenDefinitionAddress = mintTokensAction.getRRI().getAddress();

		return Set.of(
			ShardedParticleStateId.of(UnallocatedTokensParticle.class, tokenDefinitionAddress),
			ShardedParticleStateId.of(MutableSupplyTokenDefinitionParticle.class, tokenDefinitionAddress)
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(MintTokensAction mintTokensAction, Stream<Particle> store) throws StageActionException {
		if (mintTokensAction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Mint amount must be greater than 0.");
		}

		var tokenRef = mintTokensAction.getRRI();
		var particles = store.collect(Collectors.groupingBy(Particle::getClass));
		var defParticles = particles.get(MutableSupplyTokenDefinitionParticle.class);

		validateTokenIsKnown(mintTokensAction, tokenRef, defParticles);

		var currentParticles =
			particles.getOrDefault(UnallocatedTokensParticle.class, Collections.emptyList())
				.stream()
				.map(UnallocatedTokensParticle.class::cast)
				.filter(p -> p.getTokDefRef().equals(tokenRef))
				.collect(Collectors.toList());

		try {
			var mintParticles = mapToParticles(mintTokensAction, currentParticles);
			return List.of(ParticleGroup.of(mintParticles));
		} catch (NotEnoughFungiblesException e) {
			throw new TokenOverMintException(
				mintTokensAction.getRRI(),
				TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE),
				TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE.subtract(e.getCurrent())),
				mintTokensAction.getAmount()
			);
		}
	}

	private void validateTokenIsKnown(final MintTokensAction mintTokensAction, final RRI tokenRef, final List<Particle> tokDefParticles) {
		Optional.ofNullable(tokDefParticles)
			.orElse(List.of())
			.stream()
			.map(MutableSupplyTokenDefinitionParticle.class::cast)
			.filter(p -> p.getRRI().equals(tokenRef))
			.findFirst()
			.orElseThrow(() -> {
				throw new UnknownTokenException(mintTokensAction.getRRI());
			});
	}
}
