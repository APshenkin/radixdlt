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

package org.radix.api.jsonrpc.handler;

import org.json.JSONObject;
import org.radix.api.services.SystemService;

import static org.radix.api.jsonrpc.JsonRpcUtil.response;

public class SystemHandler {
	private final SystemService systemService;

	public SystemHandler(final SystemService systemService) {
		this.systemService = systemService;
	}

	public JSONObject handleGetUniverse(JSONObject request) {
		return response(request, systemService.getUniverse());
	}

	public JSONObject handleGetLocalSystem(JSONObject request) {
		return response(request, systemService.getLocalSystem());
	}

	public JSONObject handlePing(JSONObject request) {
		return response(request, systemService.getPong());
	}

	public JSONObject handleBftStart(JSONObject request) {
		return response(request, systemService.bftStart());
	}

	public JSONObject handleBftStop(JSONObject request) {
		return response(request, systemService.bftStop());
	}
}