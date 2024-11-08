package com.torring.model;

import com.dampcake.bencode.Type;
import com.torring.util.TorrentConstants;
import com.torring.util.TorrentUtils;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
@SuppressWarnings("unchecked")
public class Torrent {

  private final String announceUrl;
  private final List<List<String>> announceUrlList;
  private final String comment;
  private final String createdBy;
  private final Date createdOn;
  private final byte[] infoHash;
  private final String infoHashHex;
  private final int pieceLength;
  private final byte[] pieces;
  private final List<String> hashedPiecesList;
  private final String name;
  private final long totalLength;
  private final List<FileMetadata> files;

  public Torrent(String torrentFilePath) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-1");
    byte[] torrentFile = Files.readAllBytes(Paths.get(torrentFilePath));
    Map<String, Object> metadata = TorrentConstants.bencode.decode(
      torrentFile,
      Type.DICTIONARY
    );
    Map<String, Object> info = (Map<String, Object>) metadata.get("info");
    this.announceUrl = (String) metadata.get("announce");
    this.announceUrlList =
      metadata.containsKey("announce-list")
        ? (List<List<String>>) metadata.get("announce-list")
        : null;
    this.comment =
      metadata.containsKey("comment") ? (String) metadata.get("comment") : null;
    this.createdBy =
      metadata.containsKey("created by")
        ? (String) metadata.get("created by")
        : null;
    this.createdOn =
      metadata.containsKey("creation date")
        ? Date.from(Instant.ofEpochSecond((Long) metadata.get("creation date")))
        : null;
    this.infoHash = digest.digest(TorrentConstants.bencode.encode(info));
    this.infoHashHex = TorrentUtils.bytesToHex(infoHash);
    this.pieceLength = ((Number) info.get("piece length")).intValue();
    this.pieces = TorrentUtils.getPieces(info);
    this.hashedPiecesList = TorrentUtils.getHashedPiecesList(info);
    this.name = (String) info.get("name");
    if (info.containsKey("files")) {
      this.files = new ArrayList<>();
      long totalLength = 0;
      List<Map<String, Object>> fileList = (List<Map<String, Object>>) info.get(
        "files"
      );
      for (Map<String, Object> fileInfo : fileList) {
        long length = ((Number) fileInfo.get("length")).longValue();
        List<String> path = (List<String>) fileInfo.get("path");
        files.add(new FileMetadata(path, length));
        totalLength += length;
      }
      this.totalLength = totalLength;
    } else {
      this.totalLength = ((Number) info.get("length")).longValue();
      this.files = null;
    }
  }
}
