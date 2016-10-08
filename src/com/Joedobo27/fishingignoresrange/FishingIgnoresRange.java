package com.Joedobo27.fishingignoresrange;


import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.zones.Zones;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FishingIgnoresRange implements WurmServerMod, Configurable, Initable, ServerStartedListener {
    private static Logger logger;
    private static ClassPool classPool;

    private boolean fishingProximity = false;
    private boolean fishWhileSwimming = false;
    private boolean fishInCave = false;
    private boolean fishingInfo = false;

    @Override
    public void configure(Properties properties) {
        fishingProximity = Boolean.parseBoolean(properties.getProperty("fishingProximity", Boolean.toString(fishingProximity)));
        fishWhileSwimming = Boolean.parseBoolean(properties.getProperty("fishWhileSwimming", Boolean.toString(fishWhileSwimming)));
        fishInCave = Boolean.parseBoolean(properties.getProperty("fishInCave", Boolean.toString(fishInCave)));
        fishingInfo = Boolean.parseBoolean(properties.getProperty("fishingInfo", Boolean.toString(fishingInfo)));
    }

    @Override
    public void init() {
        try {
            fishMethodBytecode();
            fishingProximityBytecode();
        } catch (CannotCompileException | NotFoundException e) {
            logger.log(Level.WARNING, e.getMessage(), e.getCause());
        }
    }

    @Override
    public void onServerStarted() {
        if (!fishingProximity)
            return;
        boolean fishingProximitySuccess = false;
        try {
            Class actionsClass = Class.forName("com.wurmonline.server.behaviours.Actions");
            Class actionEntryClass = Class.forName("com.wurmonline.server.behaviours.ActionEntry");
            ActionEntry[] actionEntries = ReflectionUtil.getPrivateField(actionsClass,
                    ReflectionUtil.getField(actionsClass, "actionEntrys"));

            for (ActionEntry actionEntry : actionEntries) {
                Short actionNumber = ReflectionUtil.getPrivateField(actionEntry,
                        ReflectionUtil.getField(actionEntryClass, "number"));
                if (actionNumber == Actions.FISH) {
                    // This lets us target and fish on any targeted tile.
                    ReflectionUtil.setPrivateField(actionEntry, ReflectionUtil.getField(actionEntryClass, "ignoresRange"),
                            true);
                    // Fishing default: ACTION_TYPE_BLOCKED_ALL_BUT_OPEN = 33 and blockType 5.
                    // Change it to: ACTION_TYPE_BLOCKED_NONE = 29 or BlockType 0.
                    ReflectionUtil.setPrivateField(actionEntry, ReflectionUtil.getField(actionEntryClass, "blockType"), 0);
                    fishingProximitySuccess = true;
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

        if (fishingProximitySuccess) {
            logger.log(Level.INFO, "Disabling fishing targeting proximity requirements SUCCESS");
        } else {
            logger.log(Level.INFO, "Disabling fishing targeting proximity requirements FAILURE");
        }
    }

    private void fishingProximityBytecode() throws NotFoundException, CannotCompileException {
        if (!fishingProximity)
            return;
        final boolean[] fishingProximitySuccess = {false};
        JAssistClassData actionClass = new JAssistClassData("com.wurmonline.server.behaviours.Action", classPool);
        JAssistMethodData poll = new JAssistMethodData(actionClass, "()Z", "poll");
        poll.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isWithinDistanceTo", methodCall.getMethodName())) {
                    methodCall.replace("$_ = com.Joedobo27.fishingignoresrange.FishingIgnoresRange.isWithinDistanceToHook(" +
                            "this.action, this.performer, this.posX, this.posY, this.posZ, 12.0f, 2.0f);");
                    fishingProximitySuccess[0] = true;
                }
            }
        });
        if (fishingProximitySuccess[0])
            logger.log(Level.INFO, "Disabling fishing movement proximity requirements SUCCESS");
        else {
            logger.log(Level.INFO, "Disabling fishing movement proximity requirements FAILURE");
        }
    }

    private void fishMethodBytecode() throws CannotCompileException, NotFoundException {
        if (!fishWhileSwimming && !fishInCave && !fishingInfo)
            return;
        final int[] fishWhileSwimmingSuccess = new int[]{0};
        final int[] fishInCaveSuccess = new int[]{0,0,0,0,0};
        final int[] fishingInfoSuccess = new int[]{0};

        JAssistClassData fishClass = new JAssistClassData("com.wurmonline.server.behaviours.Fish", classPool);
        JAssistMethodData fish = new JAssistMethodData(fishClass,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIIFLcom/wurmonline/server/behaviours/Action;)Z",
                "fish");

        fish.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getBridgeId", methodCall.getMethodName()) && fishWhileSwimming) {
                    // if (performer.getBridgeId() == -10L && ph < -10 && performer.getVehicle() == -10L) {
                    // change getBridgeId() so it always returns 1 and thus disables can't swim and fish.
                    methodCall.replace("$_ = 1;");
                    fishWhileSwimmingSuccess[0] = 1;
                } else if (Objects.equals("isOnSurface", methodCall.getMethodName()) && methodCall.getLineNumber() == 454
                        && fishInCave) {
                    // if (performer.isOnSurface()) {
                    // have this always return true to aid changes for fishing in caves.
                    methodCall.replace("$_ = true;");
                    fishInCaveSuccess[0] = 1;
                } else if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName()) && methodCall.getLineNumber() == 421 &&
                fishingInfo) {
                    // construct a new server message using data fetched with getFishMessage() and override the variable string
                    // passed into sendNormalServerMessage.
                    methodCall.replace("$1 = com.Joedobo27.fishingignoresrange.FishingIgnoresRange.getFishMessageHook(" +
                            "com.wurmonline.server.behaviours.Fish.mesh, tilex, tiley); $_ = $proceed($$);");
                    fishingInfoSuccess[0] = 1;
                }
            }

            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 463 &&
                        fishInCave) {
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[1] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 480 &&
                        fishInCave)) {
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[2] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 498 &&
                        fishInCave)) {
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[3] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 515 &&
                        fishInCave)) {
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[4] = 1;
                }
            }
        });
        boolean changesSuccessful;
        if (fishWhileSwimming) {
            changesSuccessful = !Arrays.stream(fishWhileSwimmingSuccess).anyMatch(value -> value == 0);
            if (changesSuccessful) {
                logger.log(Level.INFO, "fishWhileSwimming option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "fishWhileSwimming option changes FAILURE");
                logger.log(Level.FINE, Arrays.toString(fishWhileSwimmingSuccess));
            }
        }
        if (fishInCave) {
            changesSuccessful = !Arrays.stream(fishInCaveSuccess).anyMatch(value -> value == 0);
            if (changesSuccessful) {
                logger.log(Level.INFO, "fishInCave option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "fishInCave option changes FAILURE");
                logger.log(Level.FINE, Arrays.toString(fishInCaveSuccess));
            }
        }
        if (fishingInfo) {
            changesSuccessful = !Arrays.stream(fishingInfoSuccess).anyMatch(value -> value == 0);
            if (changesSuccessful) {
                logger.log(Level.INFO, "fishingInfo option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "fishingInfo option changes FAILURE");
                logger.log(Level.FINE, Arrays.toString(fishingInfoSuccess));
            }
        }
    }

    /**
     * Reflective wrapper of WU package local method actionEntry.isIgnoresRange() in class ActionEntry.
     *
     * @param actionEntry ActionEntry WU object type.
     * @return boolean type.
     */
    @SuppressWarnings("unused")
    private static boolean isIgnoresRange(ActionEntry actionEntry) {
        try {
            Method isIgnoresRange = ReflectionUtil.getMethod(Class.forName("com.wurmonline.server.behaviours.ActionEntry"), "isIgnoresRange");
            isIgnoresRange.setAccessible(true);
            return (boolean) isIgnoresRange.invoke(actionEntry);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.log(Level.WARNING, e.getMessage(), e.getCause());
        }
        return false;
    }

    /**
     * This is a injected hook method whose returned value replaces what would be returned by WU isWithinDistanceTo() in Action class.
     * It is used in JA ExprEditor code.
     *
     * @param action int type.
     * @param performer   Creature WU object type.
     * @param aPosX       float type.
     * @param aPosY       float type.
     * @param aPosZ       float type.
     * @param maxDistance float type.
     * @param modifier    float type.
     * @return boolean type.
     */
    @SuppressWarnings("unused")
    public static boolean isWithinDistanceToHook(int action, Creature performer, float aPosX, float aPosY, float aPosZ, float maxDistance, float modifier) {
        if (action == 160)
            return true;
        return Math.abs(performer.getStatus().getPositionX() - (aPosX + modifier)) < maxDistance && Math.abs(performer.getStatus().getPositionY() - (aPosY + modifier)) < maxDistance;
    }

    /**
     * This is an injected hook method which changes the string passed into the WU sendNormalServerMessage(). It is designed to add
     * water type and depth info to the message for when a toon starts fishing. MeshIO and tilex/y are needed to get this new data as it's
     * not available in WU until after a fish is on the hook.
     *
     * @param mesh WU MeshIO type object
     * @param tileX int type object
     * @param tileY int type object
     * @return String type Object
     */
    @SuppressWarnings("unused")
    public static String getFishMessageHook(MeshIO mesh, int tileX, int tileY) {
        int y = 1;
        int maxDepth = 0;
        int waterFound = 0;
        String waterType = "";
        while (y++ < 10) {
            if (tileY - y > 0) {
                final int t = mesh.getTile(tileX, tileY - y);
                final short h = Tiles.decodeHeight(t);
                if (h > -3) {
                    continue;
                }
                if (h < maxDepth) {
                    maxDepth = h;
                }
                ++waterFound;
            } else {
                ++waterFound;
            }
        }
        y = 1;
        while (y++ < 10) {
            if (tileY + y < Zones.worldTileSizeY) {
                final int t = mesh.getTile(tileX, tileY + y);
                final short h = Tiles.decodeHeight(t);
                if (h > -3) {
                    continue;
                }
                if (h < maxDepth) {
                    maxDepth = h;
                }
                ++waterFound;
            } else {
                ++waterFound;
            }
        }
        int x = 1;
        while (x++ < 10) {
            if (tileX + x > Zones.worldTileSizeX) {
                final int t = mesh.getTile(tileX + x, tileY);
                final short h = Tiles.decodeHeight(t);
                if (h > -3) {
                    continue;
                }
                if (h < maxDepth) {
                    maxDepth = h;
                }
                ++waterFound;
            } else {
                ++waterFound;
            }
        }
        x = 1;
        while (x++ < 10) {
            if (tileX - x > 0) {
                final int t = mesh.getTile(tileX - x, tileY);
                final short h = Tiles.decodeHeight(t);
                if (h > -3) {
                    continue;
                }
                if (h < maxDepth) {
                    maxDepth = h;
                }
                ++waterFound;
            } else {
                ++waterFound;
            }
        }
        if (waterFound > 1) {
            if (waterFound < 9) {
                waterType = "pond"; // 20000 in WU code.
            } else if (waterFound > 20 && maxDepth < -250) {
                waterType = "deep sea"; // 5000 in WU code.
            } else {
                waterType = "lake"; // 7500 in WU code.
            }
        }
        return String.format("You throw out the line and start fishing in the %s. It's at most %d deep near here.", waterType, maxDepth);
    }


    static {
        logger = Logger.getLogger(FishingIgnoresRange.class.getName());
        classPool = HookManager.getInstance().getClassPool();
    }
}
