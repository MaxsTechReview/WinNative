package com.winlator.cmod.runtime.display.environment.components;

import android.util.Log;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.display.steampipeserver.SteamPipeServer;

/**
 * Environment component that runs a local SteamPipeServer. This allows games using the Goldberg
 * Steam emulator to communicate with a fake Steam client, enabling Steam API features like
 * authentication, callbacks, etc.
 */
public class SteamClientComponent extends EnvironmentComponent {
  private static final String TAG = "SteamClientComponent";
  private SteamPipeServer server;

  @Override
  public void start() {
    Log.d(TAG, "Starting SteamClientComponent...");
    if (server != null) return;
    server = new SteamPipeServer();
    server.start();
  }

  @Override
  public void stop() {
    Log.d(TAG, "Stopping SteamClientComponent...");
    if (server != null) {
      server.stop();
      server = null;
    }
  }
}
