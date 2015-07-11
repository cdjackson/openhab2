/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
 package org.openhab.binding.zwave2.internal.protocol.event;

import org.openhab.binding.zwave2.internal.protocol.ZWaveNodeState;

/**
 * Node status event is used to signal if a node is alive or dead
 * @author Chris Jackson
 */
public class ZWaveNodeStatusEvent extends ZWaveEvent {
	ZWaveNodeState state;

	/**
	 * Constructor. Creates a new instance of the ZWaveNetworkEvent class.
	 * @param nodeId the nodeId of the event.
	 */
	public ZWaveNodeStatusEvent(int nodeId, ZWaveNodeState state) {
		super(nodeId);

		this.state = state;
	}

	public ZWaveNodeState getState() {
		return state;
	}
}
