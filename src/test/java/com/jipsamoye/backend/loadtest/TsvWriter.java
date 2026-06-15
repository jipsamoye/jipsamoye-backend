package com.jipsamoye.backend.loadtest;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * MySQL {@code LOAD DATA} 기본 포맷에 맞춘 TSV(탭 구분) 라이터.
 *
 * <p>포맷 약속 (적재 스크립트의 LOAD DATA 구문과 정확히 일치해야 함):
 * <ul>
 *   <li>FIELDS TERMINATED BY '\t' — 탭 구분</li>
 *   <li>ESCAPED BY '\\' — 백슬래시 이스케이프 (ENCLOSED BY 미사용)</li>
 *   <li>LINES TERMINATED BY '\n'</li>
 *   <li>NULL 값은 {@code \N} 으로 표기</li>
 * </ul>
 *
 * <p>title 에 쉼표/따옴표, image_urls JSON 의 큰따옴표 등이 들어와도
 * 탭/개행/백슬래시만 이스케이프하면 안전하다(인용부호를 쓰지 않으므로 따옴표는 평문 취급).
 */
public final class TsvWriter {

    public static final String NULL_TOKEN = "\\N";

    private final BufferedWriter writer;
    private final StringBuilder line = new StringBuilder(512);
    private boolean firstField = true;

    public TsvWriter(BufferedWriter writer) {
        this.writer = writer;
    }

    /** 일반 문자열 필드. null 이면 \N 으로 기록. */
    public TsvWriter field(String value) throws IOException {
        sep();
        if (value == null) {
            line.append(NULL_TOKEN);
        } else {
            escapeInto(value, line);
        }
        return this;
    }

    public TsvWriter field(long value) throws IOException {
        sep();
        line.append(value);
        return this;
    }

    public TsvWriter field(int value) throws IOException {
        sep();
        line.append(value);
        return this;
    }

    /** NULL 필드 명시. */
    public TsvWriter nullField() throws IOException {
        sep();
        line.append(NULL_TOKEN);
        return this;
    }

    /** 한 행 종료 후 기록. */
    public void endRow() throws IOException {
        line.append('\n');
        writer.write(line.toString());
        line.setLength(0);
        firstField = true;
    }

    private void sep() {
        if (firstField) {
            firstField = false;
        } else {
            line.append('\t');
        }
    }

    /**
     * MySQL LOAD DATA 이스케이프 규칙에 맞춰 탭/개행/CR/백슬래시를 이스케이프한다.
     * (ESCAPED BY '\\' 기준)
     */
    static void escapeInto(String value, StringBuilder out) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
    }

    /** 단위 테스트용 — 단일 값 이스케이프 결과 반환. */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        escapeInto(value, sb);
        return sb.toString();
    }
}
