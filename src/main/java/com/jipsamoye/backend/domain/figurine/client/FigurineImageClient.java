package com.jipsamoye.backend.domain.figurine.client;

/**
 * 반려동물 사진을 키캡 굿즈 스타일 이미지로 변환하는 클라이언트.
 */
public interface FigurineImageClient {

    /**
     * 입력 이미지를 키캡 피규어 스타일로 변환한 PNG 바이트를 반환한다.
     *
     * @param sourceImage 입력 이미지 바이트
     * @param contentType 입력 이미지 Content-Type (image/webp 등)
     * @param filename    multipart 전송용 파일명
     */
    byte[] generateKeycapImage(byte[] sourceImage, String contentType, String filename);
}
