package com.jipsamoye.backend.loadtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 펫 커뮤니티 게시글 제목 생성기.
 *
 * <p>구조: [관형어] + [반려동물] + [상황/행동] + [변별요소] + [부가표현] + [어미/감탄]
 * 예) "우리집 먼치킨 고양이 첫 미용 후기 8개월차 요즘 푹 빠졌어요 완전 대박이에요"
 *
 * <p>슬롯별 단어풀의 조합 수가 5억을 충분히 초과하므로 1000만 행에서도 유니크가 자연 보장된다
 * (생일 문제 기준 기대 중복률 ≈ 행수/조합수 ≈ 1% 미만).
 * 단, 검색 측정용으로 특정 키워드("고양이", "강아지", "병원")가
 * {@link #KEYWORD_CAT_RATIO} 등 의도된 비율로 등장하도록 슬롯 선택을 제어한다.
 *
 * <p>변별요소(DISCRIMINATORS) 슬롯은 나이/몸무게/함께한 일수 등 펫 도메인에 자연스러운 숫자
 * 표현으로, 조합 수를 수백 배 늘리는 핵심 슬롯이다. 한 제목에 숫자 표현은 1개만 들어간다.
 * 부가표현(EXTRAS)/변별요소(DISCRIMINATORS) 슬롯은 키워드("고양이"/"강아지"/"병원")를
 * 포함하지 않으므로 키워드 비율 제어에 영향을 주지 않는다.
 *
 * <p>순수 Java 로직 — Spring/JPA/DB 의존성 없음.
 */
public final class TitleGenerator {

    // ── 검색 측정용 키워드 목표 비율 (제목에 해당 키워드가 포함될 확률) ──
    public static final double KEYWORD_CAT_RATIO = 0.08;  // "고양이"
    public static final double KEYWORD_DOG_RATIO = 0.12;  // "강아지"
    public static final double KEYWORD_HOSPITAL_RATIO = 0.05; // "병원"

    public static final String KEYWORD_CAT = "고양이";
    public static final String KEYWORD_DOG = "강아지";
    public static final String KEYWORD_HOSPITAL = "병원";

    public static final int MAX_TITLE_LENGTH = 100;

    /** 관형어 슬롯 (반려동물 앞 수식어). */
    static final List<String> MODIFIERS = List.of(
            "우리집", "옆집", "처음 키우는", "막내", "첫째", "둘째", "셋째", "장난꾸러기",
            "애교쟁이", "소심한", "용감한", "겁많은", "먹보", "잠꾸러기", "호기심 많은",
            "도도한", "순둥이", "말썽쟁이", "귀여운", "사랑스러운", "활발한", "조용한",
            "통통한", "날씬한", "엉뚱한", "똑똑한", "느긋한", "수줍은", "당당한", "포동포동한",
            "꼬물꼬물", "복슬복슬", "보들보들", "쫄깃한", "심쿵유발", "온순한", "장난기 많은",
            "겁쟁이", "개구쟁이", "응석받이",
            // ── 확장 ──
            "새침한", "넉살좋은", "다정한", "씩씩한", "느릿한", "부지런한", "게으른",
            "촐랑대는", "의젓한", "철없는", "철든", "예민한", "둔한", "눈치빠른",
            "고집센", "순한", "까칠한", "허당끼 있는", "정많은", "수다스러운", "과묵한",
            "엄살쟁이", "투정쟁이", "효자", "효녀", "분리불안", "낯가리는", "붙임성좋은",
            "겁없는", "조심성많은", "장난좋아하는", "산책광", "간식러버", "낮잠러버",
            "이불킬러", "박스러버", "창문지기", "현관지기", "그림자수호자", "껌딱지"
    );

    /** 반려동물 슬롯 — "고양이"/"강아지" 키워드와 일반(기타) 동물을 분리 관리. */
    static final List<String> CAT_PHRASES = List.of(
            "먼치킨 고양이", "코숏 고양이", "러시안블루 고양이", "스코티시폴드 고양이",
            "페르시안 고양이", "샴 고양이", "노르웨이숲 고양이", "벵갈 고양이",
            "랙돌 고양이", "아메숏 고양이", "터키시앙고라 고양이", "메인쿤 고양이",
            "삼색이 고양이", "치즈태비 고양이", "고등어 고양이", "까만 고양이",
            // ── 확장 ──
            "아비시니안 고양이", "브리티시숏헤어 고양이", "스핑크스 고양이", "데본렉스 고양이",
            "엑조틱숏헤어 고양이", "셀커크렉스 고양이", "오리엔탈숏헤어 고양이", "버만 고양이",
            "통키니즈 고양이", "싱가퓨라 고양이", "맹크스 고양이", "샤트룩스 고양이",
            "흰둥이 고양이", "젖소 고양이", "카오스 고양이", "올블랙 고양이",
            "턱시도 고양이", "젖소무늬 고양이", "회색 고양이", "갈색 고양이"
    );

    static final List<String> DOG_PHRASES = List.of(
            "말티즈 강아지", "푸들 강아지", "포메라니안 강아지", "비숑 강아지",
            "시바견 강아지", "웰시코기 강아지", "리트리버 강아지", "치와와 강아지",
            "닥스훈트 강아지", "진돗개 강아지", "보더콜리 강아지", "사모예드 강아지",
            "시츄 강아지", "요크셔테리어 강아지", "비글 강아지", "골든리트리버 강아지",
            // ── 확장 ──
            "말라뮤트 강아지", "허스키 강아지", "스피츠 강아지", "코카스파니엘 강아지",
            "퍼그 강아지", "프렌치불독 강아지", "잭러셀테리어 강아지", "슈나우저 강아지",
            "달마시안 강아지", "셔틀랜드쉽독 강아지", "비글하운드 강아지", "토이푸들 강아지",
            "스탠다드푸들 강아지", "믹스견 강아지", "백구 강아지", "황구 강아지",
            "검둥이 강아지", "삽살개 강아지", "풍산개 강아지", "보스턴테리어 강아지"
    );

    /** 키워드와 무관한 기타 반려동물(고양이/강아지 키워드를 포함하지 않음). */
    static final List<String> OTHER_PETS = List.of(
            "햄스터", "토끼", "고슴도치", "앵무새", "거북이", "이구아나", "친칠라",
            "페럿", "기니피그", "다람쥐", "도마뱀", "금붕어", "구피", "사슴벌레",
            "병아리", "잉꼬", "카나리아", "라쿤", "미어캣", "달팽이", "장수풍뎅이",
            // ── 확장 ──
            "슈가글라이더", "햄찌", "드워프햄스터", "롭이어토끼", "네덜란드드워프토끼",
            "왕관앵무", "회색앵무", "모란앵무", "코뉴어", "사랑앵무", "십자매",
            "베타", "코리도라스", "구관조", "왕도마뱀", "크레스티드게코", "레오파드게코",
            "표범도마뱀", "남생이", "사육거북", "헤르만육지거북", "민물새우", "달팽이농장"
    );

    /** 상황/행동 슬롯 — "병원" 키워드 포함군과 일반군 분리. */
    static final List<String> HOSPITAL_SITUATIONS = List.of(
            "동물병원 다녀온 후기", "병원 정기검진 받은 날", "병원에서 중성화 수술한 날",
            "병원 예방접종 후기", "병원 스케일링 받은 날", "병원 건강검진 결과",
            "병원 처음 가본 날", "병원 입원했다 퇴원한 날", "병원 엑스레이 찍은 날",
            "병원 다녀와서 푹 자는 중", "야간 응급병원 다녀온 썰", "병원 단골 됐어요",
            // ── 확장 ──
            "병원 슬개골 검사 받은 날", "병원 심장사상충 검사 후기", "병원 종합백신 맞은 날",
            "병원 초음파 검사한 날", "병원 피검사 결과 나온 날", "병원 발치 수술한 날",
            "병원 항문낭 짜러 간 날", "병원 귀청소 받은 날", "병원 영양제 처방받은 날",
            "병원 알레르기 진료 후기", "병원 정기 구충 받은 날", "병원 건강검진 패키지 후기"
    );

    static final List<String> SITUATIONS = List.of(
            "첫 미용 후기", "산책 다녀온 날", "처음 목욕시킨 날", "간식 먹는 모습",
            "낮잠 자는 모습", "장난감 가지고 노는 중", "발톱 깎은 날", "새 사료 급여 후기",
            "입양 첫날 기록", "생일 파티 한 날", "처음 사진 찍은 날", "재롱 부리는 중",
            "방석 점령한 모습", "창밖 구경하는 중", "츄르 먹방", "배변훈련 성공한 날",
            "처음 짖은 날", "스크래쳐 쓰는 모습", "이갈이 시기 기록", "털갈이 시즌 근황",
            "낯가림 극복기", "사회화 훈련 중", "캣타워 정복기", "노즈워크 도전기",
            "처음 만난 날", "집들이 한 날", "장난감 부순 썰", "간식 숨긴 거 들킨 날",
            "택배 상자 들어간 모습", "이불 속 숨은 모습", "주인 따라다니는 중",
            // ── 확장 ──
            "첫 외출 나간 날", "처음 차 타본 날", "애견카페 다녀온 날", "수영장 데뷔한 날",
            "눈 처음 본 날", "바다 구경 간 날", "단풍 구경 나간 날", "첫 눈썰매 탄 날",
            "새 하네스 착용한 날", "방석 새로 바꾼 날", "장난감 새로 사준 날", "캣휠 정복기",
            "터그놀이 한 판", "공놀이 삼매경", "숨바꼭질 하는 중", "거울 보고 놀란 날",
            "청소기 무서워하는 중", "빗질 받는 모습", "양치 훈련 중", "손 주기 배운 날",
            "기다려 성공한 날", "하우스 적응기", "분리불안 극복 중", "새 식구 합사기",
            "형아랑 노는 중", "동생 생긴 날", "처음 눈맞춤 한 날", "재채기하는 모습",
            "하품하는 순간 포착", "기지개 켜는 중", "골골송 부르는 중", "꾹꾹이 하는 중",
            "식빵 굽는 자세", "우다다 타임", "그루밍 삼매경", "간식 기다리는 눈빛"
    );

    /**
     * 변별요소(숫자) 슬롯 — 펫 도메인에 자연스러운 숫자 표현. 조합 수를 수백 배 늘리는 핵심.
     * 나이(개월/살), 몸무게, 함께한 일수, 입양 일차, D-day 등을 한 제목에 1개만 사용한다.
     * 키워드("고양이"/"강아지"/"병원")를 포함하지 않으므로 키워드 비율 제어에 영향이 없다.
     *
     * <p>{@link #buildDiscriminators()} 로 프로그램적으로 생성한다.
     */
    static final List<String> DISCRIMINATORS = buildDiscriminators();

    private static List<String> buildDiscriminators() {
        List<String> list = new ArrayList<>(700);
        // 개월수: 1~24개월차 (어린 반려동물의 흔한 표현)
        for (int m = 1; m <= 24; m++) {
            list.add(m + "개월차");
            list.add("생후 " + m + "개월");
        }
        // 나이(살): 1~20살
        for (int y = 1; y <= 20; y++) {
            list.add(y + "살");
            list.add(y + "살 기념");
        }
        // 몸무게: 1.0kg ~ 9.9kg (0.1 단위 일부) — 소형~중형 반려동물 자연 범위
        for (int kg = 1; kg <= 9; kg++) {
            for (int d = 0; d <= 9; d++) {
                list.add(kg + "." + d + "kg");
            }
        }
        // 함께한 일수 / 입양 일차: 의미 있는 마일스톤들
        int[] dayMilestones = {
                30, 50, 100, 150, 200, 250, 300, 365, 500, 600, 700, 730, 1000, 1095, 1500
        };
        for (int d : dayMilestones) {
            list.add("함께한 지 " + d + "일");
            list.add("입양 " + d + "일차");
            list.add("D+" + d);
        }
        // 주차: 1~12주차 (어린 개체)
        for (int w = 1; w <= 12; w++) {
            list.add(w + "주차");
        }
        return List.copyOf(list);
    }

    /**
     * 부가표현 슬롯 — 상황과 어미 사이에 들어가는 자연스러운 부가 표현.
     * 키워드("고양이"/"강아지"/"병원")를 포함하지 않으므로 키워드 비율 제어에 영향이 없다.
     */
    static final List<String> EXTRAS = List.of(
            "요즘 푹 빠졌어요", "매일이 행복해요", "보면 볼수록", "하루하루 소중해요",
            "이런 매력이 있네요", "키우길 잘했어요", "다시 봐도 좋아요", "오늘도 한 컷",
            "정성 가득 담아", "함께한 시간들", "기록으로 남겨요", "마음이 몽글몽글",
            "두고두고 볼래요", "소소한 일상",
            // ── 확장 ──
            "행복이 별거 없네요", "보기만 해도 미소가", "사랑이 넘쳐요", "오늘의 힐링",
            "이 맛에 키워요", "심장이 말랑말랑", "퇴근이 기다려져요", "집이 화사해졌어요",
            "웃음꽃이 폈어요", "하루의 비타민", "보물 1호랍니다", "내 인생 최고의 선택",
            "매일이 선물 같아요", "작은 행복 한 스푼", "오늘도 평화롭게", "마음이 따뜻해져요"
    );

    /** 어미/감탄 슬롯. */
    static final List<String> ENDINGS = List.of(
            "완전 대박이에요", "너무 귀여워요", "심쿵주의", "공유합니다", "자랑하고 싶어요",
            "봐주세요", "흐뭇하네요", "기록용", "추천해요", "고민이에요", "도와주세요",
            "행복합니다", "웃음나요", "감동이에요", "신기해요", "뭉클하네요", "사랑해요",
            "최고예요", "후회없어요", "꿀팁공유", "다들 어떠세요", "진심 추천", "역대급이에요",
            "또 보고싶어요", "눈물나요", "흐뭇", "ㅎㅎ", "ㅠㅠ", "!!", "...",
            // ── 확장 ──
            "심쿵했어요", "녹아내려요", "안 귀여울 수가", "다들 보세요", "공유 안 할 수 없죠",
            "보고 또 봐요", "행복 그 자체", "사랑스러워요", "기특해요", "대견해요",
            "감사한 하루", "복받았어요", "이게 행복이죠", "두근두근", "두 눈을 의심했어요",
            "오늘도 감사", "찐사랑", "내새꾸", "효도하네요", "리얼 천사"
    );

    private TitleGenerator() {
    }

    /**
     * 제목 한 건 생성. 키워드 비율 제어를 위해 반려동물/상황 슬롯을 카테고리에 따라 선택한다.
     *
     * <p>키워드 우선순위: 고양이/강아지/기타(동물 슬롯) 결정 후, 병원 키워드는 상황 슬롯에서
     * 독립적으로 결정한다. 따라서 한 제목에 "강아지"와 "병원"이 동시에 들어갈 수도 있다
     * (실제 게시판과 유사). 각 키워드의 전체 등장 비율은 목표값에 수렴한다.
     */
    public static String generate(Random random) {
        String modifier = pick(MODIFIERS, random);

        // 반려동물 슬롯: 고양이 / 강아지 / 기타
        double r = random.nextDouble();
        String pet;
        if (r < KEYWORD_CAT_RATIO) {
            pet = pick(CAT_PHRASES, random);
        } else if (r < KEYWORD_CAT_RATIO + KEYWORD_DOG_RATIO) {
            pet = pick(DOG_PHRASES, random);
        } else {
            pet = pick(OTHER_PETS, random);
        }

        // 상황 슬롯: 병원 키워드 비율 제어 (동물 슬롯과 독립)
        String situation;
        if (random.nextDouble() < KEYWORD_HOSPITAL_RATIO) {
            situation = pick(HOSPITAL_SITUATIONS, random);
        } else {
            situation = pick(SITUATIONS, random);
        }

        String discriminator = pick(DISCRIMINATORS, random);
        String extra = pick(EXTRAS, random);
        String ending = pick(ENDINGS, random);

        String title = modifier + " " + pet + " " + situation + " "
                + discriminator + " " + extra + " " + ending;
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }
        return title;
    }

    /**
     * 생성 가능한 제목의 이론적 조합 수.
     *
     * <p>반려동물 슬롯은 키워드 비율 제어로 카테고리(고양이/강아지/기타)가 확률적으로 선택되므로,
     * 실제로 등장 가능한 반려동물 표현 수는 세 단어풀의 합집합 크기다.
     * 상황 슬롯도 동일하게 (병원 + 일반) 합집합이다.
     *
     * <p>= MODIFIERS × (CAT+DOG+OTHER) × (HOSPITAL+SITUATIONS) × DISCRIMINATORS × EXTRAS × ENDINGS
     */
    public static long combinationCount() {
        long modifiers = distinctCount(MODIFIERS);
        long pets = distinctUnion(CAT_PHRASES, DOG_PHRASES, OTHER_PETS);
        long situations = distinctUnion(HOSPITAL_SITUATIONS, SITUATIONS);
        long discriminators = distinctCount(DISCRIMINATORS);
        long extras = distinctCount(EXTRAS);
        long endings = distinctCount(ENDINGS);
        return modifiers * pets * situations * discriminators * extras * endings;
    }

    private static long distinctCount(List<String> pool) {
        return pool.stream().distinct().count();
    }

    @SafeVarargs
    private static long distinctUnion(List<String>... pools) {
        return java.util.Arrays.stream(pools)
                .flatMap(List::stream)
                .distinct()
                .count();
    }

    private static String pick(List<String> pool, Random random) {
        return pool.get(random.nextInt(pool.size()));
    }
}
