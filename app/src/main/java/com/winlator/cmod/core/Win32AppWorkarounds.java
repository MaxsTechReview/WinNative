package com.winlator.cmod.core;

import android.util.Log;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.Window;

import androidx.collection.ArrayMap;

import java.util.Locale;

public class Win32AppWorkarounds {

    private final XServerDisplayActivity activity;
    private short taskAffinityMask;
    private short taskAffinityMaskWoW64;
    private ArrayMap<String, Workaround> workarounds;

    public Win32AppWorkarounds(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    public void setTaskAffinityMask(short taskAffinityMask) {
        this.taskAffinityMask = taskAffinityMask;
    }

    public void setTaskAffinityMaskWoW64(short taskAffinityMaskWoW64) {
        this.taskAffinityMaskWoW64 = taskAffinityMaskWoW64;
    }

    private void initWorkarounds() {
        if (this.workarounds != null) {
            Log.d("Win32AppWorkarounds", "Workarounds already initialized.");
            return;
        }

        workarounds = new ArrayMap<>();

        Log.d("Win32AppWorkarounds", "Initializing workarounds...");

        workarounds.put("dxmd.exe", new MultiWorkaround(
                new TaskAffinityWorkaround(taskAffinityMask),
                new DXWrapperWorkaround("dxvk"),
                new EnvVarsWorkaround("WINEVMEMMAXSIZE", "16384"),
                new EnvVarsWorkaround("WINEOVERRIDEAFFINITYMASK", Short.toString(taskAffinityMask)),
                new DelayedTaskAffinityWorkaround(taskAffinityMask)
        ));

        Log.d("Win32AppWorkarounds", "Workarounds initialized.");
    }



    public void applyStartupWorkarounds(String className) {
        Log.d("Win32AppWorkarounds", "applyStartupWorkarounds called with className: " + className);

        initWorkarounds();
        Workaround workaround = workarounds.get(className.toLowerCase(Locale.ENGLISH));

        if (workaround != null) {
            Log.d("Win32AppWorkarounds", "Found workaround for className: " + className);

            if (workaround instanceof MultiWorkaround) {
                for (Workaround subWorkaround : ((MultiWorkaround) workaround).getWorkarounds()) {
                    applyWorkaround(subWorkaround);
                }
            } else {
                applyWorkaround(workaround);
            }
        } else {
            Log.w("Win32AppWorkarounds", "No workaround found for className: " + className);
        }
    }


    public void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0 || !window.isApplicationWindow()) return;

        initWorkarounds();
        int affinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        String className = window.getClassName().toLowerCase(Locale.ENGLISH);
        Workaround workaround = workarounds.get(className);

        if (workaround instanceof TaskAffinityWorkaround) {
            affinity = ((TaskAffinityWorkaround) workaround).getAffinityMask(window);
        } else if (workaround instanceof MultiWorkaround) {
            for (Workaround subWorkaround : ((MultiWorkaround) workaround).getWorkarounds()) {
                if (subWorkaround instanceof TaskAffinityWorkaround) {
                    affinity = ((TaskAffinityWorkaround) subWorkaround).getAffinityMask(window);
                } else if (subWorkaround instanceof DelayedTaskAffinityWorkaround) {
                    ((DelayedTaskAffinityWorkaround) subWorkaround).apply(activity, window);
                }
            }
        }

        setProcessAffinity(window, affinity);
    }


    private void applyWorkaround(Workaround workaround) {
        if (workaround instanceof EnvVarsWorkaround) {
            EnvVarsWorkaround envWorkaround = (EnvVarsWorkaround) workaround;
            envWorkaround.apply(activity.getOverrideEnvVars());
            Log.d("Win32AppWorkarounds", "Applied EnvVarsWorkaround: " + envWorkaround.key + " = " + envWorkaround.value);
        } else if (workaround instanceof DXWrapperWorkaround) {
            DXWrapperWorkaround dxWorkaround = (DXWrapperWorkaround) workaround;
            activity.setDXWrapper(dxWorkaround.getValue());
            Log.d("Win32AppWorkarounds", "Applied DXWrapperWorkaround with value: " + dxWorkaround.getValue());
        } else {
            Log.d("Win32AppWorkarounds", "Applied generic workaround: " + workaround.getClass().getSimpleName());
        }
    }



    private void setProcessAffinity(Window window, int processAffinity) {
        WinHandler winHandler = activity.getWinHandler();
        int processId = window.getProcessId();

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        } else if (!window.getClassName().isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    // Base class for all workarounds
    private abstract static class Workaround {}

    private static class TaskAffinityWorkaround extends Workaround {
        private final int affinityMask;

        public TaskAffinityWorkaround(int affinityMask) {
            this.affinityMask = affinityMask;
        }

        public int getAffinityMask(Window window) {
            return affinityMask;
        }
    }

    private static class DelayedTaskAffinityWorkaround extends Workaround {
        private final int affinityMask;

        public DelayedTaskAffinityWorkaround(int affinityMask) {
            this.affinityMask = affinityMask;
        }

        public void apply(XServerDisplayActivity activity, Window window) {
            AppUtils.runDelayed(() -> {
                WinHandler winHandler = activity.getWinHandler();
                int processId = window.getProcessId();

                if (processId > 0) {
                    winHandler.setProcessAffinity(processId, affinityMask);
                }
            }, 40000); // 40 seconds delay
        }

    }

    private static class DXWrapperWorkaround extends Workaround {
        private final String value;

        public DXWrapperWorkaround(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static class EnvVarsWorkaround extends Workaround {
        private final String key;
        private final String value;

        public EnvVarsWorkaround(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public void apply(EnvVars envVars) {
            envVars.put(key, value);
        }
    }

    private static class MultiWorkaround extends Workaround {
        private final Workaround[] workarounds;

        public MultiWorkaround(Workaround... workarounds) {
            this.workarounds = workarounds;
        }

        public Workaround[] getWorkarounds() {
            return workarounds;
        }
    }
}
