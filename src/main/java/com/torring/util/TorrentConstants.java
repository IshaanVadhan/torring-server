package com.torring.util;

import com.dampcake.bencode.Bencode;
import java.nio.charset.StandardCharsets;

public class TorrentConstants {

  public static final Bencode bencode = new Bencode(
    StandardCharsets.ISO_8859_1
  );
  public static final String PEER_ID =
    "-MY0001-" + TorrentUtils.randomString(12);
  public static final int PORT = 6881;
  public static final int HANDSHAKE_LENGTH = 68;
  // public static final int CONNECTION_TIMEOUT = 10000;
  public static final String DOWNLOAD_PIECE_DIR_PATH =
    "D:/torringDownloads/temp/";
  public static final String DOWNLOAD_FILE_DIR_PATH = "D:/torringDownloads/";
  public static final String TORRENT_FILE_DIR_PATH =
    "D:/torringDownloads/torrent-files";
  // public static final int MAX_RETRIES = 3;
  public static final int NUM_WORKERS = 5;
  public static final int TIMEOUT_SECONDS = 30;
  public static final int BLOCK_SIZE = 16 * 1024;
  public static final int MESSAGE_LENGTH_SIZE = 4;
  public static final int MESSAGE_ID_SIZE = 1;
  public static final byte BITFIELD_ID = 5;
  public static final byte INTERESTED_ID = 2;
  public static final byte UNCHOKE_ID = 1;
  public static final byte REQUEST_ID = 6;
  public static final byte PIECE_ID = 7;
}
