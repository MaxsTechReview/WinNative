package com.winlator.cmod.runtime.display.environment.components;

import android.util.Log;
import androidx.annotation.Keep;
import com.winlator.cmod.runtime.display.connector.Client;
import com.winlator.cmod.runtime.display.connector.ConnectionHandler;
import com.winlator.cmod.runtime.display.connector.RequestHandler;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.connector.XConnectorEpoll;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.display.renderer.GLRenderer;
import com.winlator.cmod.runtime.display.renderer.Texture;
import com.winlator.cmod.runtime.display.xserver.Drawable;
import com.winlator.cmod.runtime.display.xserver.XServer;
import java.io.IOException;

public class VirGLRendererComponent extends EnvironmentComponent
    implements ConnectionHandler, RequestHandler {
  private final XServer xServer;
  private final UnixSocketConfig socketConfig;
  private XConnectorEpoll connector;
  private long sharedEGLContextPtr;

  static {
    System.loadLibrary("virglrenderer");
  }

  public VirGLRendererComponent(XServer xServer, UnixSocketConfig socketConfig) {
    this.xServer = xServer;
    this.socketConfig = socketConfig;
  }

  @Override
  public void start() {
    if (connector != null) return;
    connector = new XConnectorEpoll(socketConfig, this, this);
    connector.start();
  }

  @Override
  public void stop() {
    if (connector != null) {
      connector.stop();
      connector = null;
    }
  }

  @Keep
  private void killConnection(int fd) {
    connector.killConnection(connector.getClient(fd));
  }

  @Keep
  private long getSharedEGLContext() {
    if (sharedEGLContextPtr != 0) return sharedEGLContextPtr;
    final Thread thread = Thread.currentThread();
    try {
      GLRenderer renderer = xServer.getRenderer();
      renderer.xServerView.queueEvent(
          () -> {
            sharedEGLContextPtr = getCurrentEGLContextPtr();

            synchronized (thread) {
              thread.notify();
            }
          });
      synchronized (thread) {
        thread.wait();
      }
    } catch (Exception e) {
      return 0;
    }
    return sharedEGLContextPtr;
  }

  @Override
  public void handleConnectionShutdown(Client client) {
    long clientPtr = (long) client.getTag();
    destroyClient(clientPtr);
  }

  @Override
  public void handleNewConnection(Client client) {
    getSharedEGLContext();
    long clientPtr = handleNewConnection(client.clientSocket.fd);
    client.setTag(clientPtr);
  }

  @Override
  public boolean handleRequest(Client client) throws IOException {
    long clientPtr = (long) client.getTag();
    handleRequest(clientPtr);
    return true;
  }

  @Keep
  private void flushFrontbuffer(int drawableId, int framebuffer) {
    Drawable drawable = xServer.drawableManager.getDrawable(drawableId);
    if (drawable == null) {
      Log.e("VirGLRendererComponent", "Drawable not found for drawableId=" + drawableId);
      return;
    }

    synchronized (drawable.renderLock) {
      if (framebuffer == 0) {
        Log.e("VirGLRendererComponent", "Framebuffer is invalid for drawableId=" + drawableId);
        return;
      }

      Texture texture = drawable.getTexture();
      if (texture == null) {
        Log.e("VirGLRendererComponent", "Texture is null for drawableId=" + drawableId);
        return;
      }

      // Ensure existing data is valid before resetting
      try {
        texture.copyFromFramebuffer(framebuffer, drawable.width, drawable.height);
      } catch (Exception e) {
        Log.e("VirGLRendererComponent", "Error during framebuffer copy: " + e.getMessage(), e);
        return;
      }
    }

    Runnable onDrawListener = drawable.getOnDrawListener();
    if (onDrawListener != null) {
      onDrawListener.run();
    }
  }

  private native long handleNewConnection(int fd);

  private native void handleRequest(long clientPtr);

  private native long getCurrentEGLContextPtr();

  private native void destroyClient(long clientPtr);

  private native void destroyRenderer(long clientPtr);
}
