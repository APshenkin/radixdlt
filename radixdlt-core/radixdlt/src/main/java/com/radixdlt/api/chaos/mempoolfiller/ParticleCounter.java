package com.radixdlt.api.chaos.mempoolfiller;

import com.google.inject.Inject;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Counts the number of UP substate which pass a predicate
 */
public final class ParticleCounter implements StateReducer<Integer, TokensParticle> {
	private final REAddr addr;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	@Inject
	public ParticleCounter(
		@Self REAddr addr
	) {
		this.addr = Objects.requireNonNull(addr);
	}

	@Override
	public Class<Integer> stateClass() {
		return Integer.class;
	}

	@Override
	public Class<TokensParticle> particleClass() {
		return TokensParticle.class;
	}

	@Override
	public Supplier<Integer> initial() {
		return () -> 0;
	}

	@Override
	public BiFunction<Integer, TokensParticle, Integer> outputReducer() {
		return (count, p) -> {
			if (p.getHoldingAddr().equals(addr)
				&& p.getResourceAddr().isNativeToken()
				&& p.getAmount().compareTo(fee.multiply(UInt256.TWO)) > 0) {
				return count + 1;
			}
			return count;
		};
	}

	@Override
	public BiFunction<Integer, TokensParticle, Integer> inputReducer() {
		return (count, p) -> {
			if (p.getHoldingAddr().equals(addr)
				&& p.getResourceAddr().isNativeToken()
				&& p.getAmount().compareTo(fee.multiply(UInt256.TWO)) > 0) {
				return count - 1;
			}
			return count;
		};
	}
}
