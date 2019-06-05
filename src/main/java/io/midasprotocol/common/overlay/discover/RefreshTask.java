/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.common.overlay.discover;

import io.midasprotocol.common.overlay.discover.node.Node;
import io.midasprotocol.common.overlay.discover.node.NodeManager;

import java.util.ArrayList;
import java.util.Random;

public class RefreshTask extends DiscoverTask {

	public RefreshTask(NodeManager nodeManager) {
		super(nodeManager);
	}

	public static byte[] getNodeId() {
		Random gen = new Random();
		byte[] id = new byte[64];
		gen.nextBytes(id);
		return id;
	}

	@Override
	public void run() {
		discover(getNodeId(), 0, new ArrayList<Node>());
	}
}
