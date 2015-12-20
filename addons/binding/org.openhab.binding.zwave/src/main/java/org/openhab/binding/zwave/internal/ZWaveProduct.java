/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal;

import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.osgi.framework.Version;

public class ZWaveProduct {
    ThingTypeUID thingTypeUID;
    Integer manufacturer;
    Integer type;
    Integer id;
    Version versionMin;
    Version versionMax;

    ZWaveProduct(ThingTypeUID thingTypeUID, Integer manufacturer, Integer type, Integer id) {
        this(thingTypeUID, manufacturer, type, id, null, null);
    }

    ZWaveProduct(ThingTypeUID thingTypeUID, Integer manufacturer, Integer type, Integer id, String versionMin,
            String versionMax) {
        this.thingTypeUID = thingTypeUID;
        this.manufacturer = manufacturer;
        this.type = type;
        this.id = id;
        if (versionMin == null) {
            this.versionMin = new Version("0.0");
        } else {
            this.versionMin = new Version(versionMin);
        }
        if (versionMax == null) {
            this.versionMax = new Version("255.255");
        } else {
            this.versionMax = new Version(versionMax);
        }
    }

    /**
     * Check for a version'ed file
     * There are multiple permutations of the file that we need to account for -:
     * <ul>
     * <li>No version information
     * <li>Only a min version
     * <li>Only a max version
     * <li>Both min and max versions Versions need to be evaluated with the max and min specifiers separately. i.e. the
     * part either side of the decimal. So, version 1.3 is lower than version 1.11
     *
     * @param node
     * @return true if this product matches the node
     */
    public boolean match(ZWaveNode node) {
        Version vIn = new Version(node.getApplicationVersion());

        if (manufacturer != node.getManufacturer()) {
            return false;
        }

        if (type != null && type != node.getDeviceType()) {
            return false;
        }

        if (id != null && id != node.getDeviceId()) {
            return false;
        }

        if (versionMin != null) {
            if (vIn.compareTo(versionMin) < 0) {
                return false;
            }
        }

        if (versionMax != null) {
            if (vIn.compareTo(versionMax) > 0) {
                return false;
            }
        }

        // This version matches the criterea
        return true;
    }

    public ThingTypeUID getThingTypeUID() {
        return thingTypeUID;
    }
}
