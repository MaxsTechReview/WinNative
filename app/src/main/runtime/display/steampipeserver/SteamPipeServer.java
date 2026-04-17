package com.winlator.cmod.runtime.display.steampipeserver;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SteamPipeServer {
  private static final String TAG = "SteamPipeServer";
  private static final int PORT = 34865;

  // Gate per-message logging. Flipping this on produces one log line per Steam
  // API callback, which is noisy enough to skew frame timing under logcat load.
  private static final boolean VERBOSE = false;

  private ServerSocket serverSocket;
  private volatile boolean running;

  private int readNetworkInt(DataInputStream input) throws IOException {
    return Integer.reverseBytes(input.readInt());
  }

  private void writeNetworkInt(DataOutputStream output, int value) throws IOException {
    output.writeInt(Integer.reverseBytes(value));
  }

  public void start() {
    running = true;
    new Thread(
            () -> {
              try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(PORT));
                Log.d(TAG, "Server started on port " + PORT);

                while (running) {
                  try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                  } catch (IOException e) {
                    if (running) {
                      Log.e(TAG, "Error accepting client connection", e);
                    } else {
                      Log.d(TAG, "Server socket closed during shutdown");
                    }
                    break;
                  }
                }
              } catch (IOException e) {
                Log.e(TAG, "Server error", e);
              } finally {
                Log.d(TAG, "Server thread exiting");
              }
            },
            "SteamPipeServer")
        .start();
  }

  private void handleClient(Socket clientSocket) {
    new Thread(
            () -> {
              try {
                DataInputStream input =
                    new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                DataOutputStream output =
                    new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));

                // Block on readInt(). When the peer closes, EOFException ends the loop
                // without burning CPU and without adding per-message latency.
                while (running && !clientSocket.isClosed()) {
                  int messageType;
                  try {
                    messageType = readNetworkInt(input);
                  } catch (EOFException eof) {
                    break;
                  }

                  switch (messageType) {
                    case RequestCodes.MSG_INIT:
                      if (VERBOSE) Log.d(TAG, "MSG_INIT");
                      writeNetworkInt(output, 1);
                      output.flush();
                      break;
                    case RequestCodes.MSG_SHUTDOWN:
                      if (VERBOSE) Log.d(TAG, "MSG_SHUTDOWN");
                      clientSocket.close();
                      break;
                    case RequestCodes.MSG_RESTART_APP:
                      if (VERBOSE) Log.d(TAG, "MSG_RESTART_APP");
                      input.readInt(); // appId, not used
                      writeNetworkInt(output, 0);
                      output.flush();
                      break;
                    case RequestCodes.MSG_IS_RUNNING:
                      if (VERBOSE) Log.d(TAG, "MSG_IS_RUNNING");
                      writeNetworkInt(output, 1);
                      output.flush();
                      break;
                    case RequestCodes.MSG_REGISTER_CALLBACK:
                    case RequestCodes.MSG_UNREGISTER_CALLBACK:
                    case RequestCodes.MSG_RUN_CALLBACKS:
                      if (VERBOSE) Log.d(TAG, "callback msg " + messageType);
                      break;
                    default:
                      Log.w(TAG, "Unknown message type: " + messageType);
                      break;
                  }
                }
              } catch (IOException e) {
                if (running) Log.e(TAG, "Client handler error", e);
              } finally {
                try {
                  clientSocket.close();
                } catch (IOException e) {
                  Log.e(TAG, "Error closing client socket", e);
                }
                if (VERBOSE) Log.d(TAG, "Client thread exiting");
              }
            },
            "SteamPipeClient")
        .start();
  }

  public void stop() {
    running = false;
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error stopping server", e);
    }
  }
}
