package com.winlator.cmod.runtime.display.environment;

import android.content.Context;
import android.util.Log;
import com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class XEnvironment implements Iterable<EnvironmentComponent> {
  private static final String TAG = "XEnvironment";
  private final Context context;
  private final ImageFs imageFs;
  private final ArrayList<EnvironmentComponent> components = new ArrayList<>();

  public XEnvironment(Context context, ImageFs imageFs) {
    this.context = context;
    this.imageFs = imageFs;
  }

  public Context getContext() {
    return context;
  }

  public ImageFs getImageFs() {
    return imageFs;
  }

  public void addComponent(EnvironmentComponent environmentComponent) {
    environmentComponent.environment = this;
    components.add(environmentComponent);
  }

  public <T extends EnvironmentComponent> T getComponent(Class<T> componentClass) {
    for (EnvironmentComponent component : components) {
      if (component.getClass() == componentClass) return (T) component;
    }
    return null;
  }

  @Override
  public Iterator<EnvironmentComponent> iterator() {
    return components.iterator();
  }

  public File getTmpDir() {
    File tmpDir = new File(context.getFilesDir(), "tmp");
    if (!tmpDir.isDirectory()) {
      tmpDir.mkdirs();
      FileUtils.chmod(tmpDir, 0771);
    }
    return tmpDir;
  }

  public void startEnvironmentComponents() {
    FileUtils.clear(getTmpDir());
    Log.d(TAG, "Starting " + components.size() + " environment component(s)");
    for (EnvironmentComponent environmentComponent : this) {
      Log.d(TAG, "Starting component " + environmentComponent.getClass().getSimpleName());
      environmentComponent.start();
    }
    Log.d(TAG, "Environment component startup finished");
  }

  public void stopEnvironmentComponents() {
    // Stop in reverse order so dependent components (guest launcher) tear down before
    // their underlying services (audio sockets, XServer, shm).
    Log.d(TAG, "Stopping " + components.size() + " environment component(s)");
    for (int i = components.size() - 1; i >= 0; i--) {
      EnvironmentComponent component = components.get(i);
      String name = component.getClass().getSimpleName();
      try {
        Log.d(TAG, "Stopping component " + name);
        component.stop();
        Log.d(TAG, "Stopped component " + name);
      } catch (RuntimeException e) {
        Log.e(TAG, "Component stop failed for " + name, e);
        throw e;
      }
    }
    Log.d(TAG, "Environment component shutdown finished");
  }

  public void onPause() {
    GuestProgramLauncherComponent guestProgramLauncherComponent =
        getComponent(GuestProgramLauncherComponent.class);
    if (guestProgramLauncherComponent != null) guestProgramLauncherComponent.suspendProcess();
  }

  public void onResume() {
    GuestProgramLauncherComponent guestProgramLauncherComponent =
        getComponent(GuestProgramLauncherComponent.class);
    if (guestProgramLauncherComponent != null) guestProgramLauncherComponent.resumeProcess();
  }
}
