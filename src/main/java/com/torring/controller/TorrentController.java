package com.torring.controller;

import com.torring.model.Torrent;
import com.torring.service.TorrentService;
import com.torring.util.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/torrent")
public class TorrentController {

  @Autowired
  private TorrentService torrentService;

  private static final Logger logger = LoggerFactory.getLogger(
    TorrentController.class
  );

  @PostMapping("/get-metadata")
  public ResponseEntity<Object> getTorrentMetadata(
    @RequestParam("torrentFile") MultipartFile torrentFile
  ) {
    try {
      String torrentFilePath = torrentService.saveTorrentFile(torrentFile);
      Torrent torrent = new Torrent(torrentFilePath);
      logger.info("Torrent file metadata retrieved successfully!");
      return new ResponseHandler(HttpStatus.OK, torrent);
    } catch (Exception e) {
      logger.error("Failed to retrieve torrent file metadata: " + e);
      return new ResponseHandler(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to retrieve torrent file metadata: " + e
      );
    }
  }

  @PostMapping("/download")
  public ResponseEntity<Object> downloadTorrent(
    @RequestParam("torrentFile") MultipartFile torrentFile
  ) {
    try {
      torrentService.downloadFile(torrentFile);
      logger.info("Torrent file content downloaded successfully!");
      return new ResponseHandler(
        HttpStatus.OK,
        "Torrent file content downloaded successfully!"
      );
    } catch (Exception e) {
      logger.error("Failed to download torrent file content: " + e);
      return new ResponseHandler(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to download torrent file content: " + e
      );
    }
  }
}
