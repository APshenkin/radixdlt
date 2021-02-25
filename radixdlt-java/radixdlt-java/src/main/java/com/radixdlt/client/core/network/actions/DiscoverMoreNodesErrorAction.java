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

package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * Action which represents an error which occurred when trying to discover more nodes
 */
public final class DiscoverMoreNodesErrorAction implements RadixNodeAction {
	private final Throwable reason;

	private DiscoverMoreNodesErrorAction(Throwable reason) {
		this.reason = reason;
	}

	public static RadixNodeAction create(final Throwable reason) {
		return new DiscoverMoreNodesErrorAction(reason);
	}

	public Throwable getReason() {
		return reason;
	}

	// TODO: remove this
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "DISCOVER_MORE_NODES";
	}
}
