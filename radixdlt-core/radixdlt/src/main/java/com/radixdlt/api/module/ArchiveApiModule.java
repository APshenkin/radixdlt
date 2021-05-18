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

package com.radixdlt.api.module;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.EndpointConfig;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.server.ArchiveServer;
import com.radixdlt.api.service.NetworkInfoService;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.service.ScheduledStatsCollecting;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;

import java.util.List;

public class ArchiveApiModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	private final List<EndpointConfig> endpoints;

	public ArchiveApiModule(List<EndpointConfig> endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public void configure() {
		endpoints.forEach(ep -> {
			log.info("Enabling /{} endpoint", ep.name());
			install(ep.module().get());
		});

		var eventBinder = Multibinder
			.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();

		bind(TransactionStatusService.class).in(Scopes.SINGLETON);
		bind(NetworkInfoService.class).in(Scopes.SINGLETON);
		bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);

		eventBinder.addBinding().toInstance(AtomsCommittedToLedger.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);
		eventBinder.addBinding().toInstance(ScheduledStatsCollecting.class);

		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.ARCHIVE_API)
			.to(ArchiveServer.class);

		bind(ArchiveServer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> clientApiStore(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledQueueFlush.class,
			clientApiStore.queueFlushProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> transactionStatusService(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledCacheCleanup.class,
			transactionStatusService.cacheCleanupProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> networkInfoService(NetworkInfoService networkInfoService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledStatsCollecting.class,
			networkInfoService.updateStats()
		);
	}
}
