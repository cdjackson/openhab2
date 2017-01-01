/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.lutron.internal.grxprg;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.NullArgumentException;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the protocol handler for the GRX-PRG/GRX-CI-PRG. This handler will issue the protocol commands and will
 * process the responses from the interface. This handler was written to respond to any response that can be sent from
 * the TCP/IP session (either in response to our own commands or in response to external events [other TCP/IP sessions,
 * web GUI, etc]).
 *
 * @author Tim Roberts
 *
 */
class PrgProtocolHandler {
    private Logger logger = LoggerFactory.getLogger(PrgProtocolHandler.class);

    /**
     * The {@link SocketSession} used by this protocol handler
     */
    private final SocketSession _session;

    /**
     * The {@link PrgBridgeHandler} to call back to update status and state
     */
    private final PrgHandlerCallback _callback;

    // ------------------------------------------------------------------------------------------------
    // The following are the various command formats specified by the
    // http://www.lutron.com/TechnicalDocumentLibrary/RS232ProtocolCommandSet.040196d.pdf
    private final static String CMD_SCENE = "A";
    private final static String CMD_SCENELOCK = "SL";
    private final static String CMD_SCENESTATUS = "G";
    private final static String CMD_SCENESEQ = "SQ";
    private final static String CMD_ZONELOCK = "ZL";
    private final static String CMD_ZONELOWER = "D";
    private final static String CMD_ZONELOWERSTOP = "E";
    private final static String CMD_ZONERAISE = "B";
    private final static String CMD_ZONERAISESTOP = "C";
    private final static String CMD_ZONEINTENSITY = "szi";
    private final static String CMD_ZONEINTENSITYSTATUS = "rzi";
    private final static String CMD_SETTIME = "ST";
    private final static String CMD_READTIME = "RT";
    private final static String CMD_SELECTSCHEDULE = "SS";
    private final static String CMD_REPORTSCHEDULE = "RS";
    private final static String CMD_SUNRISESUNSET = "RA";
    private final static String CMD_SUPERSEQUENCESTART = "QS";
    private final static String CMD_SUPERSEQUENCEPAUSE = "QP";
    private final static String CMD_SUPERSEQUENCERESUME = "QC";
    private final static String CMD_SUPERSEQUENCESTATUS = "Q?";

    // ------------------------------------------------------------------------------------------------
    // The following are the various responses specified by the
    // http://www.lutron.com/TechnicalDocumentLibrary/RS232ProtocolCommandSet.040196d.pdf
    private final static Pattern RSP_FAILED = Pattern.compile("^~ERROR # (\\d+) (\\d+) OK");
    private final static Pattern RSP_OK = Pattern.compile("^~(\\d+) OK");
    private final static Pattern RSP_RESETTING = Pattern.compile("^~:Reseting Device... (\\d+) OK");
    private final static Pattern RSP_RMU = Pattern
            .compile("^~:mu (\\d) (\\d+) (\\w+) (\\w+) (\\w+) (\\w+) (\\w+) (\\w+) (\\w+)");
    private final static Pattern RSP_SCENESTATUS = Pattern.compile("^~?:ss (\\w{8,8})( (\\d+) OK)?");
    private final static Pattern RSP_ZONEINTENSITY = Pattern.compile(
            "^~:zi (\\d) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\w{1,3}) (\\d+) OK");
    private final static Pattern RSP_REPORTIME = Pattern
            .compile("^~:rt (\\d{1,2}) (\\d{1,2}) (\\d{1,2}) (\\d{1,2}) (\\d{1,2}) (\\d) (\\d+) OK");
    private final static Pattern RSP_REPORTSCHEDULE = Pattern.compile("^~:rs (\\d) (\\d+) OK");
    private final static Pattern RSP_SUNRISESUNSET = Pattern
            .compile("^~:ra (\\d{1,3}) (\\d{1,3}) (\\d{1,3}) (\\d{1,3}) (\\d+) OK");
    private final static Pattern RSP_SUPERSEQUENCESTATUS = Pattern
            .compile("^~:s\\? (\\w) (\\d+) (\\d{1,2}) (\\d{1,2}) (\\d+) OK");
    private final static Pattern RSP_BUTTON = Pattern.compile("^[^~:].*");
    private final static String RSP_CONNECTION_ESTABLISHED = "connection established";

    /**
     * A lookup between a 0-100 percentage and corresponding hex value. Note: this specifically matches the liason
     * software setup
     */
    private final static HashMap<Integer, String> intensityMap = new HashMap<Integer, String>();

    /**
     * The reverse lookup for the {{@link #intensityMap}
     */
    private final static HashMap<String, Integer> reverseIntensityMap = new HashMap<String, Integer>();

    /**
     * A lookup between returned shade hex intensity to corresponding shade values
     */
    private final static HashMap<String, Integer> shadeIntensityMap = new HashMap<String, Integer>();

    /**
     * Cache of current zone intensities
     */
    private final int[] zoneIntensities = new int[8];

    /**
     * Static method to setup the intensity lookup maps
     */
    static {
        intensityMap.put(0, "0");
        intensityMap.put(1, "2");
        intensityMap.put(2, "3");
        intensityMap.put(3, "4");
        intensityMap.put(4, "6");
        intensityMap.put(5, "7");
        intensityMap.put(6, "8");
        intensityMap.put(7, "9");
        intensityMap.put(8, "B");
        intensityMap.put(9, "C");
        intensityMap.put(10, "D");
        intensityMap.put(11, "F");
        intensityMap.put(12, "10");
        intensityMap.put(13, "11");
        intensityMap.put(14, "12");
        intensityMap.put(15, "14");
        intensityMap.put(16, "15");
        intensityMap.put(17, "16");
        intensityMap.put(18, "18");
        intensityMap.put(19, "19");
        intensityMap.put(20, "1A");
        intensityMap.put(21, "1B");
        intensityMap.put(22, "1D");
        intensityMap.put(23, "1E");
        intensityMap.put(24, "1F");
        intensityMap.put(25, "20");
        intensityMap.put(26, "22");
        intensityMap.put(27, "23");
        intensityMap.put(28, "24");
        intensityMap.put(29, "26");
        intensityMap.put(30, "27");
        intensityMap.put(31, "28");
        intensityMap.put(32, "29");
        intensityMap.put(33, "2B");
        intensityMap.put(34, "2C");
        intensityMap.put(35, "2D");
        intensityMap.put(36, "2F");
        intensityMap.put(37, "30");
        intensityMap.put(38, "31");
        intensityMap.put(39, "32");
        intensityMap.put(40, "34");
        intensityMap.put(41, "35");
        intensityMap.put(42, "36");
        intensityMap.put(43, "38");
        intensityMap.put(44, "39");
        intensityMap.put(45, "3A");
        intensityMap.put(46, "3B");
        intensityMap.put(47, "3D");
        intensityMap.put(48, "3E");
        intensityMap.put(49, "3F");
        intensityMap.put(50, "40");
        intensityMap.put(51, "42");
        intensityMap.put(52, "43");
        intensityMap.put(53, "44");
        intensityMap.put(54, "46");
        intensityMap.put(55, "47");
        intensityMap.put(56, "48");
        intensityMap.put(57, "49");
        intensityMap.put(58, "4B");
        intensityMap.put(59, "4C");
        intensityMap.put(60, "4D");
        intensityMap.put(61, "4F");
        intensityMap.put(62, "50");
        intensityMap.put(63, "51");
        intensityMap.put(64, "52");
        intensityMap.put(65, "54");
        intensityMap.put(66, "55");
        intensityMap.put(67, "56");
        intensityMap.put(68, "58");
        intensityMap.put(69, "59");
        intensityMap.put(70, "5A");
        intensityMap.put(71, "5B");
        intensityMap.put(72, "5D");
        intensityMap.put(73, "5E");
        intensityMap.put(74, "5F");
        intensityMap.put(75, "60");
        intensityMap.put(76, "62");
        intensityMap.put(77, "63");
        intensityMap.put(78, "64");
        intensityMap.put(79, "66");
        intensityMap.put(80, "67");
        intensityMap.put(81, "68");
        intensityMap.put(82, "69");
        intensityMap.put(83, "6B");
        intensityMap.put(84, "6C");
        intensityMap.put(85, "6D");
        intensityMap.put(86, "6F");
        intensityMap.put(87, "70");
        intensityMap.put(88, "71");
        intensityMap.put(89, "72");
        intensityMap.put(90, "74");
        intensityMap.put(91, "75");
        intensityMap.put(92, "76");
        intensityMap.put(93, "78");
        intensityMap.put(94, "79");
        intensityMap.put(95, "7A");
        intensityMap.put(96, "7B");
        intensityMap.put(97, "7D");
        intensityMap.put(98, "7E");
        intensityMap.put(99, "7F");
        intensityMap.put(100, "7F");

        for (int key : intensityMap.keySet()) {
            String value = intensityMap.get(key);
            reverseIntensityMap.put(value, key);
        }

        shadeIntensityMap.put("0", 0);
        shadeIntensityMap.put("5E", 0);
        shadeIntensityMap.put("15", 1);
        shadeIntensityMap.put("2D", 2);
        shadeIntensityMap.put("71", 3);
        shadeIntensityMap.put("72", 4);
        shadeIntensityMap.put("73", 5);
        shadeIntensityMap.put("5F", 1);
        shadeIntensityMap.put("60", 2);
        shadeIntensityMap.put("61", 3);
        shadeIntensityMap.put("62", 4);
        shadeIntensityMap.put("63", 5);
    }

    /**
     * Lookup of valid scene numbers (H is also sometimes returned - no idea what it is however)
     */
    private final static String VALID_SCENES = "0123456789ABCDEFG";

    /**
     * Constructs the protocol handler from given parameters
     *
     * @param session a non-null {@link SocketSession} (may be connected or disconnected)
     * @param config a non-null {@link PrgHandlerCallback}
     */
    PrgProtocolHandler(SocketSession session, PrgHandlerCallback callback) {

        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }

        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }

        _session = session;
        _callback = callback;
    }

    /**
     * Attempts to log into the interface.
     *
     * @return a null if logged in successfully. Non-null if an exception occurred.
     * @throws IOException an IO exception occurred during login
     */
    String login(String username) throws Exception {

        logger.info("Logging into the PRG interface");
        final NoDispatchingCallback callback = new NoDispatchingCallback();
        _session.setCallback(callback);

        String response = callback.getResponse();
        if (response.equals("login")) {
            _session.sendCommand(username);
        } else {
            return "Protocol violation - wasn't initially a command failure or login prompt: " + response;
        }

        // We should have received back a connection established response
        response = callback.getResponse();

        // Burn the empty response if we got one (
        if (response.equals("")) {
            response = callback.getResponse();
        }

        if (RSP_CONNECTION_ESTABLISHED.equals(response)) {
            postLogin();
            return null;
        } else {
            return "login failed";
        }
    }

    /**
     * Post successful login stuff - mark us online and refresh from the switch
     *
     * @throws IOException
     */
    private void postLogin() throws IOException {
        logger.info("PRG interface now connected");
        _session.setCallback(new NormalResponseCallback());
        _callback.statusChanged(ThingStatus.ONLINE, ThingStatusDetail.NONE, null);
    }

    /**
     * Refreshes the state of the specified control unit
     *
     * @param controlUnit the control unit to refresh
     */
    void refreshState(int controlUnit) {
        logger.debug("Refreshing control unit (" + controlUnit + ") state");
        refreshScene();
        refreshTime();
        refreshSchedule();
        refreshSunriseSunset();
        reportSuperSequenceStatus();

        // The RMU would return the zone lock, scene lock and scene seq state
        // Unfortunately, if any of those are true - the PRG interface locks up
        // the response until turned off - so comment out

        // Get the current state of the zone/scene lock
        // sendCommand("spm");
        // sendCommand("rmu " + controlUnit);
        // sendCommand("epm");

        refreshZoneIntensity(controlUnit);
    }

    /**
     * Validate the control unit parameter
     *
     * @param controlUnit a control unit between 1-8
     * @throws IllegalArgumentException if controlUnit is < 0 or > 8
     */
    private void validateControlUnit(int controlUnit) {
        if (controlUnit < 1 || controlUnit > 8) {
            throw new IllegalArgumentException("Invalid control unit (must be between 1 and 8): " + controlUnit);
        }
    }

    /**
     * Validates the scene and converts it to the corresponding hex value
     *
     * @param scene a scene between 0 and 16
     * @return the valid hex value of the scene
     * @throws IllegalArgumentException if scene is < 0 or > 16
     */
    private char convertScene(int scene) {
        if (scene < 0 || scene > VALID_SCENES.length()) {
            throw new IllegalArgumentException(
                    "Invalid scene (must be between 0 and " + VALID_SCENES.length() + "): " + scene);
        }
        return VALID_SCENES.charAt(scene);
    }

    /**
     * Validates the zone
     *
     * @param zone the zone to validate
     * @throws IllegalArgumentException if zone < 1 or > 8
     */
    private void validateZone(int zone) {
        if (zone < 1 || zone > 8) {
            throw new IllegalArgumentException("Invalid zone (must be between 1 and 8): " + zone);
        }

    }

    /**
     * Validates the fade and converts it to hex
     *
     * @param fade the fade
     * @return a valid fade value
     * @throws IllegalArgumentException if fade < 0 or > 120
     */
    private String convertFade(int fade) {
        if (fade < 0 || fade > 120) {
            throw new IllegalArgumentException("Invalid fade (must be between 1 and 120): " + fade);
        }
        if (fade > 59) {
            fade = (fade / 60) + 59;
        }
        return Integer.toHexString(fade).toUpperCase();
    }

    /**
     * Validates a zone intensity and returns the hex corresponding value (handles shade intensity zones as well)
     *
     * @param controlUnit the control unit
     * @param zone the zone
     * @param intensity the new intensity level
     * @return a valid hex representation
     * @throws IllegalArgumentException if controlUnit, zone or intensity are invalid
     */
    private String convertIntensity(int controlUnit, int zone, int intensity) {
        validateControlUnit(controlUnit);
        validateZone(zone);

        if (intensity < 0 || intensity > 100) {
            throw new IllegalArgumentException("Invalid intensity (must be between 0 and 100): " + intensity);
        }

        final boolean isShade = _callback.isShade(controlUnit, zone);
        if (isShade) {
            if (intensity > 5) {
                throw new IllegalArgumentException("Invalid SHADE intensity (must be between 0 and 5): " + intensity);
            }
            return Integer.toString(intensity);
        } else {
            final String hexNbr = intensityMap.get(intensity);
            if (hexNbr == null) { // this should be impossible as all 100 values are in table
                logger.warn("Unknown zone intensity (" + intensity + ")");
                return Integer.toHexString(intensity).toUpperCase();
            }
            return hexNbr;
        }
    }

    /**
     * Converts a hex zone intensity back to a integer - handles shade zones as well
     *
     * @param controlUnit the control unit
     * @param zone the zone
     * @param intensity the hex intensity value
     * @return the new intensity (between 0-100)
     * @throws IllegalArgumentException if controlUnit, zone or intensity are invalid
     */
    private int convertIntensity(int controlUnit, int zone, String intensity) {
        validateControlUnit(controlUnit);
        validateZone(zone);

        final boolean isShade = _callback.isShade(controlUnit, zone);

        if (isShade) {
            final Integer intNbr = shadeIntensityMap.get(intensity);
            if (intNbr == null) {
                logger.warn("Unknown shade intensity (" + intensity + ")");
                return Integer.parseInt(intensity, 16);
            }
            return intNbr;
        } else {
            final Integer intNbr = reverseIntensityMap.get(intensity);
            if (intNbr == null) {
                logger.warn("Unknown zone intensity (" + intensity + ")");
                return Integer.parseInt(intensity, 16);
            }
            zoneIntensities[zone] = intNbr;
            return intNbr;
        }
    }

    /**
     * Selects a specific scene on a control unit
     *
     * @param controlUnit the control unit
     * @param scene the new scene
     * @throws IllegalArgumentException if controlUnit or scene are invalid
     */
    void selectScene(int controlUnit, int scene) {
        validateControlUnit(controlUnit);
        sendCommand(CMD_SCENE + convertScene(scene) + controlUnit);
    }

    /**
     * Queries the interface for the current scene status on all control units
     */
    void refreshScene() {
        sendCommand(CMD_SCENESTATUS);
    }

    /**
     * Sets the scene locked/unlocked for the specific control unit
     *
     * @param controlUnit the control unit
     * @param locked true for locked, false otherwise
     * @throws IllegalArgumentException if controlUnit is invalid
     */
    void setSceneLock(int controlUnit, boolean locked) {
        validateControlUnit(controlUnit);
        sendCommand(CMD_SCENELOCK + (locked ? "+" : "-") + controlUnit);
    }

    /**
     * Sets the scene sequence on/off for the specific control unit
     *
     * @param controlUnit the control unit
     * @param on true for sequencing on, false otherwise
     * @throws IllegalArgumentException if controlUnit is invalid
     */
    void setSceneSequence(int controlUnit, boolean on) {
        validateControlUnit(controlUnit);
        sendCommand(CMD_SCENESEQ + (on ? "+" : "-") + controlUnit);
    }

    /**
     * Sets the zone locked/unlocked for the specific control unit
     *
     * @param controlUnit the control unit
     * @param locked true for locked, false otherwise
     * @throws IllegalArgumentException if controlUnit is invalid
     */
    void setZoneLock(int controlUnit, boolean locked) {
        validateControlUnit(controlUnit);
        sendCommand(CMD_ZONELOCK + (locked ? "+" : "-") + controlUnit);
    }

    /**
     * Sets the zone to lowering for the specific control unit
     *
     * @param controlUnit the control unit
     * @param zone the zone to lower
     * @throws IllegalArgumentException if controlUnit or zone is invalid
     */
    void setZoneLower(int controlUnit, int zone) {
        validateControlUnit(controlUnit);
        validateZone(zone);
        sendCommand(CMD_ZONELOWER + controlUnit + zone);
    }

    /**
     * Stops the zone lowering on all control units
     */
    void setZoneLowerStop() {
        sendCommand(CMD_ZONELOWERSTOP);
    }

    /**
     * Sets the zone to raising for the specific control unit
     *
     * @param controlUnit the control unit
     * @param zone the zone to raise
     * @throws IllegalArgumentException if controlUnit or zone is invalid
     */
    void setZoneRaise(int controlUnit, int zone) {
        validateControlUnit(controlUnit);
        validateZone(zone);
        sendCommand(CMD_ZONERAISE + controlUnit + zone);
    }

    /**
     * Stops the zone raising on all control units
     */
    void setZoneRaiseStop() {
        sendCommand(CMD_ZONERAISESTOP);
    }

    /**
     * Sets the zone intensity up/down by 1 with the corresponding fade time on the specific zone/control unit. Does
     * nothing if already at floor or ceiling. If the specified zone is a shade, does nothing.
     *
     * @param controlUnit the control unit
     * @param zone the zone
     * @param fade the fade time (0-59 seconds, 60-3600 seconds converted to minutes)
     * @param increase true to increase by 1, false otherwise
     * @throws IllegalArgumentException if controlUnit, zone or fade is invalid
     */
    void setZoneIntensity(int controlUnit, int zone, int fade, boolean increase) {
        if (_callback.isShade(controlUnit, zone)) {
            return;
        }

        validateControlUnit(controlUnit);
        validateZone(zone);

        int newInt = zoneIntensities[zone] += (increase ? 1 : -1);
        if (newInt < 0) {
            newInt = 0;
        }
        if (newInt > 100) {
            newInt = 100;
        }

        setZoneIntensity(controlUnit, zone, fade, newInt);
    }

    /**
     * Sets the zone intensity to a specific number with the corresponding fade time on the specific zone/control unit.
     * If a shade, only deals with intensities from 0 to 5 (stop, open close, preset 1, preset 2, preset 3).
     *
     * @param controlUnit the control unit
     * @param zone the zone
     * @param fade the fade time (0-59 seconds, 60-3600 seconds converted to minutes)
     * @param increase true to increase by 1, false otherwise
     * @throws IllegalArgumentException if controlUnit, zone, fade or intensity is invalid
     */
    void setZoneIntensity(int controlUnit, int zone, int fade, int intensity) {
        validateControlUnit(controlUnit);
        validateZone(zone);

        final String hexFade = convertFade(fade);
        final String hexIntensity = convertIntensity(controlUnit, zone, intensity);

        final StringBuffer sb = new StringBuffer(16);
        for (int z = 1; z <= 8; z++) {
            sb.append(' ');
            sb.append(zone == z ? hexIntensity : "*");
        }

        sendCommand(CMD_ZONEINTENSITY + " " + controlUnit + " " + hexFade + sb);
    }

    /**
     * Refreshes the current zone intensities for the control unit
     *
     * @param controlUnit the control unit
     * @throws IllegalArgumentException if control unit is invalid
     */
    void refreshZoneIntensity(int controlUnit) {
        validateControlUnit(controlUnit);
        sendCommand(CMD_ZONEINTENSITYSTATUS + " " + controlUnit);
    }

    /**
     * Sets the time on the PRG interface
     *
     * @param calendar a non-null calendar to set the time to
     * @throws NullArgumentException if calendar is null
     */
    void setTime(Calendar calendar) {
        if (calendar == null) {
            throw new NullArgumentException("calendar cannot be null");
        }
        final String cmd = String.format("%1 %2$tk %2$tM %2$tm %2$te %2ty %3", CMD_SETTIME, calendar,
                calendar.get(Calendar.DAY_OF_WEEK));
        sendCommand(cmd);
    }

    /**
     * Refreshes the time from the PRG interface
     */
    void refreshTime() {
        sendCommand(CMD_READTIME);
    }

    /**
     * Selects the specific schedule (0=none, 1=weekday, 2=weekend)
     *
     * @param schedule the new schedule
     * @throws IllegalArgumentException if schedule is < 0 or > 32
     */
    void selectSchedule(int schedule) {
        if (schedule < 0 || schedule > 2) {
            throw new IllegalArgumentException("Schedule invalid (must be between 0 and 2): " + schedule);
        }
        sendCommand(CMD_SELECTSCHEDULE + " " + schedule);
    }

    /**
     * Refreshes the current schedule
     */
    void refreshSchedule() {
        sendCommand(CMD_REPORTSCHEDULE);
    }

    /**
     * Refreshs the current sunrise/sunset
     */
    void refreshSunriseSunset() {
        sendCommand(CMD_SUNRISESUNSET);
    }

    /**
     * Starts the super sequence
     */
    void startSuperSequence() {
        sendCommand(CMD_SUPERSEQUENCESTART);
        reportSuperSequenceStatus();
    }

    /**
     * Pauses the super sequence
     */
    void pauseSuperSequence() {
        sendCommand(CMD_SUPERSEQUENCEPAUSE);
    }

    /**
     * Resumes the super sequence
     */
    void resumeSuperSequence() {
        sendCommand(CMD_SUPERSEQUENCERESUME);
    }

    /**
     * Refreshes the status of the super sequence
     */
    void reportSuperSequenceStatus() {
        sendCommand(CMD_SUPERSEQUENCESTATUS);
    }

    /**
     * Sends the command and puts the thing into {@link ThingStatus#OFFLINE} if an IOException occurs
     *
     * @param command a non-null, non-empty command to send
     * @throws IllegalArgumentException if command is null or empty
     */
    private void sendCommand(String command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (command.trim().length() == 0) {
            throw new IllegalArgumentException("command cannot be empty");
        }
        try {
            logger.debug("SendCommand: " + command);
            _session.sendCommand(command);
        } catch (IOException e) {
            _callback.statusChanged(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Exception occurred sending to PRG: " + e);
        }
    }

    /**
     * Handles a command failure - we simply log the response as an error (trying to convert the error number to a
     * legible error message)
     *
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleCommandFailure(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 2) {
            try {
                final int errorNbr = Integer.parseInt(m.group(1));
                String errorMsg = "ErrorCode: " + errorNbr;
                switch (errorNbr) {
                    case 1: {
                        errorMsg = "Control Unit Raise/Lower error";
                        break;
                    }
                    case 2: {
                        errorMsg = "Invalid scene selected";
                        break;

                    }
                    case 6: {
                        errorMsg = "Bad command was sent";
                        break;

                    }
                    case 13: {
                        errorMsg = "Not a timeclock unit (GRX-ATC or GRX-PRG)";
                        break;

                    }
                    case 14: {
                        errorMsg = "Illegal time was entered";
                        break;

                    }
                    case 15: {
                        errorMsg = "Invalid schedule";
                        break;

                    }
                    case 16: {
                        errorMsg = "No Super Sequence has been loaded";
                        break;

                    }
                    case 20: {
                        errorMsg = "Command was missing Control Units";
                        break;

                    }
                    case 21: {
                        errorMsg = "Command was missing data";
                        break;

                    }
                    case 22: {
                        errorMsg = "Error in command argument (improper hex value)";
                        break;

                    }
                    case 24: {
                        errorMsg = "Invalid Control Unit";
                        break;

                    }
                    case 25: {
                        errorMsg = "Invalid value, outside range of acceptable values";
                        break;

                    }
                    case 26: {
                        errorMsg = "Invalid Accessory Control";
                        break;

                    }
                    case 31: {
                        errorMsg = "Network address illegally formatted; 4 octets required (xxx.xxx.xxx.xxx)";
                        break;

                    }
                    case 80: {
                        errorMsg = "Time-out error, no response received";
                        break;

                    }
                    case 100: {
                        errorMsg = "Invalid Telnet login number";
                        break;

                    }
                    case 101: {
                        errorMsg = "Invalid Telnet login";
                        break;

                    }
                    case 102: {
                        errorMsg = "Telnet login name exceeds 8 characters";
                        break;

                    }
                    case 103: {
                        errorMsg = "INvalid number of arguments";
                        break;

                    }
                    case 255: {
                        errorMsg = "GRX-PRG must be in programming mode for specific commands";
                        break;

                    }
                }
                logger.error("Error response: " + errorMsg + " (" + errorNbr + ")");
            } catch (NumberFormatException e) {
                logger.error("Invalid failure response (can't parse error number): '{}'", resp);
            }
        } else {
            logger.error("Invalid failure response: '{}'", resp);
        }
    }

    /**
     * Handles the scene status response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleSceneStatus(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() >= 2) {
            try {
                final String sceneStatus = m.group(1);
                for (int i = 1; i <= 8; i++) {
                    char status = sceneStatus.charAt(i - 1);
                    if (status == 'M') {
                        continue; // no control unit
                    }

                    int scene = VALID_SCENES.indexOf(status);
                    if (scene < 0) {
                        logger.warn("Unknown scene status returned for zone " + i + ": " + status);
                    } else {
                        _callback.stateChanged(i, PrgConstants.CHANNEL_SCENE, new DecimalType(scene));
                        refreshZoneIntensity(i); // request to get new zone intensities
                    }
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid scene status (can't parse scene #): '{}'", resp);
            }
        } else {
            logger.error("Invalid scene status response: '{}'", resp);
        }
    }

    /**
     * Handles the report time response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleReportTime(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 7) {
            try {
                final Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(1)));
                c.set(Calendar.MINUTE, Integer.parseInt(m.group(2)));
                c.set(Calendar.MONDAY, Integer.parseInt(m.group(3)));
                c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m.group(4)));

                final int yr = Integer.parseInt(m.group(5));
                c.set(Calendar.YEAR, yr + (yr < 50 ? 1900 : 2000));

                _callback.stateChanged(PrgConstants.CHANNEL_TIMECLOCK, new DateTimeType(c));

            } catch (NumberFormatException e) {
                logger.error("Invalid time response (can't parse number): '{}'", resp);
            }
        } else {
            logger.error("Invalid time response: '{}'", resp);
        }
    }

    /**
     * Handles the report schedule response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleReportSchedule(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 2) {
            try {
                int schedule = Integer.parseInt(m.group(1));
                _callback.stateChanged(PrgConstants.CHANNEL_SCHEDULE, new DecimalType(schedule));
            } catch (NumberFormatException e) {
                logger.error("Invalid schedule response (can't parse number): '{}'", resp);
            }
        } else {
            logger.error("Invalid schedule volume response: '{}'", resp);
        }
    }

    /**
     * Handles the sunrise/sunset response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleSunriseSunset(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 5) {
            if (m.group(1).equals("255")) {
                logger.warn("Sunrise/Sunset needs to be enabled via Liason Software");
                return;
            }
            try {
                final Calendar sunrise = Calendar.getInstance();
                sunrise.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(1)));
                sunrise.set(Calendar.MINUTE, Integer.parseInt(m.group(2)));
                _callback.stateChanged(PrgConstants.CHANNEL_SUNRISE, new DateTimeType(sunrise));

                final Calendar sunset = Calendar.getInstance();
                sunset.set(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(3)));
                sunset.set(Calendar.MINUTE, Integer.parseInt(m.group(4)));
                _callback.stateChanged(PrgConstants.CHANNEL_SUNSET, new DateTimeType(sunset));

            } catch (NumberFormatException e) {
                logger.error("Invalid sunrise/sunset response (can't parse number): '{}'", resp);
            }
        } else {
            logger.error("Invalid sunrise/sunset response: '{}'", resp);
        }
    }

    /**
     * Handles the super sequence response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleSuperSequenceStatus(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 5) {
            try {
                final int nextStep = Integer.parseInt(m.group(2));
                final int nextMin = Integer.parseInt(m.group(3));
                final int nextSec = Integer.parseInt(m.group(4));
                _callback.stateChanged(PrgConstants.CHANNEL_SUPERSEQUENCESTATUS, new StringType(m.group(1)));
                _callback.stateChanged(PrgConstants.CHANNEL_SUPERSEQUENCENEXTSTEP, new DecimalType(nextStep));
                _callback.stateChanged(PrgConstants.CHANNEL_SUPERSEQUENCENEXTMIN, new DecimalType(nextMin));
                _callback.stateChanged(PrgConstants.CHANNEL_SUPERSEQUENCENEXTSEC, new DecimalType(nextSec));
            } catch (NumberFormatException e) {
                logger.error("Invalid volume response (can't parse number): '{}'", resp);
            }
        } else {
            logger.error("Invalid format volume response: '{}'", resp);
        }
    }

    /**
     * Handles the zone intensity response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleZoneIntensity(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }

        if (m.groupCount() == 10) {
            try {
                final int controlUnit = Integer.parseInt(m.group(1));
                for (int z = 1; z <= 8; z++) {
                    final String zi = m.group(z + 1);
                    if (zi.equals("*") || zi.equals(Integer.toString(z - 1))) {
                        continue; // not present
                    }
                    final int zid = convertIntensity(controlUnit, z, zi);

                    _callback.stateChanged(controlUnit, PrgConstants.CHANNEL_ZONEINTENSITY + z, new PercentType(zid));
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid volume response (can't parse number): '{}'", resp);
            }
        } else {
            logger.error("Invalid format volume response: '{}'", resp);
        }
    }

    /**
     * Handles the controller information response (currently not used).
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleControlInfo(Matcher m, String resp) {
        if (m == null) {
            throw new IllegalArgumentException("m (matcher) cannot be null");
        }
        if (m.groupCount() == 9) {
            int controlUnit = 0;
            try {
                controlUnit = Integer.parseInt(m.group(1));

                final String q4 = m.group(8);
                final String q4bits = new StringBuffer(Integer.toBinaryString(Integer.parseInt(q4, 16))).reverse()
                        .toString();
                // final boolean seqType = (q4bits.length() > 0 ? q4bits.charAt(0) : '0') == '1';
                final boolean seqMode = (q4bits.length() > 1 ? q4bits.charAt(1) : '0') == '1';
                final boolean zoneLock = (q4bits.length() > 2 ? q4bits.charAt(2) : '0') == '1';
                final boolean sceneLock = (q4bits.length() > 3 ? q4bits.charAt(4) : '0') == '1';

                _callback.stateChanged(controlUnit, PrgConstants.CHANNEL_SCENESEQ,
                        seqMode ? OnOffType.ON : OnOffType.OFF);
                _callback.stateChanged(controlUnit, PrgConstants.CHANNEL_SCENELOCK,
                        sceneLock ? OnOffType.ON : OnOffType.OFF);
                _callback.stateChanged(controlUnit, PrgConstants.CHANNEL_ZONELOCK,
                        zoneLock ? OnOffType.ON : OnOffType.OFF);
            } catch (NumberFormatException e) {
                logger.error("Invalid controller information response: '{}'", resp);
            }
        } else {
            logger.error("Invalid controller information response: '{}'", resp);
        }
    }

    /**
     * Handles the interface being reset
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleResetting(Matcher m, String resp) {
        _callback.statusChanged(ThingStatus.OFFLINE, ThingStatusDetail.DUTY_CYCLE, "Device resetting");
    }

    /**
     * Handles the button press response
     *
     * @param m the non-null {@link Matcher} that matched the response
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleButton(Matcher m, String resp) {
        _callback.stateChanged(PrgConstants.CHANNEL_BUTTONPRESS, new StringType(resp));
    }

    /**
     * Handles an unknown response (simply logs it)
     *
     * @param resp the possibly null, possibly empty actual response
     */
    private void handleUnknownCommand(String response) {
        logger.info("Unhandled response: " + response);
    }

    /**
     * This callback is our normal response callback. Should be set into the {@link SocketSession} after the login
     * process to handle normal responses.
     *
     * @author Tim Roberts
     *
     */
    private class NormalResponseCallback implements SocketSessionCallback {

        @Override
        public void responseReceived(String response) {
            // logger.debug("Response received: " + response);

            if (response == null || response.trim().length() == 0) {
                return; // simple blank - do nothing
            }

            Matcher m = RSP_OK.matcher(response);
            if (m.matches()) {
                // logger.debug(response);
                return; // nothing to do on an OK! response
            }

            m = RSP_FAILED.matcher(response);
            if (m.matches()) {
                handleCommandFailure(m, response);
                return; // nothing really to do on an error response either
            }

            m = RSP_SCENESTATUS.matcher(response);
            if (m.matches()) {
                handleSceneStatus(m, response);
                return;
            }

            m = RSP_REPORTIME.matcher(response);
            if (m.matches()) {
                handleReportTime(m, response);
                return;
            }

            m = RSP_REPORTSCHEDULE.matcher(response);
            if (m.matches()) {
                handleReportSchedule(m, response);
                return;
            }

            m = RSP_SUNRISESUNSET.matcher(response);
            if (m.matches()) {
                handleSunriseSunset(m, response);
                return;
            }

            m = RSP_SUPERSEQUENCESTATUS.matcher(response);
            if (m.matches()) {
                handleSuperSequenceStatus(m, response);
                return;
            }

            m = RSP_ZONEINTENSITY.matcher(response);
            if (m.matches()) {
                handleZoneIntensity(m, response);
                return;
            }

            m = RSP_RMU.matcher(response);
            if (m.matches()) {
                handleControlInfo(m, response);
                return;
            }

            m = RSP_RESETTING.matcher(response);
            if (m.matches()) {
                handleResetting(m, response);
                return;
            }

            m = RSP_BUTTON.matcher(response);
            if (m.matches()) {
                handleButton(m, response);
                return;
            }

            if (RSP_CONNECTION_ESTABLISHED.equals(response)) {
                return; // nothing to do on connection established
            }

            handleUnknownCommand(response);
        }

        @Override
        public void responseException(Exception exception) {
            _callback.statusChanged(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Exception occurred reading from PRG: " + exception);
        }

    }

    /**
     * Special callback used during the login process to not dispatch the responses to this class but rather give them
     * back at each call to {@link NoDispatchingCallback#getResponse()}
     *
     * @author Tim Roberts
     *
     */
    private class NoDispatchingCallback implements SocketSessionCallback {

        /**
         * Cache of responses that have occurred
         */
        private BlockingQueue<Object> _responses = new ArrayBlockingQueue<Object>(5);

        /**
         * Will return the next response from {@link #_responses}. If the response is an exception, that exception will
         * be thrown instead.
         *
         * @return a non-null, possibly empty response
         * @throws Exception an exception if one occurred during reading
         */
        String getResponse() throws Exception {
            final Object lastResponse = _responses.poll(5, TimeUnit.SECONDS);
            if (lastResponse instanceof String) {
                return (String) lastResponse;
            } else if (lastResponse instanceof Exception) {
                throw (Exception) lastResponse;
            } else if (lastResponse == null) {
                throw new Exception("Didn't receive response in time");
            } else {
                return lastResponse.toString();
            }
        }

        @Override
        public void responseReceived(String response) {
            try {
                _responses.put(response);
            } catch (InterruptedException e) {
            }
        }

        @Override
        public void responseException(Exception e) {
            try {
                _responses.put(e);
            } catch (InterruptedException e1) {
            }

        }

    }
}
