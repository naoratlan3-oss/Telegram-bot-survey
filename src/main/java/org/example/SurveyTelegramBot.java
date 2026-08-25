package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SurveyTelegramBot extends TelegramLongPollingBot {

    private final CommunityManager communityManager;
    private SurveySession currentSurveySession;

    public SurveyTelegramBot(CommunityManager communityManager) {
        this.communityManager = communityManager;
    }

    @Override
    public String getBotUsername() { return Main.BOT_USERNAME; }
    @Override
    public String getBotToken() { return Main.BOT_TOKEN; }

    public void setCurrentSurveySession(SurveySession session) {
        this.currentSurveySession = session;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Update update) {
        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String name = update.getMessage().getFrom().getFirstName();
        String username = update.getMessage().getFrom().getUserName();
        if (username != null) username = "@" + username;

        if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("היי") || text.equalsIgnoreCase("hi")) {
            if (communityManager.addNewUser(chatId, name, username)) {
                int totalMembers = communityManager.getCommunitySize();
                sendMessageSafe(chatId, "ברוך הבא לקהילה שלנו! 🎉\nכרגע אנחנו " + totalMembers + " חברים.");
                broadcastNewMember(name, totalMembers, chatId);
            } else {
                sendMessageSafe(chatId, "אהלן " + name + "! אתה כבר חבר רשום בקהילה שלנו 😉");
            }
        }
    }

    public void sendSurveyToParticipants(SurveySession session) {
        List<Question> questions = session.getQuestions();
        if (questions.isEmpty()) return;
        Question firstQuestion = questions.get(0);

        for (Long userId : session.getProgressMap().keySet()) {
            SendMessage message = new SendMessage(userId.toString(), "📊 *" + firstQuestion.getText() + "*\n\n(שאלה 1 מתוך " + questions.size() + ")");
            message.setParseMode("Markdown");
            message.setReplyMarkup(createKeyboard(0, firstQuestion, -1));
            try { execute(message); } catch (Exception ignored) {}
        }
    }

    private InlineKeyboardMarkup createKeyboard(int questionIndex, Question q, int selectedOption) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int optIndex = 0; optIndex < q.getOptions().size(); optIndex++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String btnText = (optIndex == selectedOption) ? "✅ " + q.getOptions().get(optIndex) : q.getOptions().get(optIndex);

            InlineKeyboardButton button = new InlineKeyboardButton(btnText);
            button.setCallbackData((selectedOption != -1) ? "IGNORE" : "Q_" + questionIndex + "_A_" + optIndex);
            row.add(button);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (callData.startsWith("Q_")) {
            if (currentSurveySession == null || !currentSurveySession.isActive()) {
                sendToastMessage(callbackQuery.getId(), "הסקר אינו פעיל כרגע ⏱️");
                return;
            }

            String[] parts = callData.split("_");
            int questionIndex = Integer.parseInt(parts[1]);
            int optionIndex = Integer.parseInt(parts[3]);

            ParticipantProgress progress = currentSurveySession.getProgressMap().get(chatId);

            if (progress != null && progress.getCurrentQuestionIndex() == questionIndex) {
                boolean success = currentSurveySession.processAnswer(chatId, questionIndex, optionIndex);

                if (success) {
                    sendToastMessage(callbackQuery.getId(), "תשובתך נקלטה! ✅");

                    Question currentQ = currentSurveySession.getQuestions().get(questionIndex);
                    EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
                    editMarkup.setChatId(chatId.toString());
                    editMarkup.setMessageId(messageId);
                    editMarkup.setReplyMarkup(createKeyboard(questionIndex, currentQ, optionIndex));

                    try { execute(editMarkup); } catch (Exception ignored) {}

                    new Thread(() -> {
                        try {
                            Thread.sleep(600);
                            int nextIndex = progress.getCurrentQuestionIndex();
                            List<Question> questions = currentSurveySession.getQuestions();

                            EditMessageText editMessage = new EditMessageText();
                            editMessage.setChatId(chatId.toString());
                            editMessage.setMessageId(messageId);
                            editMessage.setParseMode("Markdown");

                            if (nextIndex < questions.size()) {
                                Question nextQ = questions.get(nextIndex);
                                editMessage.setText("📊 *" + nextQ.getText() + "*\n\n(שאלה " + (nextIndex + 1) + " מתוך " + questions.size() + ")");
                                editMessage.setReplyMarkup(createKeyboard(nextIndex, nextQ, -1));
                            } else {
                                editMessage.setText("🎉 *סיימת את כל שאלות הסקר!* תודה רבה על השתתפותך.");
                            }
                            execute(editMessage);
                        } catch (Exception ignored) {}
                    }).start();
                }
            }
        }
    }

    public void broadcastMessageToParticipants(SurveySession session, String text) {
        for (Long userId : session.getProgressMap().keySet()) {
            sendMessageSafe(userId, text);
        }
    }

    public void broadcastSurveyResults(SurveySession session) {
        StringBuilder sb = new StringBuilder("📊 *תוצאות הסקר הסופיות* 📊\n───────────────────\n\n");
        List<Question> questions = session.getQuestions();
        Map<Long, ParticipantProgress> progressMap = session.getProgressMap();

        for (int qIndex = 0; qIndex < questions.size(); qIndex++) {
            Question q = questions.get(qIndex);
            sb.append("❓ *שאלה ").append(qIndex + 1).append(":* ").append(q.getText()).append("\n");

            int[] votes = new int[q.getOptions().size()];
            int totalVotes = 0;

            for (ParticipantProgress p : progressMap.values()) {
                Integer chosen = p.getAnswers().get(qIndex);
                if (chosen != null) { votes[chosen]++; totalVotes++; }
            }

            for (int i = 0; i < q.getOptions().size(); i++) {
                double pct = (totalVotes == 0) ? 0 : ((double) votes[i] / totalVotes) * 100;
                int bars = (int) Math.round(pct / 10);
                StringBuilder bar = new StringBuilder();
                for (int b = 0; b < 10; b++) bar.append(b < bars ? "█" : "░");

                sb.append("  ").append(q.getOptions().get(i)).append("\n");
                sb.append("  `").append(bar).append("` *").append(String.format("%.1f", pct)).append("%* (").append(votes[i]).append(")\n");
            }
            sb.append("\n───────────────────\n\n");
        }

        for (Long userId : progressMap.keySet()) {
            SendMessage msg = new SendMessage(userId.toString(), sb.toString());
            msg.setParseMode("Markdown");
            try { execute(msg); } catch (Exception ignored) {}
        }
    }

    private void broadcastNewMember(String name, int total, Long exclude) {
        String msg = "👋 חבר חדש הצטרף: " + name + "! אנחנו עכשיו " + total + " חברים בקהילה.";
        for (Long id : communityManager.getAllUsers().keySet()) {
            if (!id.equals(exclude)) sendMessageSafe(id, msg);
        }
    }

    private void sendToastMessage(String id, String text) {
        AnswerCallbackQuery ans = new AnswerCallbackQuery();
        ans.setCallbackQueryId(id);
        ans.setText(text);
        ans.setShowAlert(false);
        try { execute(ans); } catch (Exception ignored) {}
    }

    public void sendMessageSafe(Long chatId, String text) {
        try { execute(new SendMessage(chatId.toString(), text)); } catch (Exception ignored) {}
    }
}