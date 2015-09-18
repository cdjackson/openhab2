/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee;

import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.ImmutableSet;

/**
 * The {@link ZigBeeBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZigBeeBindingConstants {

    // Binding Name
    public static final String BINDING_ID = "zigbee";

    // Coordinator (Bridges)
    public final static ThingTypeUID COORDINATOR_TYPE_CC2530 = new ThingTypeUID(BINDING_ID, "coordinator_cc2530");

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_ON_OFF_LIGHT = new ThingTypeUID(BINDING_ID, "OnOffLight");
    public final static ThingTypeUID THING_TYPE_DIMMABLE_LIGHT = new ThingTypeUID(BINDING_ID, "DimmableLight");
    public final static ThingTypeUID THING_TYPE_COLOR_DIMMABLE_LIGHT = new ThingTypeUID(BINDING_ID,
            "ColorDimmableLight");
    public final static ThingTypeUID THING_TYPE_TEMPERATURE = new ThingTypeUID(BINDING_ID, "temperature");

    // List of all Channel ids
    public final static String CHANNEL_CFG_BINDING = "binding";

    public final static String CHANNEL_TEMPERATURE = "temperature";
    public final static String CHANNEL_SWITCH = "switch";
    public static final String CHANNEL_COLORTEMPERATURE = "color_temperature";
    public static final String CHANNEL_COLOR = "color";
    public static final String CHANNEL_BRIGHTNESS = "brightness";

    // List of all parameters
	public final static String PARAMETER_PANID = "panid";
	public final static String PARAMETER_CHANNEL = "channel";
    public final static String PARAMETER_PORT = "port";
    public final static String PARAMETER_RESET = "reset";

    public final static String PARAMETER_MACADDRESS = "macAddress";

    public final static Set<ThingTypeUID> SUPPORTED_BRIDGE_TYPES_UIDS = ImmutableSet.of(COORDINATOR_TYPE_CC2530);

    public final static Set<ThingTypeUID> SUPPORTED_DEVICE_TYPES_UIDS = ImmutableSet.of(THING_TYPE_TEMPERATURE,
            THING_TYPE_ON_OFF_LIGHT, THING_TYPE_DIMMABLE_LIGHT, THING_TYPE_COLOR_DIMMABLE_LIGHT);

    public final static Set<ThingTypeUID> SUPPORTED_LIGHT_DEVICE_TYPES_UIDS = ImmutableSet.of(THING_TYPE_ON_OFF_LIGHT,
            THING_TYPE_DIMMABLE_LIGHT, THING_TYPE_COLOR_DIMMABLE_LIGHT);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = ImmutableSet.of(
            // Coordinators
            COORDINATOR_TYPE_CC2530,

    // Things
            THING_TYPE_TEMPERATURE, THING_TYPE_ON_OFF_LIGHT, THING_TYPE_DIMMABLE_LIGHT,
            THING_TYPE_COLOR_DIMMABLE_LIGHT);
}
