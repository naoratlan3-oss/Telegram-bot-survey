package org.example;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.concurrent.*;

public class SurveyTimerManager {
    private final SurveyTelegramBot bot;
    private final SurveySession session;
    private final int surveyDurationMinutes;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> activeTask;

    private int remainingSeconds;
    private boolean isPaused = false;
    private boolean isWaitingForEarlyClose = false; // דגל להמתנה של 20 שניות כשכולם ענו

    public SurveyTimerManager(SurveyTelegramBot bot, SurveySession session, int surveyDurationMinutes) {
        this.bot = bot;
        this.session = session;
        this.surveyDurationMinutes = surveyDurationMinutes;
        this.remainingSeconds = surveyDurationMinutes * 60;
    }

    public void startSurveyTimers() {
        activeTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isPaused && session.isActive()) {

                // בדיקה האם כל המשתתפים ענו כבר על הסקר
                if (!isWaitingForEarlyClose && areAllParticipantsCompleted()) {
                    isWaitingForEarlyClose = true;
                    remainingSeconds = 20; // אם כולם ענו, מקצרים את הזמן ל-20 שניות אחרונות כפי שביקשת
                }

                remainingSeconds--;

                // שליחת תזכורת ב-2 דקות אחרונות (אם הזמן המקורי היה לפחות 3 דקות)
                if (remainingSeconds == 120 && surveyDurationMinutes >= 3 && !isWaitingForEarlyClose) {
                    sendReminderToIncompleteUsers();
                }

                // סיום הזמן או תום 20 השניות לאחר שכולם ענו
                if (remainingSeconds <= 0) {
                    session.closeSurveyEarly();
                    stop();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private boolean areAllParticipantsCompleted() {
        int totalPart = session.getProgressMap().size();
        if (totalPart == 0) return false;
        int completedCount = 0;
        for (ParticipantProgress p : session.getProgressMap().values()) {
            if (p.getCompletedQuestionsCount() == session.getQuestions().size()) {
                completedCount++;
            }
        }
        return completedCount == totalPart;
    }

    public synchronized void pauseTimer() {
        isPaused = true;
    }

    public synchronized void resumeTimer() {
        isPaused = false;
    }

    public synchronized void stop() {
        if (activeTask != null) {
            activeTask.cancel(true);
        }
        scheduler.shutdown();
    }

    private void sendReminderToIncompleteUsers() {
        int totalQuestionsCount = session.getQuestions().size();
        for (ParticipantProgress p : session.getProgressMap().values()) {
            if (p.getCompletedQuestionsCount() < totalQuestionsCount) {
                try {
                    String reminderText = "⏳ נותרו עוד 2 דקות לסיום הסקר!\nנשמח לשמוע את דעתך על השאלות שטרם ענית עליהן 🎯";
                    bot.execute(new SendMessage(p.getUserId().toString(), reminderText));
                } catch (TelegramApiException ignored) {}
            }
        }
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public boolean isPaused() {
        return isPaused;
    }
}