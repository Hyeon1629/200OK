package com.checkdang.controller;

import com.checkdang.domain.Diet;
import com.checkdang.domain.Exercise;
import com.checkdang.domain.Sleep;
import com.checkdang.domain.User;
import com.checkdang.repository.DietRepository;
import com.checkdang.repository.ExerciseRepository;
import com.checkdang.repository.SleepRepository;
import com.checkdang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai/reports")
@RequiredArgsConstructor
public class GeminiReportController {

    private static final int MAX_ROWS_PER_SECTION = 30;
    private static final Pattern GEMINI_TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final UserRepository userRepository;
    private final DietRepository dietRepository;
    private final SleepRepository sleepRepository;
    private final ExerciseRepository exerciseRepository;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${GEMINI_MODEL:gemini-1.5-flash}")
    private String geminiModel;

    @GetMapping("/health")
    public ResponseEntity<AiHealthReportResponse> getHealthReport(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime reportTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime reportFrom = from == null ? reportTo.minusDays(Math.max(1, Math.min(days, 30))) : from;

        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        List<Diet> diets = dietRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(user.getId(), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Sleep> sleeps = sleepRepository
                .findWithStagesByUserIdAndRange(user.getId(), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();
        List<Exercise> exercises = exerciseRepository
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(user.getId(), reportFrom, reportTo)
                .stream()
                .limit(MAX_ROWS_PER_SECTION)
                .toList();

        String prompt = buildPrompt(user, reportFrom, reportTo, diets, sleeps, exercises);
        String report = requestGeminiReport(prompt);

        return ResponseEntity.ok(new AiHealthReportResponse(
                reportFrom,
                reportTo,
                new AiReportSourceCount(diets.size(), sleeps.size(), exercises.size()),
                report
        ));
    }

    private String buildPrompt(
            User user,
            LocalDateTime from,
            LocalDateTime to,
            List<Diet> diets,
            List<Sleep> sleeps,
            List<Exercise> exercises) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are the health data analysis assistant for CheckDang, a diabetes and lifestyle management app.
                Write the report in Korean Markdown for direct frontend rendering.
                Use only the database values below as evidence.
                Do not provide a definitive medical diagnosis. If there are warning signs, recommend consulting a medical professional.

                Follow this exact structure:
                ## Summary
                - 2 or 3 key changes
                ## Diet Analysis
                - Analyze carbohydrates, sugar, calories, protein, sodium, and meal timing
                ## Sleep Analysis
                - Analyze sleep duration and quality
                ## Exercise Analysis
                - Analyze exercise volume and recovery
                ## Recommended Actions
                - 3 concrete actions the user can try today

                """);

        prompt.append("User: ").append(nullToDash(user.getName())).append(" / ").append(nullToDash(user.getEmail())).append("\n");
        prompt.append("Analysis period: ").append(from).append(" ~ ").append(to).append("\n\n");

        prompt.append("[Diet records]\n");
        if (diets.isEmpty()) {
            prompt.append("- none\n");
        } else {
            diets.forEach(diet -> prompt.append("- ")
                    .append(diet.getRecordedAt()).append(", ")
                    .append(diet.getMealType()).append(", ")
                    .append(nullToDash(diet.getFoodName()))
                    .append(", calories=").append(nullToDash(diet.getCalories()))
                    .append(", carbohydrate=").append(nullToDash(diet.getCarbohydrate()))
                    .append(", protein=").append(nullToDash(diet.getProtein()))
                    .append(", sugar=").append(nullToDash(diet.getSugar()))
                    .append(", sodium=").append(nullToDash(diet.getSodium()))
                    .append("\n"));
        }

        prompt.append("\n[Sleep records]\n");
        if (sleeps.isEmpty()) {
            prompt.append("- none\n");
        } else {
            sleeps.forEach(sleep -> prompt.append("- ")
                    .append("sleepTime=").append(sleep.getSleepTime())
                    .append(", wakeTime=").append(sleep.getWakeTime())
                    .append(", durationMinutes=").append(nullToDash(sleep.getDuration()))
                    .append(", quality=").append(nullToDash(sleep.getQuality()))
                    .append("\n"));
        }

        prompt.append("\n[Exercise records]\n");
        if (exercises.isEmpty()) {
            prompt.append("- none\n");
        } else {
            exercises.forEach(exercise -> prompt.append("- ")
                    .append(exercise.getRecordedAt()).append(", ")
                    .append(nullToDash(exercise.getExerciseName()))
                    .append(", durationMinutes=").append(nullToDash(exercise.getDuration()))
                    .append(", sets=").append(nullToDash(exercise.getSets()))
                    .append(", reps=").append(nullToDash(exercise.getReps()))
                    .append(", weightKg=").append(nullToDash(exercise.getWeightKg()))
                    .append("\n"));
        }

        return prompt.toString();
    }

    private String requestGeminiReport(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured.");
        }

        String encodedModel = URLEncoder.encode(geminiModel, StandardCharsets.UTF_8);
        String encodedKey = URLEncoder.encode(geminiApiKey, StandardCharsets.UTF_8);
        URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + encodedModel + ":generateContent?key=" + encodedKey);

        String body = """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        { "text": "%s" }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.4,
                    "maxOutputTokens": 1200
                  }
                }
                """.formatted(jsonEscape(prompt));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini API request failed: HTTP "
                        + response.statusCode() + " / " + response.body());
            }

            String text = extractGeminiText(response.body());
            if (text.isBlank()) {
                throw new IllegalStateException("Gemini API response did not include report text.");
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("Gemini API connection failed.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini API request was interrupted.", e);
        }
    }

    private String extractGeminiText(String responseBody) {
        Matcher matcher = GEMINI_TEXT_PATTERN.matcher(responseBody);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            text.append(jsonUnescape(matcher.group(1)));
        }
        return text.toString().trim();
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String jsonUnescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                result.append(current);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> result.append(next);
            }
        }
        return result.toString();
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    public record AiHealthReportResponse(
            LocalDateTime from,
            LocalDateTime to,
            AiReportSourceCount sourceCount,
            String report
    ) {
    }

    public record AiReportSourceCount(
            int diets,
            int sleeps,
            int exercises
    ) {
    }
}
