package com.roommatch.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Graphical interface. Like the CLI it only collects input, calls the AgentController (the Facade)
 * and shows results; no ranking or AI logic lives here.
 */
public final class RoomMatchGUI {
    private final GuiSession session;
    private final JFrame frame = new JFrame("RoomMatch AI");
    final JTextField userIdField = new JTextField(8);
    final JButton loginButton = new JButton("Log in");
    final JButton logoutButton = new JButton("Log out");
    final JLabel whoLabel = new JLabel("Not logged in");
    final JLabel statusLabel = new JLabel(" ");
    final JTabbedPane tabs = new JTabbedPane();

    RoomMatchGUI(RoomMatchCLI.Services services, GuiTasks tasks) {
        this.session = new GuiSession(services.controller(), services.repository(), tasks);
        buildFrame();
    }

    /** Opens the window on the Swing thread. */
    public static void launch(RoomMatchCLI.Services services) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look and feel is fine
            }
            new RoomMatchGUI(services, GuiTasks.background()).show();
        });
    }

    void show() {
        frame.setVisible(true);
    }

    JFrame frame() { return frame; }

    GuiSession session() { return session; }

    private void buildFrame() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        userIdField.setName("userId");
        loginButton.setName("login");
        logoutButton.setName("logout");
        whoLabel.setName("who");
        statusLabel.setName("status");
        tabs.setName("tabs");

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.add(new JLabel("User id:"));
        bar.add(userIdField);
        bar.add(loginButton);
        bar.add(logoutButton);
        bar.add(whoLabel);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        tabs.addTab("Profile", new ProfilePanel(session, this::status));
        addMatchTabs();

        frame.add(bar, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> login());
        userIdField.addActionListener(e -> login());
        logoutButton.addActionListener(e -> session.logout());
        session.onLoginChange(this::showWho);
        showWho();
        frame.setSize(900, 680);
        frame.setLocationRelativeTo(null);
    }

    private void addMatchTabs() {
        tabs.addTab("Matches", new MatchesPanel(session, this::status));
        tabs.addTab("Shortlist", new ShortlistPanel(session, this::status));
    }

    private void login() {
        try {
            session.login(userIdField.getText());
            status("Logged in as " + session.user().getId() + ".");
        } catch (IllegalArgumentException e) {
            status(e.getMessage());
        }
    }

    private void showWho() {
        boolean in = session.loggedIn();
        logoutButton.setEnabled(in);
        whoLabel.setText(in ? "Logged in: " + session.user().getId() + " (" + session.user().getLocation() + ")"
                : "Not logged in");
        if (!in) {
            status("Enter a user id (try p001, or p006 to build a profile) and press Log in.");
        }
    }

    void status(String message) {
        statusLabel.setText(message == null || message.isEmpty() ? " " : message);
    }

    /** Modal error box for failures the status line is too quiet for. */
    void error(String message) {
        JOptionPane.showMessageDialog(frame, message, "RoomMatch AI", JOptionPane.ERROR_MESSAGE);
    }
}
