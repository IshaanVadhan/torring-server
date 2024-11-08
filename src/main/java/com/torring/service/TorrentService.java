package com.torring.service;

import com.dampcake.bencode.Type;
import com.torring.model.PeerMessage;
import com.torring.model.Torrent;
import com.torring.util.TorrentConstants;
import com.torring.util.TorrentUtils;
import com.torring.util.TorrentWebSocketHandler;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TorrentService {

  @Autowired
  private TorrentWebSocketHandler torrentWebSocketHandler;

  private static final Logger logger = LoggerFactory.getLogger(
    TorrentService.class
  );

  public String saveTorrentFile(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("File cannot be null or empty");
    }
    String originalFileName = file.getOriginalFilename();
    if (originalFileName == null || originalFileName.contains("..")) {
      throw new IllegalArgumentException("Invalid file name");
    }
    if (!originalFileName.endsWith(".torrent")) {
      throw new IllegalArgumentException("Only .torrent files are allowed");
    }

    Path dirPath = Paths
      .get(TorrentConstants.TORRENT_FILE_DIR_PATH)
      .toAbsolutePath()
      .normalize();
    if (!Files.exists(dirPath)) {
      Files.createDirectories(dirPath);
    }

    Path torrentFilePath = dirPath.resolve(originalFileName);
    file.transferTo(torrentFilePath.toFile());
    return torrentFilePath.toString();
  }

  public void downloadFile(MultipartFile torrentFile) throws Exception {
    String torrentFilePath = saveTorrentFile(torrentFile);
    Torrent torrent = new Torrent(torrentFilePath);
    List<String> peers = getPeersList(torrent);
    if (peers.isEmpty()) {
      throw new RuntimeException("No peers available");
    }
    long fileLength = torrent.getTotalLength();
    int pieceLength = torrent.getPieceLength();
    byte[] pieces = torrent.getPieces();
    int numPieces = (int) Math.ceil((double) fileLength / pieceLength);
    Queue<Integer> workQueue = new ConcurrentLinkedQueue<>();
    for (int i = 0; i < numPieces; i++) {
      workQueue.offer(i);
    }
    Set<Integer> completedPieces = ConcurrentHashMap.newKeySet();
    AtomicInteger completedCount = new AtomicInteger(0);
    ConcurrentMap<Integer, byte[]> pieceBuffer = new ConcurrentHashMap<>();
    ExecutorService executorService = Executors.newFixedThreadPool(
      TorrentConstants.NUM_WORKERS
    );
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < TorrentConstants.NUM_WORKERS; i++) {
      final String peer = peers.get(i % peers.size());
      Future<?> future = executorService.submit(() ->
        downloadWorker(
          torrent,
          peer,
          workQueue,
          completedPieces,
          completedCount,
          pieceBuffer,
          numPieces,
          pieces
        )
      );
      futures.add(future);
    }
    try {
      long startTime = System.currentTimeMillis();
      while (
        completedCount.get() < numPieces &&
        System.currentTimeMillis() -
        startTime <
        TorrentConstants.TIMEOUT_SECONDS *
        1000
      ) {
        Thread.sleep(100);
      }
      if (completedCount.get() < numPieces) {
        throw new TimeoutException(
          "Download timed out after " +
          TorrentConstants.TIMEOUT_SECONDS +
          " seconds"
        );
      }
      try (
        RandomAccessFile file = new RandomAccessFile(
          TorrentConstants.DOWNLOAD_FILE_DIR_PATH + torrent.getName(),
          "rw"
        )
      ) {
        file.setLength(fileLength);
        for (int i = 0; i < numPieces; i++) {
          byte[] piece = pieceBuffer.get(i);
          if (piece == null) {
            throw new RuntimeException("Missing piece " + i);
          }
          long offset = (long) i * pieceLength;
          file.seek(offset);
          file.write(piece);
        }
      }
      // logger.info("Download completed successfully!");
    } finally {
      executorService.shutdownNow();
      for (Future<?> future : futures) {
        try {
          future.cancel(true);
        } catch (Exception e) {
          // Ignore cancellation exceptions
        }
      }
    }
  }

  public static List<String> getPeersList(Torrent torrent) throws Exception {
    List<String> peers = new ArrayList<>();
    String url = TorrentUtils.buildTrackerUrl(
      torrent.getAnnounceUrl(),
      torrent.getInfoHash(),
      torrent.getTotalLength()
    );
    HttpURLConnection conn = (HttpURLConnection) new URI(url)
      .toURL()
      .openConnection();
    conn.setRequestMethod("GET");
    try (InputStream is = conn.getInputStream()) {
      byte[] response = is.readAllBytes();
      Map<String, Object> trackerResponse = TorrentConstants.bencode.decode(
        response,
        Type.DICTIONARY
      );
      byte[] peersData;
      Object peersObj = trackerResponse.get("peers");
      if (peersObj instanceof String) {
        peersData = ((String) peersObj).getBytes(StandardCharsets.ISO_8859_1);
      } else if (peersObj instanceof byte[]) {
        peersData = (byte[]) peersObj;
      } else {
        throw new RuntimeException(
          "Unexpected peers data type: " + peersObj.getClass()
        );
      }
      for (int i = 0; i < peersData.length; i += 6) {
        if (i + 6 <= peersData.length) {
          String ip = String.format(
            "%d.%d.%d.%d",
            peersData[i] & 0xFF,
            peersData[i + 1] & 0xFF,
            peersData[i + 2] & 0xFF,
            peersData[i + 3] & 0xFF
          );
          int port = (peersData[i + 4] & 0xFF) << 8 | (peersData[i + 5] & 0xFF);
          peers.add(ip + ":" + port);
        }
      }
    }
    return peers;
  }

  private void downloadWorker(
    Torrent torrent,
    String peer,
    Queue<Integer> workQueue,
    Set<Integer> completedPieces,
    AtomicInteger completedCount,
    ConcurrentMap<Integer, byte[]> pieceBuffer,
    int numPieces,
    byte[] pieces
  ) {
    while (!Thread.currentThread().isInterrupted()) {
      Integer pieceIndex = workQueue.poll();
      if (pieceIndex == null) {
        break;
      }
      if (completedPieces.contains(pieceIndex)) {
        continue;
      }
      try {
        byte[] piece = downloadPiece(torrent, pieceIndex);
        byte[] expectedHash = Arrays.copyOfRange(
          pieces,
          pieceIndex * 20,
          (pieceIndex + 1) * 20
        );
        byte[] actualHash = MessageDigest.getInstance("SHA-1").digest(piece);
        if (Arrays.equals(expectedHash, actualHash)) {
          pieceBuffer.put(pieceIndex, piece);
          completedPieces.add(pieceIndex);
          int completed = completedCount.incrementAndGet();
          String percentage = String.format(
            "%.1f",
            (completed * 100.0) / numPieces
          );
          torrentWebSocketHandler.sendMessageToAll(
            "Downloaded " +
            completed +
            "/" +
            numPieces +
            " pieces of " +
            torrent.getName()
          );
          logger.info(
            "Downloaded piece {}/{} ({}%) from {}",
            completed,
            numPieces,
            percentage,
            peer
          );
        } else {
          workQueue.offer(pieceIndex);
        }
      } catch (Exception e) {
        System.err.printf(
          "Failed to download piece %d from peer %s: %s\n",
          pieceIndex,
          peer,
          e.getMessage()
        );
        workQueue.offer(pieceIndex);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  public static byte[] downloadPiece(Torrent torrent, int pieceIndex)
    throws Exception {
    List<String> peers = getPeersList(torrent);
    if (peers.isEmpty()) {
      throw new RuntimeException("No peers available");
    }
    byte[] downloadedPiece = null;
    for (String peer : peers) {
      String[] peerAddress = peer.split(":");
      String peerHost = peerAddress[0];
      int peerPort = Integer.parseInt(peerAddress[1]);
      try (Socket socket = new Socket(peerHost, peerPort)) {
        DataOutputStream socketOutput = new DataOutputStream(
          socket.getOutputStream()
        );
        DataInputStream socketInput = new DataInputStream(
          socket.getInputStream()
        );
        handshakeWithPeer(torrent, peer, socketInput, socketOutput);
        downloadedPiece =
          downloadPieceFromPeer(
            torrent,
            peerHost,
            peerPort,
            pieceIndex,
            socketInput,
            socketOutput
          );
        break;
      } catch (Exception e) {
        System.err.println(
          "Failed to download from peer " + peer + ": " + e.getMessage()
        );
        continue;
      }
    }
    if (downloadedPiece == null) {
      throw new RuntimeException("Failed to download piece from any peer");
    }
    byte[] expectedHash = Arrays.copyOfRange(
      torrent.getPieces(),
      pieceIndex * 20,
      (pieceIndex + 1) * 20
    );
    byte[] actualHash = MessageDigest
      .getInstance("SHA-1")
      .digest(downloadedPiece);
    if (!Arrays.equals(expectedHash, actualHash)) {
      throw new RuntimeException("Piece hash verification failed");
    }
    Path parentDir = Paths
      .get(
        TorrentConstants.DOWNLOAD_PIECE_DIR_PATH +
        TorrentUtils.getFileNameWithoutExtension(torrent.getName()) +
        "-piece-" +
        pieceIndex
      )
      .getParent();
    if (parentDir != null && !Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }
    Files.write(
      new File(
        TorrentConstants.DOWNLOAD_PIECE_DIR_PATH +
        TorrentUtils.getFileNameWithoutExtension(torrent.getName()) +
        "-piece-" +
        pieceIndex
      )
        .toPath(),
      downloadedPiece
    );
    return downloadedPiece;
  }

  public static String handshakeWithPeer(
    Torrent torrent,
    String peerAddress,
    DataInputStream socketInput,
    DataOutputStream socketOutput
  ) throws Exception {
    byte[] peerId = new byte[20];
    new Random().nextBytes(peerId);
    byte[] handshakeMsg = createHandshakeMessage(torrent.getInfoHash(), peerId);
    try {
      socketOutput.write(handshakeMsg);
      socketOutput.flush();
      byte[] peerHandshakeMsg = new byte[TorrentConstants.HANDSHAKE_LENGTH];
      socketInput.readFully(peerHandshakeMsg);
      validatePeerHandshakeMsg(peerHandshakeMsg, torrent.getInfoHash());
      byte[] receivedPeerId = Arrays.copyOfRange(
        peerHandshakeMsg,
        48,
        TorrentConstants.HANDSHAKE_LENGTH
      );
      return TorrentUtils.bytesToHex(receivedPeerId);
    } catch (Exception e) {
      System.err.println(
        "Handshake failed with peer " + peerAddress + ": " + e.getMessage()
      );
      throw e;
    }
  }

  private static byte[] createHandshakeMessage(byte[] infoHash, byte[] peerId) {
    byte[] handshake = new byte[TorrentConstants.HANDSHAKE_LENGTH];
    int offset = 0;
    handshake[offset++] = 19;
    byte[] protocol =
      "BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1);
    System.arraycopy(protocol, 0, handshake, offset, protocol.length);
    offset += protocol.length;
    offset += 8;
    System.arraycopy(infoHash, 0, handshake, offset, 20);
    offset += 20;
    System.arraycopy(peerId, 0, handshake, offset, 20);
    return handshake;
  }

  public static void validatePeerHandshakeMsg(
    byte[] peerHandshakeMsg,
    byte[] expectedInfoHash
  ) {
    byte protocolLength = peerHandshakeMsg[0];
    if (protocolLength != 19) {
      throw new RuntimeException("Invalid protocol length: " + protocolLength);
    }
    byte[] protocolBytes = Arrays.copyOfRange(peerHandshakeMsg, 1, 20);
    String protocol = new String(protocolBytes, StandardCharsets.ISO_8859_1);
    if (!"BitTorrent protocol".equals(protocol)) {
      throw new RuntimeException("Invalid protocol: " + protocol);
    }
    byte[] receivedInfoHash = Arrays.copyOfRange(peerHandshakeMsg, 28, 48);
    if (!Arrays.equals(expectedInfoHash, receivedInfoHash)) {
      throw new RuntimeException("Info hash mismatch");
    }
  }

  public static byte[] downloadPieceFromPeer(
    Torrent torrent,
    String host,
    int port,
    int pieceIndex,
    DataInputStream socketInput,
    DataOutputStream socketOutput
  ) throws Exception {
    PeerMessage bitfieldMsg = readMessageFromPeer(socketInput);
    if (bitfieldMsg.getMessageId() != TorrentConstants.BITFIELD_ID) {
      throw new RuntimeException(
        "Expected bitfield message, got: " + bitfieldMsg.getMessageId()
      );
    }
    sendMessageToPeer(
      socketOutput,
      TorrentConstants.INTERESTED_ID,
      new byte[0]
    );
    PeerMessage unchokeMsg = readMessageFromPeer(socketInput);
    if (unchokeMsg.getMessageId() != TorrentConstants.UNCHOKE_ID) {
      throw new RuntimeException(
        "Expected unchoke message, got: " + unchokeMsg.getMessageId()
      );
    }
    long pieceLength = getPieceLength(torrent, pieceIndex);
    int numBlocks = (int) Math.ceil(
      (double) pieceLength / TorrentConstants.BLOCK_SIZE
    );
    byte[] pieceData = new byte[(int) pieceLength];
    int offset = 0;
    for (int blockIndex = 0; blockIndex < numBlocks; blockIndex++) {
      int blockLength = (int) Math.min(
        TorrentConstants.BLOCK_SIZE,
        pieceLength - offset
      );
      byte[] requestPayload = createRequestPayload(
        pieceIndex,
        offset,
        blockLength
      );
      sendMessageToPeer(
        socketOutput,
        TorrentConstants.REQUEST_ID,
        requestPayload
      );
      PeerMessage pieceMsg = readMessageFromPeer(socketInput);
      if (pieceMsg.getMessageId() != TorrentConstants.PIECE_ID) {
        throw new RuntimeException(
          "Expected piece message, got: " + pieceMsg.getMessageId()
        );
      }
      System.arraycopy(
        pieceMsg.getPayload(),
        8,
        pieceData,
        offset,
        blockLength
      );
      offset += blockLength;
    }
    return pieceData;
  }

  private static PeerMessage readMessageFromPeer(DataInputStream socketInput)
    throws IOException {
    int length = socketInput.readInt();
    if (length == 0) {
      return new PeerMessage(length, (byte) 0, new byte[0]);
    }
    byte messageId = socketInput.readByte();
    byte[] payload = new byte[length - 1];
    socketInput.readFully(payload);
    return new PeerMessage(length, messageId, payload);
  }

  private static void sendMessageToPeer(
    DataOutputStream socketOutput,
    byte messageId,
    byte[] payload
  ) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(
      TorrentConstants.MESSAGE_LENGTH_SIZE +
      TorrentConstants.MESSAGE_ID_SIZE +
      payload.length
    );
    buffer.putInt(TorrentConstants.MESSAGE_ID_SIZE + payload.length);
    buffer.put(messageId);
    buffer.put(payload);
    socketOutput.write(buffer.array());
    socketOutput.flush();
  }

  private static byte[] createRequestPayload(int index, int begin, int length) {
    ByteBuffer buffer = ByteBuffer.allocate(12);
    buffer.putInt(index);
    buffer.putInt(begin);
    buffer.putInt(length);
    return buffer.array();
  }

  private static long getPieceLength(Torrent torrent, int pieceIndex) {
    long totalLength = torrent.getTotalLength();
    int pieceLength = torrent.getPieceLength();
    if (pieceIndex * pieceLength + pieceLength > totalLength) {
      return totalLength - (pieceIndex * pieceLength);
    }
    return pieceLength;
  }
}
