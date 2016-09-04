package com.Joedobo27.fishingignoresrange;


import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FishingIgnoresRange implements WurmServerMod, Initable, ServerStartedListener{
    private static Logger logger;
    private static ClassPool pool;

    @Override
    public void init() {
        final boolean[] fishingProximitySuccess = {false};
        try {
            JAssistClassData actionClass = new JAssistClassData("com.wurmonline.server.behaviours.Action", pool);
            JAssistMethodData poll = new JAssistMethodData(actionClass, "()Z", "poll");
            poll.getCtMethod().instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("isWithinDistanceTo", methodCall.getMethodName())){
                            methodCall.replace("$_ = " +
                                    "com.Joedobo27.fishingignoresrange.FishingIgnoresRange.isWithinDistanceToHook(" +
                                    "this.action, this.performer, this.posX, this.posY, this.posZ, 12.0f, 2.0f);");
                        fishingProximitySuccess[0] = true;
                    }
                }
            });

        }catch (NotFoundException | CannotCompileException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        if (fishingProximitySuccess[0])
            logger.log(Level.INFO, "Disabling fishing movement proximity requirements SUCCESS");
        else {
            logger.log(Level.INFO, "Disabling fishing movement proximity requirements FAILURE");
        }
    }

    @Override
    public void onServerStarted() {
        boolean fishingProximitySuccess = false;
        try {
            Class actionsClass = Class.forName("com.wurmonline.server.behaviours.Actions");
            Class actionEntryClass = Class.forName("com.wurmonline.server.behaviours.ActionEntry");
            ActionEntry[] actionEntries = ReflectionUtil.getPrivateField(actionsClass,
                    ReflectionUtil.getField(actionsClass, "actionEntrys"));

            for (ActionEntry actionEntry : actionEntries) {
                Short actionNumber = ReflectionUtil.getPrivateField(actionEntry,
                        ReflectionUtil.getField(actionEntryClass,"number"));
                if (actionNumber == Actions.FISH){
                    ReflectionUtil.setPrivateField(actionEntry, ReflectionUtil.getField(actionEntryClass,"ignoresRange"),
                            true);
                    fishingProximitySuccess = true;
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }

        if (fishingProximitySuccess) {
            logger.log(Level.INFO, "Disabling fishing targeting proximity requirements SUCCESS");
        } else {
            logger.log(Level.INFO, "Disabling fishing targeting proximity requirements FAILURE");
        }
    }

    @SuppressWarnings("unused")
    public static boolean isWithinDistanceToHook(short action, Creature performer, float aPosX, float aPosY, float aPosZ, float maxDistance, float modifier){
        if (action == 160)
            return true;
        return Math.abs(performer.getStatus().getPositionX() - (aPosX + modifier)) < maxDistance && Math.abs(performer.getStatus().getPositionY() - (aPosY + modifier)) < maxDistance;
    }

    static {
        logger = Logger.getLogger(FishingIgnoresRange.class.getName());
        pool = HookManager.getInstance().getClassPool();
    }
}
