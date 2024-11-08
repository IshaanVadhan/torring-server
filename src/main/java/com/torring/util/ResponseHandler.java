package com.torring.util;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponseHandler extends ResponseEntity<Object> {

  public ResponseHandler(HttpStatus status) {
    this(status, null, null);
  }

  public ResponseHandler(HttpStatus status, String message) {
    this(status, message, null);
  }

  public ResponseHandler(HttpStatus status, Object data) {
    this(status, null, data);
  }

  public ResponseHandler(HttpStatus status, String message, Object data) {
    super(buildResponse(message, status, data), status);
  }

  private static Map<String, Object> buildResponse(
    String message,
    HttpStatus status,
    Object data
  ) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", status);
    if (message != null) {
      response.put("message", message);
    }
    if (data != null) {
      response.put("data", data);
    }
    return response;
  }
}
