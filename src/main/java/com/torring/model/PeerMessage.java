package com.torring.model;

import lombok.Getter;

@Getter
public class PeerMessage {

  private final int length;
  private final byte messageId;
  private final byte[] payload;

  public PeerMessage(int length, byte messageId, byte[] payload) {
    this.length = length;
    this.messageId = messageId;
    this.payload = payload;
  }
}
