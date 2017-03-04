package com.joedobo27.fishingignoresrange;


import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.ItemList;
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
                    logger.log(Level.FINE, poll.getCtMethod().getName() + " method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = com.joedobo27.fishingignoresrange.FishingIgnoresRange.isWithinDistanceToHook(" +
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
        final int[] fishingInfoSuccess = new int[]{0,0};

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
                    logger.log(Level.FINE, "fish method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = 1;");
                    fishWhileSwimmingSuccess[0] = 1;
                } 
                else if (Objects.equals("isOnSurface", methodCall.getMethodName()) && methodCall.getLineNumber() == 447
                        && fishInCave) {
                    // if (performer.isOnSurface()) {
                    // have this always return true to aid changes for fishing in caves.
                    logger.log(Level.FINE, "fish method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = true;");
                    fishInCaveSuccess[0] = 1;
                }
                /*
                else if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName()) && methodCall.getLineNumber() == 414 &&
                fishingInfo) {
                    logger.log(Level.FINE, "fish method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    // construct a new server message using data fetched with getFishMessage() and override the variable string
                    // passed into sendNormalServerMessage.
                    methodCall.replace("$1 = com.joedobo27.fishingignoresrange.FishingIgnoresRange.getFishMessageHook(" +
                            "com.wurmonline.server.behaviours.Fish.mesh, tilex, tiley); $_ = $proceed($$);");
                    fishingInfoSuccess[0] = 1;
                }
                */
                else if(Objects.equals("setTimeLeft", methodCall.getMethodName()) && methodCall.getLineNumber() == 421) {
                    methodCall.replace("$1 = 1; $proceed($$);");
                }
                else if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName()) && methodCall.getLineNumber() == 648 &&
                fishingInfo) {
                    logger.log(Level.FINE, "fish method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$1 = " +
                            "com.joedobo27.fishingignoresrange.FishingIgnoresRange.getTooSmallMessageHook(weight, waterType, fishOnHook); " +
                            "$_ = $proceed($$);");
                    fishingInfoSuccess[0] = 1;
                }
                else if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName()) && methodCall.getLineNumber() == 661 &&
                fishingInfo) {
                    logger.log(Level.FINE, "fish method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$1 = " +
                            "com.joedobo27.fishingignoresrange.FishingIgnoresRange.getFishCaughtHook2(waterType, fishOnHook); " +
                            "$_ = $proceed($$);");
                    fishingInfoSuccess[1] = 1;
                }
            }

            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                // all four below are to replace  // Field com/wurmonline/server/Server.surfaceMesh:Lcom/wurmonline/mesh/MeshIO;
                // with     com.wurmonline.server.behaviours.Fish.mesh;
                if (Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 456 &&
                        fishInCave) {
                    logger.log(Level.FINE, fish.getCtMethod().getName() + " method,  edit call to " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[1] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 473 &&
                        fishInCave)) {
                    logger.log(Level.FINE, fish.getCtMethod().getName() + " method,  edit call to " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[2] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 491 &&
                        fishInCave)) {
                    logger.log(Level.FINE, fish.getCtMethod().getName() + " method,  edit call to " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[3] = 1;
                } else if ((Objects.equals("surfaceMesh", fieldAccess.getFieldName()) && fieldAccess.getLineNumber() == 508 &&
                        fishInCave)) {
                    logger.log(Level.FINE, fish.getCtMethod().getName() + " method,  edit call to " +
                            fieldAccess.getFieldName() + " at index " + fieldAccess.getLineNumber());
                    fieldAccess.replace("$_ = com.wurmonline.server.behaviours.Fish.mesh;");
                    fishInCaveSuccess[4] = 1;
                }
            }
        });
        if (fishWhileSwimming)
            evaluateChangesArray(fishWhileSwimmingSuccess, "fishWhileSwimming");
        if (fishInCave)
            evaluateChangesArray(fishInCaveSuccess, "fishInCave");
        if (fishingInfo)
            evaluateChangesArray(fishingInfoSuccess, "fishingInfo");
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
        //noinspection SimplifiableIfStatement
        if (action == Actions.FISH) // 160
            return true;
        return Math.abs(performer.getStatus().getPositionX() - (aPosX + modifier)) < maxDistance && Math.abs(performer.getStatus().getPositionY() - (aPosY + modifier)) < maxDistance;
    }

    @SuppressWarnings("unused")
    public static String getTooSmallMessageHook(int weight, int waterType2, short fishOnHook){
        String waterName = getWaterName(waterType2);
        String fishName = getFishName(fishOnHook);
        return String.format("The %d gram %s from the %s is too small and escapes.", weight, fishName, waterName);
    }

    private static String getWaterName(int waterType2){
        String waterName = "pond";
        if (waterType2 >= 20000) {
            waterName = "pond";
        }
        else if (waterType2 >= 7500) {
            waterName = "lake";
        }
        else if (waterType2 >= 5000) {
            waterName = "deep sea";
        }
        return waterName;
    }

    private static String getFishName(short fishOnHook){
        String fishName = "";
        switch (fishOnHook){
            case ItemList.deadPerch :
                // 163
                fishName = "Perch";
                break;
            case ItemList.deadRoach :
                // 162
                fishName = "Roach";
                break;
            case ItemList.deadTrout :
                // 165
                fishName = "Trout";
                break;
            case ItemList.deadPike :
                // 157
                fishName = "Pike";
                break;
            case ItemList.deadCatFish :
                // 160
                fishName = "Catfish";
                break;
            case ItemList.deadHerring :
                // 159
                fishName = "Herring";
                break;
            case ItemList.deadCarp :
                // 164
                fishName = "Carp";
                break;
            case ItemList.deadBass :
                // 158
                fishName = "Bass";
                break;
            case ItemList.deadOctopus :
                // 572
                fishName = "Octopus";
                break;
            case ItemList.deadMarlin :
                // 569
                fishName = "Marlin";
                break;
            case ItemList.deadSharkBlue :
                // 570
                fishName = "Blue shark";
                break;
            case ItemList.deadDorado :
                // 574
                fishName = "Dorado";
                break;
            case ItemList.deadSailFish :
                // 573
                fishName = "Sailfish";
                break;
            case ItemList.deadSharkWhite :
                // 571
                fishName = "SharkWhite";
                break;
            case ItemList.deadTuna :
                // 575
                fishName = "Tuna";
        }
        return fishName;
    }

    @SuppressWarnings("unused")
    public static String getFishCaughtHook2(int waterType2, short fishOnHook) {
        String waterName = getWaterName(waterType2);
        String fishName = getFishName(fishOnHook);
        return String.format("You think there is a %s on the hook from the %s.", fishName, waterName);
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

    private static void evaluateChangesArray(int[] ints, String option) {
        boolean changesSuccessful = Arrays.stream(ints).noneMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, option + " option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, option + " option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }

    static {
        logger = Logger.getLogger(FishingIgnoresRange.class.getName());
        classPool = HookManager.getInstance().getClassPool();
    }
}
