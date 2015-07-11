/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.internal;

import static org.openhab.binding.zwave2.ZWaveBindingConstants.CONTROLLER_SERIAL;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.zwave2.ZWaveBindingConstants;
import org.openhab.binding.zwave2.handler.ZWaveSerialHandler;
import org.openhab.binding.zwave2.handler.ZWaveThingHandler;

/**
 * The {@link ZWaveHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Chris Jackson - Initial contribution
 */
public class ZWaveHandlerFactory extends BaseThingHandlerFactory {

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return ZWaveBindingConstants.BINDING_ID.equals(thingTypeUID.getBindingId());
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        // Handle controllers here
        if (thingTypeUID.equals(CONTROLLER_SERIAL)) {
            return new ZWaveSerialHandler(thing);
        }

        // Everything else gets handled in a single handler
        return new ZWaveThingHandler(thing);
    }
}
