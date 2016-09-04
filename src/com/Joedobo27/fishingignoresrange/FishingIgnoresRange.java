package com.Joedobo27.fishingignoresrange;


import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.logging.Level;
import java.util.logging.Logger;

public class FishingIgnoresRange implements WurmServerMod, ServerStartedListener{
    private static Logger logger;

    @Override
    public void onServerStarted() {
        try {
            Class actionsClass = Class.forName("com.wurmonline.server.behaviours.Actions");
            Class actionEntryClass = Class.forName("com.wurmonline.server.behaviours.ActionEntry");
            ActionEntry[] actionEntries = ReflectionUtil.getPrivateField(actionsClass,
                    ReflectionUtil.getField(actionsClass, "actionEntrys"));

            for (ActionEntry actionEntry : actionEntries) {
                Short actionNumber = ReflectionUtil.getPrivateField(actionEntries,
                        ReflectionUtil.getField(actionEntryClass,"number"));
                if (actionNumber == Actions.FISH){
                    ReflectionUtil.setPrivateField(actionEntry, ReflectionUtil.getField(actionEntryClass,"isIgnoresRange"),
                            true);
                }
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    static {
        logger = Logger.getLogger(FishingIgnoresRange.class.getName());
    }


}
