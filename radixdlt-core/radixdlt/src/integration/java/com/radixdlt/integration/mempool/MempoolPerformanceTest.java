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

package com.radixdlt.integration.mempool;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerKey;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.store.DatabaseLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests relative performance between an empty mempool and a full mempool and checks
 * to see that performance between the two isn't wildly different.
 */
public final class MempoolPerformanceTest {
    private static final Logger logger = LogManager.getLogger();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Inject @Self private ECKeyPair self;
    @Inject private DeterministicEpochsConsensusProcessor processor;
    @Inject private DeterministicNetwork network;
    @Inject private EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
    @Inject private EventDispatcher<ScheduledMempoolFill> scheduledMempoolFillEventDispatcher;

    private Injector createInjector() {
        return Guice.createInjector(
            new SingleNodeAndPeersDeterministicNetworkModule(),
            new MempoolFillerModule(),
            new AbstractModule() {
                @Override
                protected void configure() {
                    bindConstant().annotatedWith(Names.named("numPeers")).to(0);
                    bindConstant().annotatedWith(MempoolMaxSize.class).to(1000);
                    bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
                    bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
                }

                @ProvidesIntoSet
                private TokenIssuance mempoolFillerIssuance(@MempoolFillerKey ECPublicKey mempoolFillerKey) {
                    return TokenIssuance.of(mempoolFillerKey, TokenUnitConversions.unitsToSubunits(10000000000L));
                }
            }
        );
    }

    private void runUntil(View view, Runnable runnable) {
        while (true) {
            ControlledMessage msg = network.nextMessage().value();
            processor.handleMessage(msg.origin(), msg.message());

            if (msg.message() instanceof EpochViewUpdate) {
                EpochViewUpdate viewUpdate = (EpochViewUpdate) msg.message();
                if (viewUpdate.getViewUpdate().getCurrentView().gt(view)) {
                    break;
                }

                if (viewUpdate.getViewUpdate().getCurrentView().number() % 100 == 0) {
                    runnable.run();
                }
            }
        }
    }

    @Test
    public void validate_that_full_mempool_doesnt_take_too_much_time() {
        createInjector().injectMembers(this);
        processor.start();

        long start0 = System.currentTimeMillis();
        runUntil(View.of(2000), () -> { });
        long end0 = System.currentTimeMillis();

        long start1 = System.currentTimeMillis();
        mempoolFillerUpdateEventDispatcher.dispatch(MempoolFillerUpdate.create(true));
        runUntil(View.of(4000), () -> scheduledMempoolFillEventDispatcher.dispatch(ScheduledMempoolFill.create()));
        long end1 = System.currentTimeMillis();

        long duration0 = end0 - start0;
        long duration1 = end1 - start1;
        double change = ((double) duration1) / duration0;
        logger.info("Change: {} No-Mempool: {} Mempool: {}", change, duration0, duration1);

        assertThat(change).isLessThan(2.0);
    }
}