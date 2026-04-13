package com.winlator.cmod.runtime.display.environment;

public abstract class EnvironmentComponent {
  protected XEnvironment environment;

  public abstract void start();

  public abstract void stop();
}
