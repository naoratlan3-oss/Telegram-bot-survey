package org.example;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.*;
import java.util.List;

public class MainUI extends JFrame {
    private final CommunityManager communityManager;
    private final SurveyTelegramBot bot;

    // טבלת קהילה חיה לפי דרישות האפיון
    private final DefaultTableModel communityTableModel;
    private final JLabel lblTotalMembers;

    private final JSpinner minParticipantsSpinner;
    private final JTextField delayField;
    private final JTextField durationField;
    private SurveySession currentSession;
    private SurveyTimerManager timerManager;

    public MainUI(CommunityManager communityManager, SurveyTelegramBot bot) {
        this.communityManager = communityManager;
        this.bot = bot;

        setTitle(" מערכת ניהול סקרים מקצועית - Enterprise Edition");
        setSize(1050, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- פאנל קהילה חי (שמאל) עם JTable מפורט ---
        JPanel communityPanel = new JPanel(new BorderLayout());
        communityPanel.setBorder(BorderFactory.createTitledBorder("ניהול חברי הקהילה (Live)"));

        communityTableModel = new DefaultTableModel(new String[]{"שם משתמש", "Telegram Username", "מועד הצטרפות"}, 0);
        JTable communityTable = new JTable(communityTableModel);
        communityPanel.add(new JScrollPane(communityTable), BorderLayout.CENTER);

        lblTotalMembers = new JLabel("סה״כ חברי קהילה: 0");
        lblTotalMembers.setFont(new Font("Arial", Font.BOLD, 14));
        lblTotalMembers.setForeground(new Color(41, 128, 185));
        communityPanel.add(lblTotalMembers, BorderLayout.SOUTH);
        add(communityPanel, BorderLayout.WEST);

        communityManager.addUIListener(() -> SwingUtilities.invokeLater(this::updateCommunityTable));

        // --- פאנל שליטה מרכזי (מרכז) ---
        JPanel controlPanel = new JPanel();
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        delayField = new JTextField("0", 3);
        applyNumberFilter(delayField);

        durationField = new JTextField("5", 3);
        applyNumberFilter(durationField);

        minParticipantsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 1000, 1)); // מינימום 3 לפי האפיון!

        JButton openCreatorBtn = new JButton("פתח מחולל סקרים חדש 📝");
        openCreatorBtn.setFont(new Font("Arial", Font.BOLD, 16));
        openCreatorBtn.setBackground(new Color(39, 174, 96));
        openCreatorBtn.setForeground(Color.WHITE);

        openCreatorBtn.addActionListener(e -> {
            int minRequired = (Integer) minParticipantsSpinner.getValue();
            // בדיקת תנאי סף נוקשה מהאפיון (< 3 חברים)
            if (communityManager.getCommunitySize() < minRequired) {
                JOptionPane.showMessageDialog(this, "שגיאה: נדרשים לפחות " + minRequired + " חברים בקהילה כדי להתחיל סקר!", "תנאי סף לא הושג", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (delayField.getText().isEmpty() || durationField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this, "נא למלא את שדות הזמנים כראוי.", "שגיאה", JOptionPane.WARNING_MESSAGE);
                return;
            }
            openSurveyCreatorDialog();
        });

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        inputPanel.add(new JLabel("השהיה לפני פרסום (דקות):"));
        inputPanel.add(delayField);
        inputPanel.add(new JLabel("משך מענה לסקר (דקות):"));
        inputPanel.add(durationField);
        inputPanel.add(new JLabel("מינימום חברים לסקרי:"));
        inputPanel.add(minParticipantsSpinner);

        controlPanel.add(inputPanel);
        controlPanel.add(Box.createVerticalStrut(25));
        controlPanel.add(openCreatorBtn);
        add(controlPanel, BorderLayout.CENTER);

        updateCommunityTable();
    }

    private void updateCommunityTable() {
        communityTableModel.setRowCount(0);
        communityManager.getAllUsers().forEach((id, info) -> {
            communityTableModel.addRow(new Object[]{info.name, info.username, info.joinTime});
        });
        lblTotalMembers.setText("סה״כ חברי קהילה: " + communityManager.getCommunitySize());
    }

    private void applyNumberFilter(JTextField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string.matches("\\d+")) super.insertString(fb, offset, string, attr);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text.matches("\\d+")) super.replace(fb, offset, length, text, attrs);
            }
        });
    }

    class QuestionPanel extends JPanel {
        private final JTextField qField;
        private final JPanel optionsGrid;
        private final List<JTextField> optFields = new ArrayList<>();

        public QuestionPanel(int questionIndex) {
            setLayout(new BorderLayout(5, 5));
            setBorder(BorderFactory.createTitledBorder("שאלה " + questionIndex));

            JPanel topPanel = new JPanel(new BorderLayout());
            qField = new JTextField();

            JComboBox<Integer> numOptionsCombo = new JComboBox<>(new Integer[]{2, 3, 4});
            numOptionsCombo.addActionListener(e -> rebuildOptions((Integer) numOptionsCombo.getSelectedItem()));

            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            controlPanel.add(new JLabel("מספר תשובות:"));
            controlPanel.add(numOptionsCombo);

            topPanel.add(qField, BorderLayout.CENTER);
            topPanel.add(controlPanel, BorderLayout.EAST);
            add(topPanel, BorderLayout.NORTH);

            optionsGrid = new JPanel();
            optionsGrid.setLayout(new BoxLayout(optionsGrid, BoxLayout.Y_AXIS));
            add(optionsGrid, BorderLayout.CENTER);
            rebuildOptions(2);
        }

        private void rebuildOptions(int count) {
            optionsGrid.removeAll();
            optFields.clear();
            for (int j = 1; j <= count; j++) {
                JPanel optRow = new JPanel(new BorderLayout());
                optRow.add(new JLabel("תשובה " + j + ": "), BorderLayout.WEST);
                JTextField optField = new JTextField();
                optFields.add(optField);
                optRow.add(optField, BorderLayout.CENTER);
                optionsGrid.add(optRow);
            }
            revalidate();
            repaint();
        }

        public Question getQuestionData() {
            String qText = qField.getText().trim();
            if (qText.isEmpty()) return null;
            List<String> options = new ArrayList<>();
            for (JTextField optF : optFields) {
                String oText = optF.getText().trim();
                if (oText.isEmpty()) return null;
                options.add(oText);
            }
            return new Question(qText, options);
        }
    }

    private void openSurveyCreatorDialog() {
        JDialog dialog = new JDialog(this, "מחולל סקרים מתקדם", true);
        dialog.setSize(650, 750);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationRelativeTo(this);

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JComboBox<Integer> numQuestionsCombo = new JComboBox<>(new Integer[]{1, 2, 3}); // 1 עד 3 שאלות לפי אפיון
        configPanel.add(new JLabel("מספר שאלות בסקר:"));
        configPanel.add(numQuestionsCombo);

        JPanel questionsContainer = new JPanel();
        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(questionsContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        List<QuestionPanel> activeQuestionPanels = new ArrayList<>();

        Runnable rebuildQuestions = () -> {
            questionsContainer.removeAll();
            activeQuestionPanels.clear();
            int qCount = (Integer) numQuestionsCombo.getSelectedItem();
            for (int i = 1; i <= qCount; i++) {
                QuestionPanel qp = new QuestionPanel(i);
                activeQuestionPanels.add(qp);
                questionsContainer.add(qp);
            }
            questionsContainer.revalidate();
            questionsContainer.repaint();
        };

        numQuestionsCombo.addActionListener(e -> rebuildQuestions.run());
        rebuildQuestions.run();

        JButton submitBtn = new JButton("שלח סקר לקהילה 🚀");
        submitBtn.setBackground(new Color(46, 204, 113));
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFont(new Font("Arial", Font.BOLD, 16));

        submitBtn.addActionListener(e -> {
            List<Question> finalQuestions = new ArrayList<>();
            for (QuestionPanel qp : activeQuestionPanels) {
                Question q = qp.getQuestionData();
                if (q == null) {
                    JOptionPane.showMessageDialog(dialog, "נא למלא את כל השאלות והתשובות!", "שגיאה", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                finalQuestions.add(q);
            }

            int delayMin = Integer.parseInt(delayField.getText());
            int durationMin = Integer.parseInt(durationField.getText());

            currentSession = new SurveySession(
                    communityManager.getCommunitySize(),
                    finalQuestions,
                    () -> System.out.println("תזכורת נשלחה!"),
                    () -> {
                        SwingUtilities.invokeLater(this::showResultsDialog);
                        bot.broadcastSurveyResults(currentSession);
                    }
            );

            communityManager.getAllUsers().keySet().forEach(currentSession::initParticipant);
            dialog.dispose();

            if (delayMin > 0) {
                showPrePublishControlDialog(delayMin, durationMin);
            } else {
                startActiveSurvey(durationMin);
                showActiveSurveyMonitorDialog();
            }
        });

        dialog.add(configPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(submitBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showPrePublishControlDialog(int delayMinutes, int durationMinutes) {
        JDialog controlDialog = new JDialog(this, "המתנה לפרסום הסקר", true);
        controlDialog.setSize(400, 220);
        controlDialog.setLayout(new BorderLayout(10, 10));
        controlDialog.setLocationRelativeTo(this);
        controlDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JLabel timerLabel = new JLabel("", JLabel.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 22));
        timerLabel.setForeground(new Color(192, 57, 43));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton pauseResumeBtn = new JButton("השהה ⏸️");
        JButton cancelBtn = new JButton("בטל סקר ❌");
        cancelBtn.setBackground(new Color(231, 76, 60));
        cancelBtn.setForeground(Color.WHITE);

        btnPanel.add(pauseResumeBtn);
        btnPanel.add(cancelBtn);

        controlDialog.add(new JLabel("הסקר יפורסם בעוד:", JLabel.CENTER), BorderLayout.NORTH);
        controlDialog.add(timerLabel, BorderLayout.CENTER);
        controlDialog.add(btnPanel, BorderLayout.SOUTH);

        final int[] totalSecs = {delayMinutes * 60};
        final boolean[] isPaused = {false};

        javax.swing.Timer uiTimer = new javax.swing.Timer(1000, null);
        uiTimer.addActionListener(e -> {
            if (!isPaused[0]) {
                totalSecs[0]--;
                int m = totalSecs[0] / 60;
                int s = totalSecs[0] % 60;
                timerLabel.setText(String.format("%02d:%02d", m, s));

                if (totalSecs[0] <= 0) {
                    uiTimer.stop();
                    controlDialog.dispose();
                    startActiveSurvey(durationMinutes);
                    showActiveSurveyMonitorDialog();
                }
            }
        });

        pauseResumeBtn.addActionListener(e -> {
            isPaused[0] = !isPaused[0];
            if (isPaused[0]) {
                pauseResumeBtn.setText("המשך ▶️");
                timerLabel.setForeground(new Color(243, 156, 18));
            } else {
                pauseResumeBtn.setText("השהה ⏸️");
                timerLabel.setForeground(new Color(192, 57, 43));
            }
        });

        cancelBtn.addActionListener(e -> {
            uiTimer.stop();
            controlDialog.dispose();
            JOptionPane.showMessageDialog(this, "הסקר בוטל בהצלחה.", "בוטל", JOptionPane.INFORMATION_MESSAGE);
        });

        uiTimer.start();
        controlDialog.setVisible(true);
    }

    // ==========================================
    // חלון בקרה חי לסקר פעיל (כולל מעקב סטטוסים מדויק: "טרם ענה", "בתהליך", "השלים")
    // ==========================================
    private void showActiveSurveyMonitorDialog() {
        JDialog monitorDialog = new JDialog(this, "ניהול סקר פעיל בזמן אמת", true);
        monitorDialog.setSize(650, 550);
        monitorDialog.setLayout(new BorderLayout(10, 10));
        monitorDialog.setLocationRelativeTo(this);
        monitorDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JLabel redTimerLabel = new JLabel("00:00", JLabel.CENTER);
        redTimerLabel.setFont(new Font("Arial", Font.BOLD, 30));
        redTimerLabel.setForeground(Color.RED);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("⏱️ ספירה לאחור לסיום הסקר"));
        topPanel.add(redTimerLabel, BorderLayout.CENTER);

        JTextArea liveLogArea = new JTextArea();
        liveLogArea.setEditable(false);
        liveLogArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(liveLogArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("סטטוס התקדמות המשתתפים (חי)"));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton pauseResumeActiveBtn = new JButton("השהה סקר ⏸️");
        JButton cancelActiveBtn = new JButton("בטל סקר ושלח הודעה לקהילה ❌");
        cancelActiveBtn.setBackground(new Color(231, 76, 60));
        cancelActiveBtn.setForeground(Color.WHITE);

        actionPanel.add(pauseResumeActiveBtn);
        actionPanel.add(cancelActiveBtn);

        monitorDialog.add(topPanel, BorderLayout.NORTH);
        monitorDialog.add(scrollPane, BorderLayout.CENTER);
        monitorDialog.add(actionPanel, BorderLayout.SOUTH);

        Timer monitorTimer = new Timer(1000, null);
        monitorTimer.addActionListener(e -> {
            if (timerManager != null) {
                int secs = timerManager.getRemainingSeconds();
                int m = secs / 60;
                int s = secs % 60;
                redTimerLabel.setText(String.format("%02d:%02d", m, s));

                StringBuilder logBuilder = new StringBuilder();
                Map<Long, CommunityManager.UserInfo> allUsers = communityManager.getAllUsers();
                Map<Long, ParticipantProgress> progressMap = currentSession.getProgressMap();
                List<Question> questions = currentSession.getQuestions();
                int totalQ = questions.size();

                for (Map.Entry<Long, CommunityManager.UserInfo> entry : allUsers.entrySet()) {
                    Long uId = entry.getKey();
                    String uName =  entry.getValue().name;
                    CommunityManager.UserInfo uInfo = entry.getValue();
                    ParticipantProgress prog = progressMap.get(uId);

                    logBuilder.append("👤 משתמש: ").append(uInfo.name).append(" (").append(uInfo.username).append(")\n");
                    if (prog == null) {
                        logBuilder.append("   ⚪ אינו משתתף בסקר זה (הצטרף לאחר תחילתו)\n");
                    } else {
                        int completed = prog.getCompletedQuestionsCount();
                        String status;
                        if (completed == 0) status = "טרם ענה";
                        else if (completed < totalQ) status = "בתהליך";
                        else status = "השלים";

                        logBuilder.append("   📌 התקדמות: ").append(completed).append("/").append(totalQ)
                                .append(" | סטטוס: [ ").append(status).append(" ]\n");
                    }
                    logBuilder.append("──────────────────────────────────────────\n");
                }
                liveLogArea.setText(logBuilder.toString());

                if (!currentSession.isActive()) {
                    monitorTimer.stop();
                    monitorDialog.dispose();
                    showResultsDialog();
                }
            }
        });

        // השהיה/המשך סקר פעיל + עדכון חברי הקהילה בטלגרם
        pauseResumeActiveBtn.addActionListener(e -> {
            if (timerManager.isPaused()) {
                timerManager.resumeTimer();
                currentSession.startTimers();
                pauseResumeActiveBtn.setText("השהה סקר ⏸️");
                redTimerLabel.setForeground(Color.RED);
                bot.broadcastMessageToParticipants(currentSession, "▶️ הסקר חזר לפעילות! ניתן להמשיך לענות.");
            } else {
                timerManager.pauseTimer();
                pauseResumeActiveBtn.setText("המשך סקר ▶️");
                redTimerLabel.setForeground(new Color(243, 156, 18));
                bot.broadcastMessageToParticipants(currentSession, "⏸️ הסקר הושהה זמנית על ידי המנהל. נמשיך בקרוב.");
            }
        });

        // ביטול סקר פעיל + הודעת ביטול למשתמשים והצגת תוצאות למנהל בלבד
        cancelActiveBtn.addActionListener(e -> {
            monitorTimer.stop();
            if (timerManager != null) timerManager.stop();
            currentSession.closeSurveyEarly();
            monitorDialog.dispose();

            bot.broadcastMessageToParticipants(currentSession, "❌ הסקר בוטל על ידי המנהל, עמכם הסליחה.");
            JOptionPane.showMessageDialog(this, "הסקר בוטל בהצלחה. להלן התוצאות החלקיות:", "בוטל", JOptionPane.WARNING_MESSAGE);
            showResultsDialog();
        });

        monitorTimer.start();
        monitorDialog.setVisible(true);
    }

    private void startActiveSurvey(int durationMinutes) {
        timerManager = new SurveyTimerManager(bot, currentSession, durationMinutes);
        currentSession.startTimers();
        timerManager.startSurveyTimers();
        bot.setCurrentSurveySession(currentSession);
        bot.sendSurveyToParticipants(currentSession);
    }

    private void showResultsDialog() {
        if (currentSession == null) return;

        JDialog resultsDialog = new JDialog(this, "תוצאות הסקר הסופיות", true);
        resultsDialog.setSize(500, 600);
        resultsDialog.setLocationRelativeTo(this);

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        List<Question> questions = currentSession.getQuestions();
        Map<Long, ParticipantProgress> progressMap = currentSession.getProgressMap();

        for (int qIndex = 0; qIndex < questions.size(); qIndex++) {
            Question q = questions.get(qIndex);
            List<String> options = q.getOptions();

            int[] votes = new int[options.size()];
            int totalVotesForQuestion = 0;

            for (ParticipantProgress p : progressMap.values()) {
                Integer chosenOption = p.getAnswers().get(qIndex);
                if (chosenOption != null) { votes[chosenOption]++; totalVotesForQuestion++; }
            }

            List<OptionResult> resultsList = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                double percentage = (totalVotesForQuestion == 0) ? 0 : ((double) votes[i] / totalVotesForQuestion) * 100;
                resultsList.add(new OptionResult(options.get(i), votes[i], percentage));
            }

            // מיון מהתשובה הפופולרית ביותר לפחות פופולרית (דרישת אפיון מדויקת)
            resultsList.sort((a, b) -> Integer.compare(b.votes, a.votes));

            JPanel qBlock = new JPanel(new BorderLayout());
            qBlock.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("שאלה " + (qIndex + 1) + ": " + q.getText()),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            JPanel statsPanel = new JPanel();
            statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));

            for (OptionResult res : resultsList) {
                JLabel lbl = new JLabel(String.format("• %s: %d הצבעות (%.1f%%)", res.text, res.votes, res.percentage));
                statsPanel.add(lbl);
            }

            qBlock.add(statsPanel, BorderLayout.CENTER);
            container.add(qBlock);
            container.add(Box.createVerticalStrut(10));
        }

        resultsDialog.add(new JScrollPane(container));
        resultsDialog.setVisible(true);
    }

    private static class OptionResult {
        String text;
        int votes;
        double percentage;

        OptionResult(String text, int votes, double percentage) {
            this.text = text;
            this.votes = votes;
            this.percentage = percentage;
        }
    }
}