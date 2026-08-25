package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParticipantProgress {
    private final Long userId;
    private final Map<Integer, Integer> answers;
    private int currentQuestionIndex; // עוקב אחרי השאלה שמוצגת כרגע למשתמש

    public ParticipantProgress(Long userId) {
        this.userId = userId;
        this.answers = new ConcurrentHashMap<>();
        this.currentQuestionIndex = 0; // תמיד מתחילים בשאלה הראשונה (אינדקס 0)
    }

    public Long getUserId() {
        return userId;
    }

    public synchronized boolean recordAnswer(int questionId, int optionIndex) {
        if (answers.containsKey(questionId)) {
            return false; // המשתמש כבר ענה על השאלה הזו
        }
        answers.put(questionId, optionIndex);
        return true;
    }

    public synchronized int getCompletedQuestionsCount() {
        return answers.size();
    }

    public Map<Integer, Integer> getAnswers() {
        return answers;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void incrementQuestionIndex() {
        this.currentQuestionIndex++;
    }
}