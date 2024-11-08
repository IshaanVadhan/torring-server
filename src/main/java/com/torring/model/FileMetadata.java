package com.torring.model;

import java.util.List;
import lombok.Getter;

@Getter
public class FileMetadata {

  private final List<String> path;
  private final long length;

  public FileMetadata(List<String> path, long length) {
    this.path = path;
    this.length = length;
  }
}
