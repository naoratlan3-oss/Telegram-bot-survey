package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import javax.swing.SwingUtilities;

public class Main {
    // השתמש בטוקנים האמיתיים שלך כאן
    public static final String BOT_TOKEN = "8931899102:AAHhau9i7PkK0Lfw9SbrVBHKgYp4w0B0rw8";
    public static final String BOT_USERNAME = "@NaorTheBot";
    public static final String OPENAI_API_KEY = "YOUR_OPENAI_API_KEY";

    public static void main(String[] args) {
        try {
            // 1. יצירת מנהל הקהילה (ה-State המרכזי)
            CommunityManager communityManager = new CommunityManager();

            // 2. הפעלת בוט הטלגרם ב-Thread נפרד
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SurveyTelegramBot bot = new SurveyTelegramBot(communityManager);
            botsApi.registerBot(bot);
            System.out.println("Bot is up and running!");

            // 3. הפעלת ממשק המשתמש (Swing) ב-Thread הבטוח שלו
            SwingUtilities.invokeLater(() -> {
                MainUI ui = new MainUI(communityManager, bot);
                ui.setVisible(true);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}