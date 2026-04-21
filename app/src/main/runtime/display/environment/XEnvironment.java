package com.winlator.cmod.runtime.display.environment;

import android.content.Context;
import com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public class XEnvironment implements Iterable<EnvironmentComponent> {
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
    for (EnvironmentComponent environmentComponent : this) environmentComponent.start();
  }

  public void stopEnvironmentComponents() {
    // Stop in reverse order so dependent components (guest launcher) tear down before
    // their underlying services (audio sockets, XServer, shm).
    for (int i = components.size() - 1; i >= 0; i--) {
      components.get(i).stop();
    }
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
