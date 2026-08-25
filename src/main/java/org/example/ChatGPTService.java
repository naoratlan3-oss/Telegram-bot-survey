package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ChatGPTService {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public List<Question> generateSurvey(String topic, int numQuestions, int numOptions) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        // פרומפט מדוייק שמכריח את ChatGPT להחזיר פורמט JSON שקל לפרסר
        String prompt = String.format(
                "Create a JSON array of %d survey questions in Hebrew about '%s'. " +
                        "Each object must have 'question' (string) and 'options' (array of exactly %d strings). " +
                        "Return ONLY valid JSON array without any markdown formatting or extra text.",
                numQuestions, topic, numOptions
        );

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-3.5-turbo");

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);

        requestBody.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + Main.OPENAI_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API Error: " + response.statusCode());
        }

        // חילוץ התוכן והמרתו למבנה הנתונים שלנו
        JSONObject jsonResponse = new JSONObject(response.body());
        String content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

        // מנקה פורמט Markdown ש-ChatGPT לפעמים מוסיף
        if (content.startsWith("```json")) {
            content = content.replace("```json", "").replace("```", "").trim();
        }

        return parseQuestionsFromJson(content);
    }

    private List<Question> parseQuestionsFromJson(String jsonContent) {
        List<Question> questions = new ArrayList<>();
        JSONArray jsonArray = new JSONArray(jsonContent);

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject qObj = jsonArray.getJSONObject(i);
            String qText = qObj.getString("question");

            JSONArray optArray = qObj.getJSONArray("options");
            List<String> options = new ArrayList<>();
            for (int j = 0; j < optArray.length(); j++) {
                options.add(optArray.getString(j));
            }
            questions.add(new Question(qText, options));
        }
        return questions;
    }
}