package com.jipsamoye.backend.loadtest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * 성능 측정용 더미 데이터 생성기 (순수 Java → TSV 파일).
 *
 * <p>Spring 컨텍스트/JPA/DB 접근 없이 단어풀 조합으로 users / pet_post TSV 를 생성한다.
 * 생성된 파일은 {@code loadtest/load.sh} 의 LOAD DATA LOCAL INFILE 로 적재한다.
 *
 * <p>실행:
 * <pre>
 *   ./gradlew testClasses
 *   java -cp build/classes/java/test com.jipsamoye.backend.loadtest.DummyDataGenerator
 * </pre>
 * 또는 {@link DummyDataGeneratorRunTest} 의 (기본 비활성) 테스트로 실행.
 */
public final class DummyDataGenerator {

    public static final int USER_COUNT = 10_000;
    public static final int POST_COUNT = 10_000_000;

    /** created_at 분산 범위(년). 현재 시점 기준 과거 N년 ~ 현재. */
    public static final int CREATED_AT_SPAN_YEARS = 3;

    /** like_count 멱법칙 파라미터. exponent 가 클수록 0 근처에 더 쏠린다. */
    public static final int MAX_LIKE = 50_000;
    public static final double LIKE_POWER_EXPONENT = 6.0;

    /** image_urls 가 빈 배열([])일 확률. */
    public static final double EMPTY_IMAGE_RATIO = 0.30;
    public static final int MAX_IMAGES = 5;

    /** comment_count: 0 또는 소량. */
    public static final int MAX_COMMENT = 30;
    public static final double LIKE_COMMENT_EXPONENT = 5.0;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static final long SEED = 20260614L;

    private DummyDataGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "loadtest/data");
        Files.createDirectories(outDir);

        Path usersFile = outDir.resolve("users.tsv");
        Path postsFile = outDir.resolve("pet_post.tsv");

        long start = System.currentTimeMillis();
        System.out.println("[loadtest] generating users -> " + usersFile.toAbsolutePath());
        generateUsers(usersFile, USER_COUNT, SEED);

        System.out.println("[loadtest] generating pet_post -> " + postsFile.toAbsolutePath());
        generatePosts(postsFile, POST_COUNT, USER_COUNT, SEED + 1);

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[loadtest] done. users=%d posts=%d elapsed=%.1fs%n",
                USER_COUNT, POST_COUNT, elapsed / 1000.0);
    }

    /**
     * users TSV 생성.
     * 컬럼 순서(LOAD DATA 와 일치): nickname, email, provider, provider_id, role,
     * created_at, updated_at  (id 는 AUTO_INCREMENT, 나머지 nullable 은 \N)
     *
     * <p>주의: pet_post.user_id 는 1..USER_COUNT 를 참조한다. 빈 테이블에 적재하므로
     * AUTO_INCREMENT id 가 1 부터 순서대로 부여되어 i 번째 행 = id i 가 보장된다.
     */
    public static void generateUsers(Path file, int count, long seed) throws IOException {
        Random random = new Random(seed);
        LocalDateTime now = LocalDateTime.now();
        long spanSeconds = (long) CREATED_AT_SPAN_YEARS * 365 * 24 * 3600;

        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w, 1 << 16)) {
            TsvWriter out = new TsvWriter(bw);
            for (int i = 1; i <= count; i++) {
                // nickname: 영숫자 조합, ≤10자, 유니크 (Base36 인코딩으로 보장)
                String nickname = nickname(i);
                String email = "user" + i + "@loadtest.jipsamoye.com";
                String providerId = "naver_" + i;
                LocalDateTime createdAt =
                        now.minusSeconds((long) (random.nextDouble() * spanSeconds));
                String ts = createdAt.format(DT);

                out.field(nickname)        // nickname
                        .field(email)      // email
                        .field("NAVER")    // provider
                        .field(providerId) // provider_id
                        .field("USER")     // role
                        .field(ts)         // created_at
                        .field(ts)         // updated_at
                        .endRow();
            }
            bw.flush();
        }
    }

    /**
     * pet_post TSV 생성.
     * 컬럼 순서(LOAD DATA 와 일치): user_id, title, content, image_urls,
     * like_count, comment_count, created_at, updated_at
     * (id AUTO_INCREMENT, deleted_at 은 LOAD DATA 에서 NULL 지정)
     */
    public static void generatePosts(Path file, int count, int userCount, long seed)
            throws IOException {
        Random random = new Random(seed);
        LocalDateTime now = LocalDateTime.now();
        long spanSeconds = (long) CREATED_AT_SPAN_YEARS * 365 * 24 * 3600;

        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w, 1 << 20)) {
            TsvWriter out = new TsvWriter(bw);
            for (int i = 0; i < count; i++) {
                long userId = 1 + random.nextInt(userCount);
                String title = TitleGenerator.generate(random);
                String imageUrls = imageUrlsJson(userId, random);
                int likeCount = powerLaw(random, MAX_LIKE, LIKE_POWER_EXPONENT);
                int commentCount = powerLaw(random, MAX_COMMENT, LIKE_COMMENT_EXPONENT);
                LocalDateTime createdAt =
                        now.minusSeconds((long) (random.nextDouble() * spanSeconds));
                String ts = createdAt.format(DT);

                out.field(userId)          // user_id
                        .field(title)      // title
                        .nullField()       // content (빈값/NULL)
                        .field(imageUrls)  // image_urls (JSON)
                        .field(likeCount)  // like_count
                        .field(commentCount) // comment_count
                        .field(ts)         // created_at
                        .field(ts)         // updated_at
                        .endRow();
            }
            bw.flush();
        }
    }

    /** like_count / comment_count 멱법칙: 대부분 낮고 소수만 큼. */
    static int powerLaw(Random random, int max, double exponent) {
        double u = random.nextDouble();
        return (int) (max * Math.pow(u, exponent));
    }

    /** image_urls JSON 문자열 생성. 일부 [], 일부 1~MAX_IMAGES 개. */
    static String imageUrlsJson(long userId, Random random) {
        if (random.nextDouble() < EMPTY_IMAGE_RATIO) {
            return "[]";
        }
        int n = 1 + random.nextInt(MAX_IMAGES);
        StringBuilder sb = new StringBuilder(64);
        sb.append('[');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append('"')
                    .append("https://images.jipsamoye.com/posts/")
                    .append(userId).append('/')
                    .append(UUID.randomUUID())
                    .append(".jpg")
                    .append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    /** 유니크 nickname (≤10자, 영숫자). i 를 Base36 로 인코딩하고 'u' 접두. */
    static String nickname(int i) {
        return "u" + Integer.toString(i, 36);
    }
}
