package com.torring.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TorrentWebSocketHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(
    TorrentWebSocketHandler.class
  );

  private final Set<WebSocketSession> sessions = new HashSet<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    logger.info("New WebSocket connection established: " + session.getId());
  }

  @Override
  protected void handleTextMessage(
    WebSocketSession session,
    TextMessage message
  ) {
    logger.info("Message received: " + message.getPayload());
  }

  @Override
  public void afterConnectionClosed(
    WebSocketSession session,
    CloseStatus status
  ) {
    sessions.remove(session);
    logger.info("WebSocket connection closed: " + session.getId());
  }

  public void sendMessageToAll(String message) throws IOException {
    TextMessage textMessage = new TextMessage(message);
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        session.sendMessage(textMessage);
      }
    }
  }
}
