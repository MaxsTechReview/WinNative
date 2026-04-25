package com.winlator.cmod.runtime.wine;

import android.content.Context;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.shared.util.ArrayUtils;
import com.winlator.cmod.shared.util.StringUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class MSLink {
  public static final byte SW_SHOWNORMAL = 1;
  public static final byte SW_SHOWMAXIMIZED = 3;
  public static final byte SW_SHOWMINNOACTIVE = 7;
  private static final int HasLinkTargetIDList = 1 << 0;
  private static final int HasArguments = 1 << 5;
  private static final int HasIconLocation = 1 << 6;
  private static final int ForceNoLinkInfo = 1 << 8;

  public static final class Options {
    public String targetPath;
    public String cmdArgs;
    public String iconLocation;
    public int iconIndex;
    public int fileSize;
    public int showCommand = SW_SHOWNORMAL;
  }

  private static int charToHexDigit(char chr) {
    return chr >= 'A' ? chr - 'A' + 10 : chr - '0';
  }

  private static byte twoCharsToByte(char chr1, char chr2) {
    return (byte)
        (charToHexDigit(Character.toUpperCase(chr1)) * 16
            + charToHexDigit(Character.toUpperCase(chr2)));
  }

  private static byte[] convertCLSIDtoDATA(String str) {
    return new byte[] {
      twoCharsToByte(str.charAt(6), str.charAt(7)),
      twoCharsToByte(str.charAt(4), str.charAt(5)),
      twoCharsToByte(str.charAt(2), str.charAt(3)),
      twoCharsToByte(str.charAt(0), str.charAt(1)),
      twoCharsToByte(str.charAt(11), str.charAt(12)),
      twoCharsToByte(str.charAt(9), str.charAt(10)),
      twoCharsToByte(str.charAt(16), str.charAt(17)),
      twoCharsToByte(str.charAt(14), str.charAt(15)),
      twoCharsToByte(str.charAt(19), str.charAt(20)),
      twoCharsToByte(str.charAt(21), str.charAt(22)),
      twoCharsToByte(str.charAt(24), str.charAt(25)),
      twoCharsToByte(str.charAt(26), str.charAt(27)),
      twoCharsToByte(str.charAt(28), str.charAt(29)),
      twoCharsToByte(str.charAt(30), str.charAt(31)),
      twoCharsToByte(str.charAt(32), str.charAt(33)),
      twoCharsToByte(str.charAt(34), str.charAt(35))
    };
  }

  private static byte[] stringToByteArray(String str) {
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) str.charAt(i);
    return bytes;
  }

  private static byte[] intToByteArray(int value) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
  }

  private static byte[] stringSizePaddedToByteArray(String str) {
    ByteBuffer buffer = ByteBuffer.allocate(str.length() + 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort((short) str.length());
    for (int i = 0; i < str.length(); i++) buffer.put((byte) str.charAt(i));
    return buffer.array();
  }

  private static byte[] generateIDLIST(byte[] bytes) {
    ByteBuffer buffer =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) (bytes.length + 2));
    return ArrayUtils.concat(buffer.array(), bytes);
  }

  public static void createFile(String targetPath, File outputFile) {
    Options options = new Options();
    options.targetPath = targetPath;
    createFile(options, outputFile);
  }

  public static void createFile(Options options, File outputFile) {
    byte[] HeaderSize = new byte[] {0x4c, 0x00, 0x00, 0x00};
    byte[] LinkCLSID = convertCLSIDtoDATA("00021401-0000-0000-c000-000000000046");

    int linkFlags = HasLinkTargetIDList | ForceNoLinkInfo;
    if (options.cmdArgs != null && !options.cmdArgs.isEmpty()) linkFlags |= HasArguments;
    if (options.iconLocation != null && !options.iconLocation.isEmpty())
      linkFlags |= HasIconLocation;

    byte[] LinkFlags = intToByteArray(linkFlags);

    byte[] FileAttributes, prefixOfTarget;
    options.targetPath = options.targetPath.replaceAll("/+", "\\\\");
    if (options.targetPath.endsWith("\\")) {
      FileAttributes = new byte[] {0x10, 0x00, 0x00, 0x00};
      prefixOfTarget =
          new byte[] {0x31, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
      options.targetPath = options.targetPath.replaceAll("\\\\+$", "");
    } else {
      FileAttributes = new byte[] {0x20, 0x00, 0x00, 0x00};
      prefixOfTarget =
          new byte[] {0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    byte[] CreationTime, AccessTime, WriteTime;
    CreationTime =
        AccessTime = WriteTime = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    byte[] FileSize = intToByteArray(options.fileSize);
    byte[] IconIndex = intToByteArray(options.iconIndex);
    byte[] ShowCommand = intToByteArray(options.showCommand);
    byte[] Hotkey = new byte[] {0x00, 0x00};
    byte[] Reserved1 = new byte[] {0x00, 0x00};
    byte[] Reserved2 = new byte[] {0x00, 0x00, 0x00, 0x00};
    byte[] Reserved3 = new byte[] {0x00, 0x00, 0x00, 0x00};

    byte[] CLSIDComputer = convertCLSIDtoDATA("20d04fe0-3aea-1069-a2d8-08002b30309d");
    byte[] CLSIDNetwork = convertCLSIDtoDATA("208d2c60-3aea-1069-a2d7-08002b30309d");

    byte[] itemData, prefixRoot, targetRoot, targetLeaf;
    if (options.targetPath.startsWith("\\")) {
      prefixRoot = new byte[] {(byte) 0xc3, 0x01, (byte) 0x81};
      targetRoot = stringToByteArray(options.targetPath);
      targetLeaf =
          !options.targetPath.endsWith("\\")
              ? stringToByteArray(
                  options.targetPath.substring(options.targetPath.lastIndexOf("\\") + 1))
              : null;
      itemData = ArrayUtils.concat(new byte[] {0x1f, 0x58}, CLSIDNetwork);
    } else {
      prefixRoot = new byte[] {0x2f};
      int index = options.targetPath.indexOf("\\");
      targetRoot = stringToByteArray(options.targetPath.substring(0, index + 1));
      targetLeaf = stringToByteArray(options.targetPath.substring(index + 1));
      itemData = ArrayUtils.concat(new byte[] {0x1f, 0x50}, CLSIDComputer);
    }

    targetRoot = ArrayUtils.concat(targetRoot, new byte[21]);

    byte[] endOfString = new byte[] {0x00};
    byte[] IDListItems =
        ArrayUtils.concat(
            generateIDLIST(itemData),
            generateIDLIST(ArrayUtils.concat(prefixRoot, targetRoot, endOfString)));
    if (targetLeaf != null)
      IDListItems =
          ArrayUtils.concat(
              IDListItems,
              generateIDLIST(ArrayUtils.concat(prefixOfTarget, targetLeaf, endOfString)));
    byte[] IDList = generateIDLIST(IDListItems);

    byte[] TerminalID = new byte[] {0x00, 0x00};

    byte[] StringData = new byte[0];
    if ((linkFlags & HasArguments) != 0)
      StringData = ArrayUtils.concat(StringData, stringSizePaddedToByteArray(options.cmdArgs));
    if ((linkFlags & HasIconLocation) != 0)
      StringData = ArrayUtils.concat(StringData, stringSizePaddedToByteArray(options.iconLocation));

    try (FileOutputStream os = new FileOutputStream(outputFile)) {
      os.write(HeaderSize);
      os.write(LinkCLSID);
      os.write(LinkFlags);
      os.write(FileAttributes);
      os.write(CreationTime);
      os.write(AccessTime);
      os.write(WriteTime);
      os.write(FileSize);
      os.write(IconIndex);
      os.write(ShowCommand);
      os.write(Hotkey);
      os.write(Reserved1);
      os.write(Reserved2);
      os.write(Reserved3);
      os.write(IDList);
      os.write(TerminalID);

      if (StringData.length > 0) os.write(StringData);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static String parseFilePath(File lnkFile) {
    String filePath = "";
    try (FileInputStream fis = new FileInputStream(lnkFile);
        DataInputStream dis = new DataInputStream(fis)) {
      int linkFlags, linkInfoStart;
      byte[] bytes = new byte[(int) lnkFile.length()];
      dis.readFully(bytes);
      ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      linkFlags = data.getInt(0x14);
      if ((linkFlags & (1 << 0)) != 0) {
        short linkInfoTargetIdListSize = data.getShort(0x4C);
        linkInfoStart = 0x4E + linkInfoTargetIdListSize;
      } else if ((linkFlags & (1 << 1)) != 0) {
        linkInfoStart = 0x4C;
      } else {
        return filePath;
      }

      int localBasePathOffset = data.getInt(linkInfoStart + 16);
      if (localBasePathOffset > 0) {
        filePath = StringUtils.fromANSIString(data, linkInfoStart + localBasePathOffset);
      }
    } catch (IOException e) {
    }

    return filePath;
  }

  public static boolean createDesktopFile(File lnkFile, Context context) {
    return createDesktopFile(lnkFile, context, null);
  }

  public static boolean createDesktopFile(File lnkFile, Context context, Container container) {
    String lnkFilePath = lnkFile.getPath();
    String windowsPath = parseFilePath(lnkFile);
    if (windowsPath == null || windowsPath.isEmpty()) return false;

    String filePath = StringUtils.escapeDOSPath(windowsPath);
    ImageFs imageFs = ImageFs.find(context);
    
    // Determine the prefix and native path for C drive games
    String winePrefix = imageFs.wineprefix;
    File exeFile = WineUtils.getNativePath(container, imageFs, windowsPath);

    if (windowsPath.matches("^[cC]:.*")) {
      File containerRootDir = container != null ? container.getRootDir() : new File(imageFs.getRootDir(), "home/" + ImageFs.USER);
      winePrefix = new File(containerRootDir, ".wine").getAbsolutePath();

      // PEACFUL UPGRADE: If getNativePath failed (common during first creation), 
      // calculate the Absolute Android Path math immediately so we have the parent folder.
      if (exeFile == null || !exeFile.exists()) {
        String relPath = windowsPath.substring(2).replace("\\", "/");
        while (relPath.startsWith("/")) relPath = relPath.substring(1);
        exeFile = new File(containerRootDir, ".wine/drive_c/" + relPath);
      }
    }

    File desktopFile =
        new File(lnkFilePath.substring(0, lnkFilePath.lastIndexOf(".")) + ".desktop");
    String name = lnkFile.getName().substring(0, lnkFile.getName().lastIndexOf("."));

    // Smart Discovery (Synchronized like main):
    String customLibraryIconPath = "";
    if (exeFile != null && exeFile.exists()) {
      String safeName =
          lnkFile
              .getName()
              .substring(0, lnkFile.getName().lastIndexOf("."))
              .replace("/", "_")
              .replace("\\", "_");
      File iconOutFile = new File(context.getFilesDir(), "custom_icons/" + safeName + ".png");

      if (PeIconExtractor.INSTANCE.extractAndSave(exeFile, iconOutFile)) {
        customLibraryIconPath = iconOutFile.getAbsolutePath();
      } else {
        File gameDir = exeFile.isDirectory() ? exeFile : exeFile.getParentFile();
        if (gameDir != null && gameDir.exists()) {
          File[] candidates =
              gameDir.listFiles(
                  (dir, name_dir) -> {
                    String lower = name_dir.toLowerCase();
                    return lower.endsWith(".jpg")
                        || lower.endsWith(".jpeg")
                        || lower.endsWith(".png");
                  });
          if (candidates != null && candidates.length > 0) {
            customLibraryIconPath = candidates[0].getAbsolutePath();
          }
        }
      }
    }

    // SILENT MERGE: If Wine created a file, read it first so we don't destroy its data.
    java.util.ArrayList<String> lines = new java.util.ArrayList<>();
    if (desktopFile.exists()) {
        for (String line : com.winlator.cmod.shared.io.FileUtils.readLines(desktopFile)) {
            if (line.contains("[Extra Data]")) break;
            lines.add(line);
        }
    } else {
        lines.add("[Desktop Entry]");
        lines.add("Name=" + name);
        lines.add("Exec=env WINEPREFIX=\"" + winePrefix + "\" wine " + filePath);
        lines.add("Type=Application");
        lines.add("Icon=custom_game");
        lines.add("StartupNotify=True");
    }

    try (FileOutputStream fos = new FileOutputStream(desktopFile);
        PrintWriter pw = new PrintWriter(fos)) {
      for (String line : lines) pw.write(line + "\n");

      // Merge our working metadata into the file
      pw.write("\n[Extra Data]\n");
      pw.write("game_source=CUSTOM\n");
      pw.write("custom_name=" + name + "\n");
      pw.write("use_container_defaults=1\n");
      if (container != null) pw.write("container_id=" + container.id + "\n");

      // Write Absolute Android Paths immediately so the launcher never says "Path not found"
      if (exeFile != null) {
        pw.write("custom_exe=" + exeFile.getAbsolutePath() + "\n");
        pw.write("launch_exe_path=" + exeFile.getAbsolutePath() + "\n");
        File parent = exeFile.getParentFile();
        if (parent != null) pw.write("custom_game_folder=" + parent.getAbsolutePath() + "\n");
      } else {
        pw.write("custom_exe=" + windowsPath + "\n");
        pw.write("launch_exe_path=" + windowsPath + "\n");
      }
      
      if (!customLibraryIconPath.isEmpty()) {
        pw.write("customCoverArtPath=" + customLibraryIconPath + "\n");
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
