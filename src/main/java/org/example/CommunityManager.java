package org.example;

import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommunityManager {
    // מפתח: ChatID, ערך: פרטי המשתמש (שם, @username, שעת הצטרפות)
    private final Map<Long, UserInfo> users = new ConcurrentHashMap<>();
    private final List<Runnable> uiListeners = new ArrayList<>();
    private final File dataFile = new File("community_users_v2.txt");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public static class UserInfo {
        public String name;
        public String username;
        public String joinTime;

        public UserInfo(String name, String username, String joinTime) {
            this.name = name;
            this.username = username != null ? username : "אין";
            this.joinTime = joinTime;
        }
    }

    public CommunityManager() {
        loadUsers();
    }

    public synchronized boolean addNewUser(Long chatId, String name, String username) {
        if (!users.containsKey(chatId)) {
            String joinTime = LocalTime.now().format(timeFormatter);
            users.put(chatId, new UserInfo(name, username, joinTime));
            saveUsers();
            notifyUI();
            return true;
        }
        return false;
    }

    public int getCommunitySize() { return users.size(); }
    public Map<Long, UserInfo> getAllUsers() { return users; }

    public void addUIListener(Runnable listener) { uiListeners.add(listener); }
    private void notifyUI() { for (Runnable r : uiListeners) r.run(); }

    private void loadUsers() {
        if (!dataFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("___");
                if (p.length >= 3) {
                    users.put(Long.parseLong(p[0]), new UserInfo(p[1], p[2], p[3]));
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(dataFile))) {
            for (Map.Entry<Long, UserInfo> entry : users.entrySet()) {
                UserInfo u = entry.getValue();
                writer.println(entry.getKey() + "___" + u.name + "___" + u.username + "___" + u.joinTime);
            }
        } catch (Exception ignored) {}
    }
}