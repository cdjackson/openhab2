package org.openhab.binding.zwave.internal.protocol;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 *
 * @author Chris Jackson - Initial contribution
 *
 */
@XStreamAlias("multiAssociationCommandClass")
public class ZWaveAssociation {
    private int node;
    private int endpoint;

    public ZWaveAssociation(int node) {
        this.node = node;
        this.endpoint = 0;
    }

    public ZWaveAssociation(int node, int endpoint) {
        this.node = node;
        this.endpoint = endpoint;
    }

    public int getNode() {
        return node;
    }

    public int getEndpoint() {
        return endpoint;
    }

    @Override
    public boolean equals(Object checker) {
        ZWaveAssociation assoc = (ZWaveAssociation) checker;
        if (this.node == assoc.node && this.endpoint == assoc.endpoint) {
            return true;
        }
        return false;
    }
}