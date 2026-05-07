---
name: checkdang-springboot
description: |
  체크당(checkdang) Spring Boot 프로젝트에서 새로운 API, 도메인, 기능을 추가하거나 초기 설정을 구성할 때 팀의 코딩 컨벤션을 정확히 따르도록 안내하는 스킬.
  
  다음 상황에서 반드시 이 스킬을 사용할 것:
  - "~~ API 만들어줘", "~~ 기능 추가해줘", "~~ 엔드포인트 만들어줘"
  - "새 도메인/엔티티 추가", "Controller/Service/Repository 작성"
  - 체크당 프로젝트에서 DynamoDB, JWT, OAuth2 관련 코드 작성
  - build.gradle, application.yaml, SecurityConfig 등 초기 설정 구성
  - 기존 코드와 동일한 패턴으로 코드를 생성해야 할 때
---

# 체크당 Spring Boot 코딩 컨벤션 스킬

## 프로젝트 기본 정보

- **Java**: 17
- **Spring Boot**: 3.5.x
- **루트 패키지**: `com.checkdang`
- **데이터베이스**: RDS (JPA) + DynamoDB (시계열)
- **인증**: JWT + OAuth2 (Google, Kakao)
- **메인 클래스**: `@SpringBootApplication` + `@EnableJpaAuditing` (exclude 없음 — RDS 사용하므로)

```java
@SpringBootApplication
@EnableJpaAuditing  // @CreatedDate, @LastModifiedDate 자동 채움 활성화
public class CheckdangApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckdangApplication.class, args);
    }
}
```

**Why `@EnableJpaAuditing`**: 이 어노테이션 없으면 JPA Entity의 `@CreatedDate`/`@LastModifiedDate`가 동작하지 않아 `createdAt`이 항상 `null`로 저장됨. Auditing은 글로벌 설정이라 메인 클래스에 한 번만 선언.

## 데이터 저장소 구분 (설계 원칙)

### RDS + JPA 사용 대상
관계형 구조가 필요하거나 다른 테이블과 FK로 연결되는 데이터:

| 도메인 | 이유 |
|---|---|
| 사용자 계정 (User) | 모든 RDS 테이블의 FK 기준점 |
| 식단 (음식/칼로리/영양소) | 음식 정보 정규화, 끼니별 관계형 구조 |
| 수면 | 수면 단계별 구조화 데이터 |
| 근력 운동 (루틴/세트/무게) | 운동종류 ↔ 루틴 ↔ 세트 다대다 관계 |
| 통증 기록 + AI 분석 결과 | 바디맵 좌표 + AI 분석이 하나의 기록으로 묶임, 과거 패턴 쿼리 필요 |

### DynamoDB 사용 대상
시간 흐름에 따라 쌓이는 시계열 데이터 (Partition Key: userId, Sort Key: timestamp):

| 도메인 | 이유 |
|---|---|
| 유산소 운동 (칼로리/걸음수) | 삼성헬스 스트림, 심박수와 시간축 연계 분석 |
| 심박수 | 고빈도 시계열 |
| 혈당 수치 | 시간당 직접 입력, AI 예측 모델 입력값 |

---

## 초기 설정 (build.gradle)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.x'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.checkdang'
version = '0.0.1-SNAPSHOT'
java { sourceCompatibility = '17' }

configurations { compileOnly { extendsFrom annotationProcessor } }

repositories { mavenCentral() }

dependencyManagement {
    imports {
        mavenBom "software.amazon.awssdk:bom:2.25.70"  // AWS SDK 버전 일괄 관리
    }
}

dependencies {
    // 웹 서버
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'  // /actuator/health 헬스체크용

    // 보안 (JWT + OAuth2)
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'  // Google, Kakao 소셜 로그인
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'  // JWT → JSON 변환

    // RDS (사용자 계정, 식단, 수면, 근력 운동, 통증 기록)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'  // AWS RDS MySQL

    // DynamoDB (시계열 3종: 유산소 운동, 심박수, 혈당)
    implementation 'software.amazon.awssdk:dynamodb'
    implementation 'software.amazon.awssdk:dynamodb-enhanced'

    // 유틸
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    implementation 'me.paulschwarz:spring-dotenv:4.0.0'  // .env 파일 → 환경변수 로딩

    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

---

## 초기 설정 (application.yaml)

```yaml
spring:
  application:
    name: checkdang

  datasource:
    url: jdbc:mysql://${RDS_HOST}:3306/${RDS_DB_NAME}?useSSL=true&serverTimezone=Asia/Seoul
    username: ${RDS_USERNAME}
    password: ${RDS_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate        # 프로덕션: validate (스키마 자동 변경 금지)
                                # 개발 초기: create 또는 update 사용 가능
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true
    show-sql: false             # 프로덕션: false (로그 과다 방지)

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            client-authentication-method: client_secret_post  # Kakao는 POST 방식 인증 요구
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/kakao"
            scope:
              - profile_nickname
              # email 스코프는 Kakao 비즈니스 앱 승인 전에는 요청 불가 → 생략
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id  # Kakao 응답의 고유 식별자 필드명

jwt:
  secret: ${JWT_SECRET}          # Base64 인코딩된 256비트 이상 키 (HS256 알고리즘 최소 요구사항)
  expiration: ${JWT_EXPIRATION}  # 밀리초 단위, 기본값 86400000 (24시간)

oauth2:
  redirect-uri: ${OAUTH2_REDIRECT_URI}  # OAuth2 로그인 성공 후 프론트엔드 콜백 URL

aws:
  region: ${AWS_REGION}          # 기본값: ap-northeast-2 (서울)
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
  dynamodb:
    endpoint: ${DYNAMODB_ENDPOINT:}   # 로컬 개발: http://localhost:8000, 프로덕션: 빈칸
    tables:                            # 시계열 3종 테이블명 (DynamoDbConfig가 참조)
      blood-sugar: blood_sugar_records
      heart-rate: heart_rate_records
      cardio-exercise: cardio_exercise_records

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health  # 헬스체크만 외부 노출, 나머지 보안상 비활성화
```

### 테스트용 설정 (src/test/resources/application.yaml)

RDS는 H2 인메모리 DB, DynamoDB는 로컬 DynamoDB를 사용합니다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:checkdang;MODE=MySQL;DB_CLOSE_DELAY=-1
    # MODE=MySQL: 프로덕션 RDS(MySQL)와 SQL 문법 호환
    # DB_CLOSE_DELAY=-1: 테스트 진행 중 DB 유지
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop          # 테스트마다 스키마 재생성
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

aws:
  region: us-east-1
  credentials:
    access-key: local   # 로컬 DynamoDB는 자격증명 불필요, 더미값 사용
    secret-key: local
  dynamodb:
    endpoint: http://localhost:8000  # DynamoDB Local 사용
    tables:
      blood-sugar: blood_sugar_records
      heart-rate: heart_rate_records
      cardio-exercise: cardio_exercise_records

jwt:
  secret: Y2hlY2tkYW5nLWp3dC1zZWNyZXQta2V5  # 테스트용 고정 키 (프로덕션과 분리)
  expiration: 86400000

oauth2:
  redirect-uri: http://localhost:3000/oauth2/callback
```

테스트용 build.gradle 추가:
```groovy
testRuntimeOnly 'com.h2database:h2'  // 테스트 인메모리 DB
```

---

## 초기 설정 (Config 클래스들)

### AppConfig.java

```java
@Configuration
public class AppConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt 선택 이유: 단방향 해시 + salt 자동 적용으로 rainbow table 공격 방어
        // 비용 계수 기본값 10 = 약 100ms 소요 → brute force 방어에 적절
    }
}
```

### SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // CSRF 비활성화 이유: JWT는 쿠키를 사용하지 않아 CSRF 공격 대상이 아님

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Stateless 이유: JWT 기반 인증 → 서버에 세션 저장 불필요, Fargate 멀티 인스턴스에서 세션 공유 문제 없음

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",       // 회원가입, 로그인
                    "/actuator/health",   // 로드밸런서 헬스체크
                    "/oauth2/**",         // OAuth2 인증 시작
                    "/login/oauth2/**"    // OAuth2 콜백
                ).permitAll()
                .anyRequest().authenticated())

            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo ->
                    userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler))

            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                UsernamePasswordAuthenticationFilter.class);
            // JWT 필터를 앞에 배치: 토큰이 있으면 폼 로그인 필터 전에 인증 처리

        return http.build();
    }
}
```

### DynamoDbConfig.java (베이스 구조)

```java
@Configuration
public class DynamoDbConfig {

    @Value("${aws.region}") private String region;
    @Value("${aws.credentials.access-key}") private String accessKey;
    @Value("${aws.credentials.secret-key}") private String secretKey;
    @Value("${aws.dynamodb.endpoint:}") private String endpoint;
    @Value("${aws.dynamodb.table-name}") private String tableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            // 로컬 개발: DynamoDB Local (http://localhost:8000) 사용
            // 프로덕션: 빈칸으로 두면 AWS 서버 자동 사용
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        // Enhanced Client: Java 객체 ↔ DynamoDB 아이템 자동 변환 지원
    }

    // 각 엔티티마다 아래 패턴으로 테이블 Bean 추가 (StaticTableSchema 사용)
    // 이유: DynamoDB Enhanced의 @DynamoDbBean 어노테이션 대신 StaticTableSchema를 쓰는 이유는
    //       도메인 클래스에 DynamoDB 의존성을 섞지 않기 위함 (관심사 분리)
    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient client) {
        // ... StaticTableSchema 정의 (레이어별 패턴 섹션 참고)
    }
}
```

---

## JWT 패턴

### JwtTokenProvider.java

```java
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        // HS256 사용 이유: 대칭키 방식으로 서버 단독 검증 가능, 비대칭키(RS256) 대비 단순
        this.expiration = expiration;
    }

    public String generateToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)              // subject = 이메일 (사용자 식별자)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;  // 만료, 서명 불일치, 형식 오류 모두 false
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

### JwtAuthenticationFilter.java

```java
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
        // 토큰이 없거나 유효하지 않아도 필터를 통과시킴
        // → permitAll() 경로는 인증 없이 접근 가능, authenticated() 경로는 SecurityContext 비어있어 거부됨
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);  // "Bearer " (7자) 제거
        }
        return null;
    }
}
```

---

## OAuth2 패턴

### CustomOAuth2UserService.java

```java
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email, name, providerId;
        User.Provider provider;

        if ("google".equals(registrationId)) {
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
            providerId = oAuth2User.getAttribute("sub");  // Google 고유 식별자
            provider = User.Provider.GOOGLE;
        } else if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            providerId = String.valueOf(oAuth2User.getAttribute("id"));
            String kakaoEmail = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            // Kakao 이메일 미제공 시 가짜 이메일 생성 (이메일이 Partition Key이므로 필수)
            email = (kakaoEmail != null) ? kakaoEmail : "kakao_" + providerId + "@checkdang.com";
            name = (String) profile.get("nickname");
            provider = User.Provider.KAKAO;
        } else {
            throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 제공자입니다.");
        }

        // 신규 사용자는 자동 가입, 기존 사용자는 그대로 사용
        final String finalEmail = email, finalName = name, finalProviderId = providerId;
        final User.Provider finalProvider = provider;
        userRepository.findByEmail(email).orElseGet(() ->
            userRepository.save(User.builder()
                    .email(finalEmail)
                    .name(finalName)
                    .provider(finalProvider)
                    .providerId(finalProviderId)
                    .role(User.Role.PATIENT)  // OAuth2 신규 가입 기본 역할
                    .build())
        );

        return oAuth2User;
    }
}
```

### OAuth2SuccessHandler.java

```java
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = extractEmail(oAuth2User);
        String token = jwtTokenProvider.generateToken(email);

        // 토큰을 쿼리 파라미터로 전달 → 프론트엔드가 받아서 로컬 스토리지 저장
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String extractEmail(OAuth2User oAuth2User) {
        if (oAuth2User.getAttribute("email") != null) {
            return oAuth2User.getAttribute("email");  // Google
        }
        Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
        String kakaoEmail = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        if (kakaoEmail != null) return kakaoEmail;
        return "kakao_" + oAuth2User.getAttribute("id") + "@checkdang.com";  // Kakao 폴백
    }
}
```

---

## 환경변수 전체 목록 (.env.example)

```properties
# Google OAuth2 (Google Cloud Console에서 발급)
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

# Kakao OAuth2 (Kakao Developers에서 발급, REST API 키 사용)
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=your-kakao-client-secret

# JWT (openssl rand -base64 32 로 생성, 256비트 이상 필수)
JWT_SECRET=your-base64-encoded-256bit-secret
JWT_EXPIRATION=86400000   # 24시간 (밀리초)

# OAuth2 콜백 (프론트엔드 주소)
OAUTH2_REDIRECT_URI=http://localhost:3000/oauth2/callback

# RDS (AWS RDS MySQL)
RDS_HOST=your-rds-endpoint.rds.amazonaws.com
RDS_DB_NAME=checkdang
RDS_USERNAME=your-db-username
RDS_PASSWORD=your-db-password

# AWS DynamoDB (시계열 데이터: 유산소 운동, 심박수, 혈당)
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key
DYNAMODB_ENDPOINT=   # 로컬 개발: http://localhost:8000 / 프로덕션: 빈칸
```

---

## 파일 구조 규칙

전체 패키지 구조:

```
src/main/java/com/checkdang/
├── CheckdangApplication.java          ← @SpringBootApplication + @EnableJpaAuditing
├── config/
│   ├── AppConfig.java                 ← PasswordEncoder Bean
│   ├── SecurityConfig.java            ← Spring Security + JWT 필터 등록
│   └── DynamoDbConfig.java            ← DynamoDB 클라이언트 + 시계열 테이블 Bean들
├── controller/                        ← REST API 엔드포인트
├── service/                           ← 비즈니스 로직
├── repository/                        ← 데이터 접근
│   ├── (RDS)  → JpaRepository 상속 인터페이스
│   └── (Dynamo) → DynamoDbTable 주입 클래스
├── domain/                            ← 엔티티
│   ├── (RDS)  → @Entity 클래스
│   └── (Dynamo) → POJO + Lombok 5종
├── dto/                               ← 요청/응답 DTO + ErrorResponse
├── exception/
│   └── GlobalExceptionHandler.java    ← @RestControllerAdvice
└── security/
    ├── jwt/
    │   ├── JwtTokenProvider.java
    │   └── JwtAuthenticationFilter.java
    └── oauth2/
        ├── CustomOAuth2UserService.java
        └── OAuth2SuccessHandler.java
```

### 새 RDS 도메인 추가 시 (예: `Diet`)

생성할 파일:
```
domain/Diet.java                       ← @Entity, @Table, @EntityListeners
repository/DietRepository.java         ← extends JpaRepository<Diet, Long>
service/DietService.java               ← @Transactional(readOnly = true) + 쓰기 메서드 @Transactional
controller/DietController.java         ← /api/diets
dto/DietRequest.java                   ← @Getter @NoArgsConstructor + @Valid 검증
dto/DietResponse.java                  ← @Getter @Builder + from() 팩토리
```
**수정할 파일**: `SecurityConfig.java` (공개/보호 경로 변경 시)

### 새 DynamoDB 도메인 추가 시 (예: `BloodSugarRecord`)

생성할 파일:
```
domain/BloodSugarRecord.java           ← Lombok 5종, JPA 어노테이션 없음
repository/BloodSugarRepository.java   ← DynamoDbTable<...> 주입, query() 사용
service/BloodSugarService.java         ← @Transactional 없음
controller/BloodSugarController.java   ← /api/blood-sugar-records
dto/BloodSugarRequest.java
dto/BloodSugarResponse.java
```
**수정할 파일**:
- `config/DynamoDbConfig.java` (StaticTableSchema + Bean 추가)
- `application.yaml` (`aws.dynamodb.tables.{도메인}` 추가)
- `.env.example` (필요시)
- `SecurityConfig.java` (공개/보호 경로 변경 시)

---

## 레이어별 코딩 패턴

### RDS 도메인 (JPA 방식)

#### 단일 Entity 패턴
```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 기본 생성자 (외부 직접 생성 방지)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)       // @CreatedDate/@LastModifiedDate 자동 처리
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;  // OAuth2 사용자는 null 가능

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)  // DB에 "PATIENT" 문자열로 저장 (숫자 인덱스 사용 금지)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    private String providerId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Role { PATIENT, DOCTOR, ADMIN }
    public enum Provider { LOCAL, GOOGLE, KAKAO }
}
```

**RDS Entity 규칙:**
- `@Entity` + `@Table(name = "snake_case_테이블명")`
- `@EntityListeners(AuditingEntityListener.class)` — `@CreatedDate` 사용하는 모든 Entity에 필수 (메인 클래스 `@EnableJpaAuditing`과 짝)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA 요구사항이면서 외부 무분별한 생성 방지
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`
- Enum: 반드시 `@Enumerated(EnumType.STRING)` (숫자 저장 시 Enum 순서 변경에 취약)
- 시간: `LocalDateTime` + `@CreatedDate` (DynamoDB와 달리 `Instant` 아님)
- `@Builder`와 `@NoArgsConstructor` 공존 시 `@AllArgsConstructor` 필수

#### 연관관계 매핑 패턴

**다대일 (`@ManyToOne`)**: 자식 → 부모 (대부분의 경우)
```java
@Entity
@Table(name = "blood_sugar_records")  // 만약 RDS에 저장한다면 (현재는 DynamoDB지만 예시)
public class XxxRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)   // 항상 LAZY (EAGER 금지)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

**일대다 (`@OneToMany`)**: 부모 → 자식 컬렉션. **양방향 관계는 신중하게 사용**:
```java
@Entity
@Table(name = "workout_routines")
public class WorkoutRoutine {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String name;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoutineExercise> exercises = new ArrayList<>();
    // mappedBy: FK는 RoutineExercise.routine이 관리 (양방향에서 외래키 주인 명시)
    // cascade ALL + orphanRemoval: 루틴 삭제 시 자식 운동 자동 삭제
    // @Builder.Default: Builder로 생성 시 빈 리스트 보장 (NPE 방지)
}

@Entity
@Table(name = "routine_exercises")
public class RoutineExercise {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private WorkoutRoutine routine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_type_id", nullable = false)
    private ExerciseType exerciseType;

    private Integer orderIndex;  // 루틴 내 순서
}
```

**연관관계 규칙 (중요):**
- `fetch = FetchType.LAZY` 항상 명시. EAGER는 N+1 문제와 의도치 않은 쿼리 폭발의 원인
- `@OneToMany`는 기본이 LAZY이지만 명시적으로 적어 일관성 유지
- `cascade = CascadeType.ALL`은 **부모-자식 관계가 명확할 때만** 사용 (예: 루틴-세트). 다른 도메인이 참조 가능한 엔티티(User 등)에는 절대 사용 금지
- 양방향 관계가 꼭 필요한 경우만 `@OneToMany` 추가. 단방향 `@ManyToOne`만으로 해결 가능하면 그게 더 단순
- `@Builder.Default`로 컬렉션 초기화 (빈 `ArrayList`로 시작)
- 다대다 (`@ManyToMany`)는 사용 금지 — 중간 엔티티 (`RoutineExercise` 같은)로 풀어서 표현해야 추가 컬럼 부착 가능

#### Repository (JPA 방식)
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // 페이징 조회
    Page<User> findByRole(User.Role role, Pageable pageable);

    // 복잡한 쿼리는 @Query 사용 (메서드명으로 표현하기 어려울 때)
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.createdAt >= :since")
    List<User> findRecentUsersByRole(@Param("role") User.Role role,
                                      @Param("since") LocalDateTime since);
}
```

**Repository 규칙:**
- `JpaRepository<Entity, PK타입>` 상속 → 기본 CRUD 자동 제공
- 메서드명 쿼리 (`findBy`, `existsBy`, `countBy`, `deleteBy`) 우선 활용
- 메서드명이 길어지면 `@Query` JPQL로 작성
- 페이징은 `Pageable` 파라미터 받아 `Page<T>` 반환

---

### 1. Controller

```java
@RestController
@RequestMapping("/api/xxxs")       // 도메인명 복수형 kebab-case
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @PostMapping                   // 생성 → 201 Created
    public ResponseEntity<XxxResponse> create(@RequestBody XxxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(xxxService.create(request));
    }

    @GetMapping("/{id}")           // 조회 → 200 OK
    public ResponseEntity<XxxResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(xxxService.getById(id));
    }
}
```

**규칙:**
- `@RestController` + `@RequestMapping` + `@RequiredArgsConstructor` 세트 고정
- URL 베이스 경로: `/api/{도메인복수}` (kebab-case, 예: `/api/blood-sugar-records`)
- 응답은 항상 `ResponseEntity<T>` 래퍼 사용
- 생성 작업: `ResponseEntity.status(HttpStatus.CREATED).body(...)`
- 조회/로그인: `ResponseEntity.ok(...)`
- 비즈니스 로직은 Service에 위임, Controller는 얇게 유지

---

### 2. Service

#### RDS 도메인 Service (사용자/식단/수면/근력운동/통증)

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 클래스 레벨: 기본은 읽기 전용 (성능 최적화 + 안전)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional   // 쓰기 메서드만 readOnly=false로 오버라이드
    public UserResponse create(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(User.Role.PATIENT)
                .provider(User.Provider.LOCAL)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getById(Long id) {   // 조회 메서드는 readOnly 상속 → @Transactional 불필요
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        return UserResponse.from(user);
    }
}
```

**RDS Service 규칙:**
- 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드 `@Transactional` 오버라이드
  - **Why**: 조회는 readOnly로 dirty checking/flush 비활성화 → 성능 향상. 쓰기는 명시적으로 표시 → 의도 명확
- `@Transactional` 없이 RDS 쓰기 작업 시 변경 감지(dirty checking)가 동작하지 않거나 LazyInitializationException 발생

#### DynamoDB 도메인 Service (혈당/심박수/유산소)

```java
@Service
@RequiredArgsConstructor
public class BloodSugarService {

    private final BloodSugarRepository bloodSugarRepository;

    public BloodSugarResponse record(Long userId, BloodSugarRequest request) {
        BloodSugarRecord record = BloodSugarRecord.builder()
                .userId(String.valueOf(userId))
                .measuredAt(request.getMeasuredAt() != null
                        ? request.getMeasuredAt() : Instant.now())
                .bloodSugar(request.getBloodSugar())
                .mealTiming(request.getMealTiming())
                .build();
        return BloodSugarResponse.from(bloodSugarRepository.save(record));
    }

    public List<BloodSugarResponse> getRecentRecords(Long userId, int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        return bloodSugarRepository.findByUserIdSince(String.valueOf(userId), from)
                .stream()
                .map(BloodSugarResponse::from)
                .toList();
    }
}
```

**DynamoDB Service 규칙:**
- `@Transactional` 사용 안 함 — DynamoDB는 JPA 트랜잭션과 무관 (단일 아이템 putItem은 자체 atomic)
- userId는 RDS의 `Long id`를 `String.valueOf()`로 변환해 DynamoDB Partition Key로 사용
- 시계열 데이터의 timestamp는 클라이언트가 보내거나 서버에서 `Instant.now()`로 부여

#### 공통 규칙
- `@Service` + `@RequiredArgsConstructor` 세트 고정
- 예외: `IllegalArgumentException` (비즈니스 로직 위반), `UsernameNotFoundException` (Spring Security 전용)
- 에러 메시지는 항상 한국어
- 엔티티 생성 시 항상 Builder 패턴
- 응답 변환은 DTO의 `from()` 팩토리 메서드 사용

---

### 3. Repository (DynamoDB 시계열 전용)

DynamoDB Repository는 **시계열 3종(혈당, 심박수, 유산소)** 에만 사용합니다. RDS 도메인은 위쪽의 `JpaRepository` 패턴을 사용하세요.

```java
@Repository
@RequiredArgsConstructor
public class BloodSugarRepository {

    private final DynamoDbTable<BloodSugarRecord> bloodSugarTable;

    /** 단건 저장 (Composite Key: userId + measuredAt) */
    public BloodSugarRecord save(BloodSugarRecord record) {
        if (record.getMeasuredAt() == null) {
            record.setMeasuredAt(Instant.now());
        }
        bloodSugarTable.putItem(record);
        return record;
    }

    /** 단건 조회 (Partition Key + Sort Key 모두 필요) */
    public Optional<BloodSugarRecord> findOne(String userId, Instant measuredAt) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(measuredAt.toString())   // Instant → ISO 8601 String
                .build();
        return Optional.ofNullable(bloodSugarTable.getItem(key));
    }

    /** 시간 범위 조회 (시계열 핵심 패턴) */
    public List<BloodSugarRecord> findByUserIdBetween(String userId, Instant from, Instant to) {
        QueryConditional condition = QueryConditional.sortBetween(
                Key.builder().partitionValue(userId).sortValue(from.toString()).build(),
                Key.builder().partitionValue(userId).sortValue(to.toString()).build()
        );
        return bloodSugarTable.query(r -> r.queryConditional(condition))
                .items()
                .stream()
                .toList();
    }

    /** 특정 시점 이후 (예: 최근 N일) */
    public List<BloodSugarRecord> findByUserIdSince(String userId, Instant from) {
        QueryConditional condition = QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder().partitionValue(userId).sortValue(from.toString()).build()
        );
        return bloodSugarTable.query(r -> r.queryConditional(condition))
                .items()
                .stream()
                .toList();
    }

    /** 최신 N건 (역순 정렬) */
    public List<BloodSugarRecord> findLatestByUserId(String userId, int limit) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build()
        );
        return bloodSugarTable.query(r -> r
                        .queryConditional(condition)
                        .scanIndexForward(false)   // false = 내림차순 (최신부터)
                        .limit(limit))
                .items()
                .stream()
                .toList();
    }
}
```

**DynamoDB Repository 규칙:**
- JPA 없음 — Enhanced Client의 `putItem()`, `getItem()`, `query()` 직접 사용
- **Composite Key 설계**: Partition Key = `userId`, Sort Key = timestamp 계열 필드 (`measuredAt`, `recordedAt` 등)
  - **Why**: userId 단독으로는 시계열 조회 불가. Sort Key에 timestamp를 두면 자연스럽게 시간순 정렬 + 범위 쿼리 가능
- 단건 조회는 `getItem(Key)`, 범위 조회는 `query(QueryConditional)` 사용
- 범위 쿼리 종류:
  - `sortBetween(start, end)` — 시간 구간
  - `sortGreaterThanOrEqualTo(from)` — 특정 시점 이후
  - `sortLessThan(to)` — 특정 시점 이전
  - `keyEqualTo(pk)` + `scanIndexForward(false)` — Partition Key만으로 최신순 조회
- `Instant` ↔ String 변환: 항상 `instant.toString()` (ISO 8601)로 직렬화
- `limit(N)` — 페이지네이션 또는 최신 N건만 가져올 때 (DynamoDB 비용 절감 효과 큼)
- 반환값: 단건은 `Optional<T>`, 다건은 `List<T>`

---

### 4. Domain (Entity) — DynamoDB 시계열 전용

DynamoDB에 저장되는 시계열 3종 (유산소 운동, 심박수, 혈당)에만 사용:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloodSugarRecord {   // 예: 혈당 수치

    private String userId;        // Partition Key (RDS User의 id를 String으로 참조)
    private Instant measuredAt;   // Sort Key (시계열 기준)
    private Double bloodSugar;
    private MealTiming mealTiming;

    public enum MealTiming {
        FASTING, AFTER_BREAKFAST, AFTER_LUNCH, AFTER_DINNER
    }
}
```

**DynamoDB Entity 규칙:**
- JPA 어노테이션 없음 — DynamoDB 스키마는 `DynamoDbConfig`에서 정의
- Partition Key: `userId` (String), Sort Key: `timestamp` 또는 `measuredAt` (Instant)
- 시간: `Instant` 타입 (RDS와 달리 LocalDateTime 아님 — ISO 8601 String으로 DynamoDB 저장)
- Lombok 5종: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`

---

### 5. DTO

#### 요청 DTO
```java
@Getter
@NoArgsConstructor
public class XxxRequest {
    private String field1;
    private String field2;
    private Xxx.XxxEnum enumField;   // Entity Enum 참조
}
```

#### 응답 DTO
```java
@Getter
@Builder
public class XxxResponse {
    private String id;
    private String field1;
    private Xxx.XxxEnum enumField;
    private Instant createdAt;

    public static XxxResponse from(Xxx entity) {   // 팩토리 메서드 필수
        return XxxResponse.builder()
                .id(entity.getId())
                .field1(entity.getField1())
                .enumField(entity.getEnumField())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
```

#### 토큰+엔티티 조합 응답 (인증 관련)
```java
@Getter
@Builder
public class TokenXxxResponse {
    private String accessToken;
    private String tokenType;
    private XxxResponse data;

    public static TokenXxxResponse of(String token, XxxResponse data) {
        return TokenXxxResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .data(data)
                .build();
    }
}
```

**규칙:**
- Request: `@Getter @NoArgsConstructor` (직렬화 불필요하므로 Builder 없음)
- Response: `@Getter @Builder` + 반드시 팩토리 메서드 (`from(Entity)` 또는 `of(...)`)
- 민감 정보 (`password` 등)는 Response DTO에서 반드시 제외
- Builder 내부에서 팩토리 메서드로 객체 생성

---

### 6. DynamoDB Config (새 엔티티 추가 시)

`DynamoDbConfig.java`에 새 테이블 Bean 추가:

```java
@Bean
public DynamoDbTable<Xxx> xxxTable(DynamoDbEnhancedClient client) {
    StaticTableSchema<Xxx> schema = StaticTableSchema.builder(Xxx.class)
        .newItemSupplier(Xxx::new)
        .addAttribute(String.class, a -> a
            .name("id")
            .getter(Xxx::getId)
            .setter(Xxx::setId)
            .tags(StaticAttributeTags.primaryPartitionKey()))  // Partition Key
        .addAttribute(String.class, a -> a
            .name("field1")
            .getter(Xxx::getField1)
            .setter(Xxx::setField1))
        // Enum → String 변환 패턴
        .addAttribute(String.class, a -> a
            .name("role")
            .getter(u -> u.getRole() != null ? u.getRole().name() : null)
            .setter((u, v) -> u.setRole(v != null ? Xxx.Role.valueOf(v) : null)))
        // Instant → String 변환 패턴
        .addAttribute(String.class, a -> a
            .name("createdAt")
            .getter(u -> u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
            .setter((u, v) -> u.setCreatedAt(v != null ? Instant.parse(v) : null)))
        .build();
    return client.table("xxx_table_name", schema);  // snake_case 테이블명
}
```

**규칙:**
- Enum 필드: `name()` / `valueOf()` 변환 필수
- `Instant` 필드: `toString()` / `Instant.parse()` 변환 필수
- DynamoDB 테이블명: snake_case (예: `blood_sugar_records`)

---

## 글로벌 예외 처리 (`@RestControllerAdvice`)

`exception/GlobalExceptionHandler.java` — 모든 컨트롤러에서 발생한 예외를 일관된 JSON 응답으로 변환:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 비즈니스 로직 위반 (중복 이메일, 존재하지 않는 ID 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    /** Spring Security 인증 실패 */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    /** OAuth2 인증 실패 */
    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleOAuth2(OAuth2AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }

    /** Bean Validation 실패 (@Valid 검증 실패) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    /** 그 외 예측하지 못한 예외 — 500 + 사용자에게는 일반 메시지만 노출 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);   // 스택트레이스는 서버 로그에만
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                        "서버 내부 오류가 발생했습니다."));
    }
}
```

### ErrorResponse DTO

`dto/ErrorResponse.java`:

```java
@Getter
@Builder
public class ErrorResponse {
    private int status;
    private String error;       // HTTP 상태 영문명 (예: "BAD_REQUEST")
    private String message;     // 사용자에게 보여줄 한국어 메시지
    private Instant timestamp;

    public static ErrorResponse of(HttpStatus status, String message) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.name())
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
```

**예외 처리 규칙:**
- 비즈니스 로직 위반 → `IllegalArgumentException` + 한국어 메시지 → 400 응답
- 인증/인가 → `UsernameNotFoundException`, `OAuth2AuthenticationException` → 401 응답
- 검증 실패 → `@Valid` + `MethodArgumentNotValidException` → 필드별 메시지 묶어서 400 응답
- 예측 못한 예외 → 500이지만 사용자에게는 "서버 내부 오류" 일반 메시지만, 스택트레이스는 로그에
- **민감 정보 노출 금지**: 예외 메시지에 비밀번호/토큰/SQL/파일경로 포함 금지

---

## 삼성헬스 API 데이터 수신 패턴

수면, 식단, 근력 운동 (RDS) + 유산소 운동, 심박수 (DynamoDB) 데이터는 클라이언트(안드로이드 앱)가 삼성헬스 SDK로 데이터를 받은 뒤 우리 API로 전송하는 구조입니다.

### Controller — 일괄 수신 패턴

```java
@RestController
@RequestMapping("/api/samsung-health")
@RequiredArgsConstructor
public class SamsungHealthSyncController {

    private final SleepService sleepService;
    private final HeartRateService heartRateService;
    private final CardioExerciseService cardioExerciseService;

    /** 수면 데이터 일괄 동기화 (RDS) */
    @PostMapping("/sleep")
    public ResponseEntity<SyncResponse> syncSleep(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid List<SleepSyncRequest> requests) {
        Long userId = getUserId(principal);
        int saved = sleepService.syncFromSamsungHealth(userId, requests);
        return ResponseEntity.ok(SyncResponse.of(saved, requests.size()));
    }

    /** 심박수 데이터 일괄 동기화 (DynamoDB, 고빈도) */
    @PostMapping("/heart-rate")
    public ResponseEntity<SyncResponse> syncHeartRate(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid List<HeartRateSyncRequest> requests) {
        Long userId = getUserId(principal);
        int saved = heartRateService.syncFromSamsungHealth(userId, requests);
        return ResponseEntity.ok(SyncResponse.of(saved, requests.size()));
    }
}
```

### Service — 멱등성 보장 + 일괄 처리

```java
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private final HeartRateRepository heartRateRepository;

    /** 삼성헬스 → DynamoDB 동기화. 동일 timestamp는 덮어쓰기 (멱등) */
    public int syncFromSamsungHealth(Long userId, List<HeartRateSyncRequest> requests) {
        String pk = String.valueOf(userId);
        int saved = 0;
        for (HeartRateSyncRequest req : requests) {
            HeartRateRecord record = HeartRateRecord.builder()
                    .userId(pk)
                    .recordedAt(req.getRecordedAt())   // 클라이언트가 보낸 측정 시각 그대로 사용
                    .bpm(req.getBpm())
                    .source("SAMSUNG_HEALTH")
                    .build();
            heartRateRepository.save(record);
            saved++;
        }
        return saved;
    }
}
```

**삼성헬스 연동 규칙:**
- **별도 컨트롤러로 분리** (`/api/samsung-health/{도메인}`) — 직접 입력 API와 데이터 출처를 명확히 구분
- **List 일괄 수신** — 앱은 보통 마지막 동기화 이후 누적된 데이터를 한 번에 보냄. 1건씩 받으면 네트워크 비용 폭증
- **멱등성 보장** — 동일 timestamp의 재전송은 덮어쓰기. DynamoDB는 `putItem`이 자동으로 upsert이므로 자연스럽게 보장됨. RDS는 `(user_id, recorded_at)` 유니크 제약 + `INSERT ... ON DUPLICATE KEY UPDATE` 또는 `findBy → update` 패턴 사용
- **timestamp는 클라이언트 시각 사용** — 서버 수신 시각이 아니라 실제 측정 시각이 분석에 의미 있음
- **source 필드** — 향후 다른 헬스 플랫폼(Apple Health 등) 추가 시 데이터 출처 추적용
- **인증** — `@AuthenticationPrincipal`로 JWT에서 사용자 식별. 클라이언트가 `userId`를 보내도 신뢰하지 않음 (위변조 방지)

---

## 보안 설정 규칙

새 API 추가 시 `SecurityConfig.java`의 공개/보호 경로를 확인할 것:

- **공개 경로** (인증 불필요): `/api/auth/**`, `/actuator/health`, `/oauth2/**`, `/login/oauth2/**`
- **보호 경로** (기본): 나머지 모든 `/api/**`
- 인증 헤더: `Authorization: Bearer <JWT_TOKEN>`

새 공개 API가 있으면 `SecurityConfig`의 `permitAll()` 목록에 추가.

---

## 환경변수 규칙

- 민감 정보는 절대 소스코드에 하드코딩 금지
- `.env` 파일로 관리 (`spring-dotenv` 라이브러리)
- `application.yaml`에서 `${ENV_VAR_NAME}` 형식으로 참조
- `.env.example`에 키 이름과 예시값 추가

---

## 네이밍 컨벤션

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스명 | PascalCase | `BloodSugarRecord` |
| 메서드/변수 | camelCase | `bloodSugarLevel` |
| Enum 값 | UPPER_SNAKE_CASE | `BLOOD_SUGAR_HIGH` |
| URL 경로 | kebab-case | `/api/blood-sugar-records` |
| DynamoDB 테이블명 | snake_case | `blood_sugar_records` |
| 에러 메시지 | 한국어 | `"이미 존재하는 항목입니다."` |

---

## 코드 생성 체크리스트

### 공통 (모든 도메인)
- [ ] **저장소 결정**: 관계형 데이터/FK 필요 → RDS, 시계열 누적 데이터 → DynamoDB
- [ ] `controller/XxxController.java` — `/api/xxxs` 경로(kebab-case 복수형), `ResponseEntity<T>` 반환
- [ ] `dto/XxxRequest.java` — `@Getter @NoArgsConstructor` + `@Valid` 검증 어노테이션
- [ ] `dto/XxxResponse.java` — `@Getter @Builder` + `from()` 팩토리, 민감정보(password 등) 제외
- [ ] `SecurityConfig.java` — 공개 경로 여부 확인 (필요시 `permitAll()` 추가)
- [ ] 에러 메시지 한국어, 비즈니스 위반 시 `IllegalArgumentException`

### RDS 도메인 추가 시
- [ ] `domain/Xxx.java`
  - [ ] `@Entity` + `@Table(name = "snake_case")`
  - [ ] `@EntityListeners(AuditingEntityListener.class)` (메인 클래스 `@EnableJpaAuditing`과 짝)
  - [ ] `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@AllArgsConstructor` + `@Builder` + `@Getter`
  - [ ] PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` `Long id`
  - [ ] Enum: `@Enumerated(EnumType.STRING)`
  - [ ] 시간: `LocalDateTime` + `@CreatedDate` (Instant 아님)
  - [ ] 연관관계: `fetch = FetchType.LAZY` 명시
- [ ] `repository/XxxRepository.java` — `extends JpaRepository<Xxx, Long>`
- [ ] `service/XxxService.java`
  - [ ] 클래스에 `@Transactional(readOnly = true)`
  - [ ] 쓰기 메서드에 `@Transactional` 오버라이드
- [ ] DDL: 프로덕션은 `ddl-auto: validate`이므로 RDS에 테이블 미리 생성 또는 마이그레이션 도구 사용

### DynamoDB 도메인 추가 시
- [ ] `domain/Xxx.java`
  - [ ] Lombok 5종 (`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`)
  - [ ] JPA 어노테이션 절대 사용 금지
  - [ ] Partition Key: `userId` (String), Sort Key: timestamp 계열 (`Instant`)
  - [ ] Enum은 Entity 내부에 선언
- [ ] `repository/XxxRepository.java`
  - [ ] `DynamoDbTable<Xxx>` 주입
  - [ ] `save()`, `findOne()`, `findByUserIdBetween()`, `findLatestByUserId()` 등 시계열 쿼리 메서드
- [ ] `service/XxxService.java` — `@Transactional` 사용 안 함
- [ ] `config/DynamoDbConfig.java` — `StaticTableSchema` + `DynamoDbTable<Xxx>` Bean 추가
  - [ ] Partition Key 태그: `StaticAttributeTags.primaryPartitionKey()`
  - [ ] Sort Key 태그: `StaticAttributeTags.primarySortKey()`
  - [ ] Enum: `name()` / `valueOf()` 변환
  - [ ] Instant: `toString()` / `Instant.parse()` 변환
- [ ] `application.yaml` — `aws.dynamodb.tables.{도메인}` 키 추가
- [ ] `.env.example` — 새 환경변수 있으면 추가

### 삼성헬스 연동 도메인 추가 시 (위 체크 + 추가)
- [ ] `controller/SamsungHealthSyncController.java`에 엔드포인트 추가 (별도 컨트롤러 분리)
- [ ] List 일괄 수신 (`List<XxxSyncRequest>`)
- [ ] 멱등성 보장 (RDS는 유니크 제약, DynamoDB는 putItem 자동 upsert)
- [ ] timestamp는 클라이언트 측정 시각 사용
- [ ] `source = "SAMSUNG_HEALTH"` 필드 추가
