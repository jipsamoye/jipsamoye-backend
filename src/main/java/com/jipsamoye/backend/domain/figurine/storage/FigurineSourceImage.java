package com.jipsamoye.backend.domain.figurine.storage;

/**
 * OpenAI에 전달할 원본(또는 썸네일) 이미지 바이트와 메타데이터.
 */
public record FigurineSourceImage(byte[] bytes, String contentType, String filename) {
}
