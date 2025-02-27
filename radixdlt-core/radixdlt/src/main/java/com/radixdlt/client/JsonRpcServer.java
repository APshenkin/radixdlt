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

package com.radixdlt.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.JsonRpcUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.undertow.server.HttpServerExchange;

import static com.radixdlt.api.JsonRpcUtil.invalidParamsError;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.methodNotFound;
import static com.radixdlt.api.JsonRpcUtil.parseError;
import static com.radixdlt.api.JsonRpcUtil.serverError;

import static java.util.Optional.ofNullable;

/**
 * Stateless Json Rpc 2.0 Server
 */
public final class JsonRpcServer {
	private static final long DEFAULT_MAX_REQUEST_SIZE = 1024L * 1024L;
	private static final Logger log = LogManager.getLogger();

	/**
	 * Maximum request size in bytes
	 */
	private final long maxRequestSizeBytes;

	/**
	 * Store to query atoms from
	 */
	private final Map<String, JsonRpcHandler> handlers = new HashMap<>();

	@Inject
	public JsonRpcServer(Map<String, JsonRpcHandler> additionalHandlers) {
		this(additionalHandlers, DEFAULT_MAX_REQUEST_SIZE);
	}

	public JsonRpcServer(
		Map<String, JsonRpcHandler> additionalHandlers,
		long maxRequestSizeBytes
	) {
		this.maxRequestSizeBytes = maxRequestSizeBytes;

		fillHandlers(additionalHandlers);
	}

	private void fillHandlers(Map<String, JsonRpcHandler> additionalHandlers) {
		handlers.putAll(additionalHandlers);
		handlers.keySet().forEach(name -> log.trace("Registered JSON RPC method: {}", name));
	}

	/**
	 * Extract a JSON RPC API request from an HttpServerExchange, handle it as usual and return the response
	 *
	 * @param exchange The JSON RPC API request
	 *
	 * @return The response
	 */
	public String handleJsonRpc(HttpServerExchange exchange) {
		try {
			return handleRpc(readBody(exchange)).toString();
		} catch (IOException e) {
			throw new IllegalStateException("RPC failed", e);
		}
	}

	private String readBody(HttpServerExchange exchange) throws IOException {
		// Switch to blocking since we need to retrieve whole request body
		exchange.setMaxEntitySize(maxRequestSizeBytes);
		exchange.startBlocking();

		return CharStreams.toString(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8));
	}

	/**
	 * Handle the string JSON-RPC request with size checks, return appropriate error if size exceeds the limit.
	 *
	 * @param requestString The string JSON-RPC request
	 *
	 * @return The response to the request, could be a JSON-RPC error
	 */
	JSONObject handleRpc(String requestString) {
		log.trace("RPC: input {}", requestString);

		int length = requestString.getBytes(StandardCharsets.UTF_8).length;

		if (length > maxRequestSizeBytes) {
			return requestTooLongError(length);
		}

		return jsonObject(requestString)
			.map(this::handle)
			.map(value -> logValue("result", value))
			.fold(failure -> parseError("Unable to parse input: " + failure.message()), v -> v);
	}

	private JSONObject handle(JSONObject request) {
		if (!request.has("id")) {
			return invalidParamsError(request, "The 'id' missing");
		}

		if (!request.has("method")) {
			return invalidParamsError(request, "The method must be specified");
		}

		try {
			return ofNullable(handlers.get(logValue("method", request.getString("method"))))
				.map(handler -> handler.execute(request))
				.orElseGet(() -> methodNotFound(request));

		} catch (Exception e) {
			log.warn("Exception while handling request: ", e);
			return serverError(request, e);
		}
	}

	private JSONObject requestTooLongError(int length) {
		log.trace("RPC error: Request too big: {} > {}", length, maxRequestSizeBytes);
		return JsonRpcUtil.requestTooLongError(length);
	}

	private static <T> T logValue(String message, T value) {
		log.trace("RPC: {} {}", message, value);
		return value;
	}
}
