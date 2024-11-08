package com.torring.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TorrentUtils {

  public static final int LEFT_SIZE = 999;

  public static String randomString(int length) {
    String chars =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    StringBuilder sb = new StringBuilder(length);
    Random random = new Random();
    for (int i = 0; i < length; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  public static String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(String.format("%02x", b & 0xff));
    }
    return hex.toString();
  }

  public static byte[] getPieces(Map<String, Object> info) {
    Object pieces = info.get("pieces");
    if (pieces instanceof String) {
      return ((String) pieces).getBytes(StandardCharsets.ISO_8859_1);
    } else if (pieces instanceof byte[]) {
      return (byte[]) pieces;
    } else {
      throw new RuntimeException(
        "Pieces data is neither String nor byte array: " + pieces.getClass()
      );
    }
  }

  public static List<byte[]> getPiecesList(Map<String, Object> info) {
    byte[] pieces = getPieces(info);
    List<byte[]> piecesList = new ArrayList<>();
    for (int i = 0; i + 20 <= pieces.length; i += 20) {
      piecesList.add(Arrays.copyOfRange(pieces, i, i + 20));
    }
    return piecesList;
  }

  public static List<String> getHashedPiecesList(Map<String, Object> info) {
    List<byte[]> piecesList = getPiecesList(info);
    List<String> hashedPiecesList = new ArrayList<>();

    for (byte[] piece : piecesList) {
      StringBuilder hexString = new StringBuilder();
      for (byte b : piece) {
        hexString.append(String.format("%02x", b));
      }
      hashedPiecesList.add(hexString.toString());
    }

    return hashedPiecesList;
  }

  public static String buildTrackerUrl(
    String announceUrl,
    byte[] infoHash,
    long fileLength
  ) throws Exception {
    StringBuilder url = new StringBuilder(announceUrl);
    url
      .append("?info_hash=")
      .append(
        URLEncoder.encode(
          new String(infoHash, StandardCharsets.ISO_8859_1),
          StandardCharsets.ISO_8859_1
        )
      );
    url
      .append("&peer_id=")
      .append(
        URLEncoder.encode(TorrentConstants.PEER_ID, StandardCharsets.UTF_8)
      );
    url.append("&port=").append(TorrentConstants.PORT);
    url.append("&uploaded=0");
    url.append("&downloaded=0");
    url.append("&left=").append(fileLength);
    url.append("&compact=1");
    return url.toString();
  }

  public static String getFileNameWithoutExtension(String fileName) {
    int lastIndex = fileName.lastIndexOf('.');
    return (lastIndex == -1) ? fileName : fileName.substring(0, lastIndex);
  }
}
