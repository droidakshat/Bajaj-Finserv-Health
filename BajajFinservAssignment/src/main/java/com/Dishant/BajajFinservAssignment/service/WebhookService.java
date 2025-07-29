package com.Dishant.BajajFinservAssignment.service;

import jakarta.annotation.PostConstruct;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebhookService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    private static final String NAME = "John Doe";
    private static final String REG_NO = "REG99999"; // Ends in 47 → Odd → Question 1
    private static final String EMAIL = "john@example.com";

    @PostConstruct
    public void startProcess() {
        try {
            // Step 1: Send request to generate webhook and token
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", NAME);
            requestBody.put("regNo", REG_NO);
            requestBody.put("email", EMAIL);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(INIT_URL, entity, Map.class);

            // Debug the raw response
            System.out.println("📥 RAW RESPONSE FROM INIT_URL:");
            System.out.println(response.getBody());

            Map<String, Object> responseBody = response.getBody();

            // Check if response contains nested "data" key
            String webhookUrl;
            String accessToken;

            if (responseBody.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                webhookUrl = (String) data.get("webhook");
                accessToken = (String) data.get("accessToken");
            } else {
                webhookUrl = (String) responseBody.get("webhook");
                accessToken = (String) responseBody.get("accessToken");
            }

            if (webhookUrl == null || accessToken == null) {
                throw new IllegalStateException("Webhook URL or Access Token is missing from the response.");
            }

            System.out.println("Webhook URL: " + webhookUrl);
            System.out.println("Access Token: " + accessToken);

            // Step 2: Choose final SQL query
            String finalQuery = getSQLQueryBasedOnReg(REG_NO);
            System.out.println(" Final SQL Query:\n" + finalQuery);

            // Step 3: Submit the answer
            submitAnswer(webhookUrl, accessToken, finalQuery);

        } catch (Exception e) {
            System.err.println("Error during startup task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getSQLQueryBasedOnReg(String regNo) {
        int lastDigit = Character.getNumericValue(regNo.charAt(regNo.length() - 1));
        boolean isOdd = lastDigit % 2 != 0;

        if (isOdd) {
            // ✅ SQL for Question 1
            return """
                SELECT p.AMOUNT AS SALARY,
                       CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,
                       TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,
                       d.DEPARTMENT_NAME
                FROM PAYMENTS p
                         JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID
                         JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID
                WHERE DAY(p.PAYMENT_TIME) != 1
                  AND p.AMOUNT = (
                    SELECT MAX(AMOUNT)
                    FROM PAYMENTS
                    WHERE DAY(PAYMENT_TIME) != 1
                  )
                LIMIT 1;
            """;
        } else {
            // ✅ SQL for Question 2
            return """
                SELECT
                    e1.EMP_ID,
                    e1.FIRST_NAME,
                    e1.LAST_NAME,
                    d.DEPARTMENT_NAME,
                    COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT
                FROM EMPLOYEE e1
                         JOIN DEPARTMENT d ON e1.DEPARTMENT = d.DEPARTMENT_ID
                         LEFT JOIN EMPLOYEE e2 ON e1.DEPARTMENT = e2.DEPARTMENT
                                                AND e2.DOB > e1.DOB
                GROUP BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME
                ORDER BY e1.EMP_ID DESC;
            """;
        }
    }

    private void submitAnswer(String webhookUrl, String token, String finalQuery) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token); // Sets Authorization: Bearer <token>

            Map<String, String> body = new HashMap<>();
            body.put("finalQuery", finalQuery);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            System.out.println("✅ Submission Response: " + response.getStatusCode());
            System.out.println("🔁 Response Body: " + response.getBody());
        } catch (Exception ex) {
            System.err.println("Failed to submit SQL answer: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
