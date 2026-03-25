import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;
import org.fife.ui.autocomplete.*;
// import org.fife.ui.rsyntaxtextarea.parser.*;
// import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.tree.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
// import java.nio.file.*;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

public class CodeEditor extends JFrame {

    private JTabbedPane tabbedPane;
    private JTextArea consoleArea;
    private UndoManager undoManager;
    private JTree fileTree;
    private File projectDir;

    // ---- Added fields for input terminal ----
    private JPanel inputPanel;
    private JTextField inputField;
    private OutputStreamWriter programInputWriter;

    public CodeEditor() {
        setTitle("Advanced Java Editor");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ---------------- Tabs ----------------
        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // ---------------- Console ----------------
        consoleArea = new JTextArea(8, 100);
        consoleArea.setEditable(false);
        consoleArea.setBackground(Color.BLACK);
        consoleArea.setForeground(Color.WHITE);
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        add(consoleScroll, BorderLayout.SOUTH);

        // ---------------- Input Terminal ----------------
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(new Color(20, 20, 20));
        JLabel promptLabel = new JLabel("Input: ");
        promptLabel.setForeground(Color.WHITE);
        inputField = new JTextField();
        inputField.setBackground(Color.DARK_GRAY);
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.setVisible(false);
        add(inputPanel, BorderLayout.NORTH);

        // ---------------- File Explorer ----------------
        projectDir = new File(System.getProperty("user.home") + "/Documents/Java/Code Editor");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(projectDir.getName());
        fileTree = new JTree(root);
        fileTree.setPreferredSize(new Dimension(200, 400));
        JScrollPane treeScroll = new JScrollPane(fileTree);
        add(treeScroll, BorderLayout.WEST);
        buildFileTree(root, projectDir);

        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null)
                return;
            File selectedFile = new File(projectDir, node.toString());
            if (selectedFile.isFile() && selectedFile.getName().endsWith(".java")) {
                openFile(selectedFile);
            }
        });

        // ---------------- Menu ----------------
        // ---------------- Menu ----------------
        JMenuBar menuBar = new JMenuBar();

        // Add menus directly to JMenuBar (do NOT wrap them in panels)
        JMenu fileMenu = new JMenu("File");
        JMenuItem newTabItem = new JMenuItem("New File");
        newTabItem.addActionListener(e -> addNewTab("Untitled"));
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveFile());
        JMenuItem saveAllItem = new JMenuItem("Save All");
        saveAllItem.addActionListener(e -> saveAllFiles());
        fileMenu.add(newTabItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAllItem);
        menuBar.add(fileMenu);

        JMenu themeMenu = new JMenu("Theme");
        JMenuItem darkItem = new JMenuItem("Dark");
        darkItem.addActionListener(e -> applyTheme("dark"));
        JMenuItem modernItem = new JMenuItem("Modern");
        modernItem.addActionListener(e -> applyTheme("modern"));
        JMenuItem lightItem = new JMenuItem("Light");
        lightItem.addActionListener(e -> applyTheme("light"));
        themeMenu.add(darkItem);
        themeMenu.add(modernItem);
        themeMenu.add(lightItem);
        menuBar.add(themeMenu);

        // --- Format menu (add after theme menu) ---
        JMenu formatMenu = new JMenu("Format");

        JMenuItem fmtAll = new JMenuItem("Format Document (All)");
        fmtAll.addActionListener(e -> {
            RSyntaxTextArea ta = getCurrentTextArea();
            if (ta != null) {
                formatRange(ta, 0, ta.getLineCount() - 1);
                // update last-format marker for this tab (store on the scrollpane)
                Component comp = tabbedPane.getSelectedComponent();
                if (comp instanceof JScrollPane) {
                    ((JScrollPane) comp).putClientProperty("lastFormatLength", ta.getDocument().getLength());
                }
            }
        });

        JMenuItem fmtSelection = new JMenuItem("Format Selection");
        fmtSelection.addActionListener(e -> {
            RSyntaxTextArea ta = getCurrentTextArea();
            if (ta != null) {
                try {
                    int start = ta.getSelectionStart();
                    int end = ta.getSelectionEnd();
                    int startLine = ta.getLineOfOffset(start);
                    int endLine = ta.getLineOfOffset(Math.max(0, end - 1));
                    formatRange(ta, startLine, endLine);
                    // don't change lastFormatLength here (optionally you can)
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }
        });

        JMenuItem fmtFromCursor = new JMenuItem("Format From Cursor to End");
        fmtFromCursor.addActionListener(e -> {
            RSyntaxTextArea ta = getCurrentTextArea();
            if (ta != null) {
                try {
                    int start = ta.getCaretPosition();
                    int startLine = ta.getLineOfOffset(start);
                    formatRange(ta, startLine, ta.getLineCount() - 1);
                    Component comp = tabbedPane.getSelectedComponent();
                    if (comp instanceof JScrollPane) {
                        ((JScrollPane) comp).putClientProperty("lastFormatLength", ta.getDocument().getLength());
                    }
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }
        });

        JMenuItem fmtNewOnly = new JMenuItem("Format New Lines (since last format)");
        fmtNewOnly.addActionListener(e -> {
            RSyntaxTextArea ta = getCurrentTextArea();
            if (ta != null) {
                Component comp = tabbedPane.getSelectedComponent();
                int lastLen = 0;
                if (comp instanceof JScrollPane) {
                    Object obj = ((JScrollPane) comp).getClientProperty("lastFormatLength");
                    if (obj instanceof Integer)
                        lastLen = (Integer) obj;
                }
                try {
                    int startLine = (lastLen <= 0) ? 0 : ta.getLineOfOffset(Math.max(0, lastLen - 1));
                    int endLine = ta.getLineCount() - 1;
                    // if lastLen points to end already, nothing to do
                    if (ta.getDocument().getLength() > lastLen) {
                        formatRange(ta, startLine, endLine);
                    }
                    // update marker
                    if (comp instanceof JScrollPane) {
                        ((JScrollPane) comp).putClientProperty("lastFormatLength", ta.getDocument().getLength());
                    }
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Add items
        formatMenu.add(fmtAll);
        formatMenu.add(fmtSelection);
        formatMenu.add(fmtFromCursor);
        formatMenu.addSeparator();
        formatMenu.add(fmtNewOnly);

        // add the format menu to your menuBar (just once)
        menuBar.add(formatMenu);

        // Add horizontal glue so items added after this are pushed to the right
        menuBar.add(Box.createHorizontalGlue());

        // Styled Run button added directly to the JMenuBar (on the right)
        JButton runButton = new JButton("Run ▶️");
        runButton.setFont(new Font("Arial", Font.BOLD, 14));
        runButton.setPreferredSize(new Dimension(100, 30));
        runButton.setFocusPainted(false);

        // Make button background visible across LAFs
        runButton.setOpaque(true);
        runButton.setBorderPainted(false);
        runButton.setBackground(new Color(70, 150, 250));
        runButton.setForeground(Color.WHITE);
        runButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Hover effect (keeps color visible)
        runButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                runButton.setBackground(new Color(60, 140, 240));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                runButton.setBackground(new Color(70, 150, 250));
            }
        });

        // keep the existing action
        runButton.addActionListener(e -> runCurrentFile());

        // Add the Run button to the menu bar (it will appear on the right due to glue)
        menuBar.add(runButton);

        // Install the menu bar
        setJMenuBar(menuBar);

        // ---------------- Add first tab ----------------
        addNewTab("Main");

        // ---------------- Auto-save every 1 minute ----------------
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> saveAllFiles());
            }
        }, 60000, 60000);

        // Handle user input submission
        inputField.addActionListener(e -> {
            String input = inputField.getText();
            inputField.setText("");
            inputPanel.setVisible(false);
            if (programInputWriter != null) {
                try {
                    programInputWriter.write(input + "\n");
                    programInputWriter.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // create and install a custom tab header with a right-side close button
    private void installTabHeader(final RTextScrollPane sp, String title) {
        // find index for this component (must be added to tabbedPane already)
        int index = tabbedPane.indexOfComponent(sp);
        if (index < 0)
            return;

        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setOpaque(false);

        // Title label stored so we can update it later (on save/rename)
        JLabel tabTitle = new JLabel(title);
        tabTitle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        tabTitle.setFont(tabTitle.getFont().deriveFont(Font.PLAIN, 12f));
        tabPanel.add(tabTitle, BorderLayout.CENTER);

        // keep reference to label on the scroll pane so other code can update it
        sp.putClientProperty("tabLabel", tabTitle);

        // Close button
        JButton closeButton = new JButton("✕");
        closeButton.setToolTipText("Close");
        closeButton.setFocusable(false);
        closeButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        closeButton.setContentAreaFilled(false);
        closeButton.setOpaque(false);
        closeButton.setBorderPainted(false);
        closeButton.setForeground(new Color(100, 100, 100));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.setPreferredSize(new Dimension(22, 20));
        closeButton.setFont(new Font("Arial", Font.BOLD, 12));

        // Hover effect
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(Color.RED);
                closeButton.setOpaque(true);
                closeButton.setBackground(new Color(40, 40, 40, 30));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(new Color(100, 100, 100));
                closeButton.setOpaque(false);
            }
        });

        // Remove the tab when close clicked
        closeButton.addActionListener(e -> {
            int i = tabbedPane.indexOfComponent(sp);
            if (i >= 0)
                tabbedPane.remove(i);
        });

        tabPanel.add(closeButton, BorderLayout.EAST);

        tabbedPane.setTabComponentAt(index, tabPanel);
    }

    private void addNewTab(String title) {
        RSyntaxTextArea textArea = createCustomEditor();
        RTextScrollPane sp = new RTextScrollPane(textArea);

        // add the tab content first
        tabbedPane.addTab(title, sp);

        // install the custom header (title + right-side close button)
        installTabHeader(sp, title);

        // select the new tab
        tabbedPane.setSelectedComponent(sp);

        // force UI update if required
        SwingUtilities.updateComponentTreeUI(tabbedPane);
    }

    // ---------- Auto-completion provider (Java keywords + snippets) ----------
    private CompletionProvider createCompletionProvider() {

        DefaultCompletionProvider provider = new DefaultCompletionProvider();

        // --- Java keywords ---
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "continue", "default", "do", "double", "else",
                "enum", "extends", "final", "finally", "float", "for", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new",
                "package", "private", "protected", "public", "return", "short",
                "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while"
        };
        for (String kw : keywords) {
            provider.addCompletion(new BasicCompletion(provider, kw));
        }

        // --- Common types ---
        String[] types = {
                "String", "System", "Object", "Integer", "Double", "Float",
                "List", "ArrayList", "HashMap"
        };
        for (String t : types) {
            provider.addCompletion(new BasicCompletion(provider, t));
        }

        // --- Snippets / shorthand completions ---
        provider.addCompletion(new ShorthandCompletion(
                provider,
                "main",
                "public static void main(String[] args) {\n\t$cursor$\n}",
                "Main method"));

        provider.addCompletion(new ShorthandCompletion(
                provider,
                "sout",
                "System.out.println($cursor$);",
                "System.out.println"));

        provider.addCompletion(new ShorthandCompletion(
                provider,
                "sysout",
                "System.out.println($cursor$);",
                "System.out.println (sysout)"));

        provider.addCompletion(new ShorthandCompletion(
                provider,
                "fori",
                "for (int i = 0; i < ${limit}; i++) {\n\t$cursor$\n}",
                "For loop"));

        // --- Basic member completions so System.out.<ctrl-space> shows useful choices
        // ---
        provider.addCompletion(new BasicCompletion(provider, "println"));
        provider.addCompletion(new BasicCompletion(provider, "print"));
        provider.addCompletion(new BasicCompletion(provider, "printf"));

        // extra convenient completions
        provider.addCompletion(new BasicCompletion(provider, "exit"));
        provider.addCompletion(new BasicCompletion(provider, "currentTimeMillis"));

        return provider;
    }

    private RSyntaxTextArea createCustomEditor() {
        // Use our Ghost-enabled RSyntaxTextArea subclass
        GhostRSyntaxTextArea textArea = new GhostRSyntaxTextArea(20, 60);

        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);

        // Default dark style (you already change this in applyTheme)
        textArea.setBackground(Color.BLACK);
        textArea.setForeground(Color.WHITE);
        textArea.setCaretColor(Color.WHITE);
        textArea.setSelectionColor(new Color(60, 60, 120));
        textArea.setCurrentLineHighlightColor(new Color(25, 25, 25));

        // SyntaxScheme (same as you had)
        SyntaxScheme scheme = (SyntaxScheme) textArea.getSyntaxScheme().clone();
        scheme.getStyle(Token.RESERVED_WORD).foreground = new Color(86, 156, 214);
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = new Color(197, 134, 192);
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = new Color(206, 145, 120);
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = new Color(181, 206, 168);
        scheme.getStyle(Token.COMMENT_EOL).foreground = new Color(87, 166, 74);
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = new Color(87, 166, 74);
        scheme.getStyle(Token.OPERATOR).foreground = Color.WHITE;
        scheme.getStyle(Token.IDENTIFIER).foreground = Color.WHITE;
        textArea.setSyntaxScheme(scheme);

        // Undo manager
        undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));

        // Undo / Redo keybindings
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "Undo");
        textArea.getActionMap().put("Undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canUndo())
                    undoManager.undo();
            }
        });
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "Redo");
        textArea.getActionMap().put("Redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (undoManager.canRedo())
                    undoManager.redo();
            }
        });

        // Auto-indent on newline
        textArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    try {
                        int caret = textArea.getCaretPosition();
                        int line = textArea.getLineOfOffset(caret);
                        if (line - 1 >= 0) {
                            String prevLineText = textArea.getText(textArea.getLineStartOffset(line - 1),
                                    textArea.getLineEndOffset(line - 1) - textArea.getLineStartOffset(line - 1));
                            String indent = "";
                            for (char c : prevLineText.toCharArray()) {
                                if (c == ' ' || c == '\t')
                                    indent += c;
                                else
                                    break;
                            }
                            textArea.getDocument().insertString(caret, indent, null);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        // --------------------- AutoCompletion install (unchanged)
        // ---------------------
        CompletionProvider provider = createCompletionProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        ac.setShowDescWindow(true);
        ac.setAutoCompleteSingleChoices(false);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(200);
        ac.setParameterAssistanceEnabled(true);
        ac.install(textArea);

        // --------------------- Ghost-text support ---------------------
        // Build a small candidate list for inline suggestions (keywords, types,
        // snippets, common members)
        // You can extend this list as needed.
        java.util.List<String> candidateList = new java.util.ArrayList<>();

        // keywords (kept short for speed — but you can include all)
        String[] keywords = {
                "public", "private", "protected", "class", "static", "final", "void", "int", "double", "float",
                "if", "else", "for", "while", "switch", "case", "try", "catch", "return", "new", "import", "package"
        };
        for (String k : keywords)
            candidateList.add(k);

        // common types
        String[] types = { "String", "System", "Object", "Integer", "Double", "Float", "List", "ArrayList", "HashMap" };
        for (String t : types)
            candidateList.add(t);

        // members & methods people want quickly
        candidateList.add("println");
        candidateList.add("print");
        candidateList.add("printf");
        candidateList.add("out");
        candidateList.add("main");
        candidateList.add("sout");
        candidateList.add("sysout");

        // also add entries from provider's BasicCompletion if possible (best-effort)
        try {
            // DefaultCompletionProvider exposes getCompletionByInput? Not reliably; instead
            // attempt to iterate any
            // known completions via reflection as a fallback (nonfatal). This is optional
            // and safely ignored on failure.
            if (provider instanceof DefaultCompletionProvider) {
                DefaultCompletionProvider dp = (DefaultCompletionProvider) provider;
                // DefaultCompletionProvider doesn't provide public getter for all completions,
                // so we'll skip reflection here.
                // If you want to add more candidates, just add to the candidateList above.
            }
        } catch (Exception ignore) {
        }

        // old:
        // GhostSuggestionManager manager = new GhostSuggestionManager(textArea,
        // candidateList);
        // manager.start();

        // new:
        GhostSuggestionManager manager = new GhostSuggestionManager(textArea, candidateList, provider);
        manager.start();

        return textArea;
    }

    /**
     * Ghost-enabled RSyntaxTextArea subclass.
     * Paints a faint "ghost" suggestion after the caret, and provides an
     * accept-with-TAB action.
     *
     * Enhanced: if a full snippet string is stored as client property
     * "ghostFullInsert", Tab will insert that
     * snippet and position the caret according to the "ghostCursorOffset" client
     * property (negative values count
     * from the end).
     */
    private static class GhostRSyntaxTextArea extends RSyntaxTextArea {

        private String ghostText = null;
        private Color ghostColor = new Color(150, 150, 170, 150); // light translucent color
        private final Font ghostFont;

        public GhostRSyntaxTextArea(int rows, int cols) {
            super(rows, cols);
            ghostFont = getFont().deriveFont(Font.PLAIN, getFont().getSize());

            // Bind TAB to accept ghost (action name "accept-ghost")
            getInputMap().put(KeyStroke.getKeyStroke("TAB"), "accept-ghost");
            getActionMap().put("accept-ghost", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // If popup is visible (AutoCompletion's popup), we should let the popup handle
                    // Tab.
                    // Popup detection is tricky; we conservatively check client property set by
                    // manager:
                    Object popupVisible = getClientProperty("autoCompletePopupVisible");
                    if (popupVisible instanceof Boolean && ((Boolean) popupVisible)) {
                        // Let focus move to popup (do nothing here)
                        return;
                    }

                    if (ghostText != null && ghostText.length() > 0) {
                        try {
                            // If a full snippet insertion is provided, insert that instead and position
                            // caret
                            Object fullObj = getClientProperty("ghostFullInsert");
                            if (fullObj instanceof String) {
                                String fullInsert = (String) fullObj;
                                // insert the snippet text
                                int caret = getCaretPosition();
                                getDocument().insertString(caret, fullInsert, null);

                                // compute new caret pos using ghostCursorOffset
                                Object offObj = getClientProperty("ghostCursorOffset");
                                int offset = 0;
                                if (offObj instanceof Integer)
                                    offset = (Integer) offObj;
                                int newCaret;
                                if (offset < 0)
                                    newCaret = caret + fullInsert.length() + offset; // negative from end
                                else
                                    newCaret = caret + offset;
                                // clamp
                                newCaret = Math.max(0, Math.min(newCaret, getDocument().getLength()));
                                setCaretPosition(newCaret);

                                // clear ghost & snippet props
                                setGhostText(null);
                                putClientProperty("ghostFullInsert", null);
                                putClientProperty("ghostCursorOffset", null);
                            } else {
                                // No snippet — insert the displayed ghost remainder
                                int caret = getCaretPosition();
                                getDocument().insertString(caret, ghostText, null);
                                setCaretPosition(caret + ghostText.length());
                                setGhostText(null);
                            }
                        } catch (Exception ex) {
                            // fallback: transfer focus
                            transferFocus();
                        }
                    } else {
                        // no ghost -> default tab (may move focus)
                        transferFocus();
                    }
                }
            });
        }

        public void setGhostText(String text) {
            this.ghostText = (text == null || text.isEmpty()) ? null : text;
            // Request repaint of area around caret
            SwingUtilities.invokeLater(() -> {
                try {
                    Rectangle r = modelToView(getCaretPosition());
                    if (r != null)
                        repaint(r.x, Math.max(0, r.y - 6), getWidth() - r.x, r.height + 12);
                    else
                        repaint();
                } catch (BadLocationException e) {
                    repaint();
                }
            });
        }

        public String getGhostText() {
            return ghostText;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // draw ghost text if present
            if (ghostText == null || ghostText.isEmpty())
                return;
            try {
                int caretPos = getCaretPosition();
                Rectangle caretRect = modelToView(caretPos);
                if (caretRect == null)
                    return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setFont(ghostFont);
                g2.setColor(ghostColor);

                // compute baseline position for text rendering
                FontMetrics fm = g2.getFontMetrics();
                int tx = caretRect.x;
                int ty = caretRect.y + fm.getAscent();

                // Draw the ghost text
                g2.drawString(ghostText, tx, ty);

                g2.dispose();
            } catch (BadLocationException ex) {
                // ignore
            }
        }
    }

    /**
     * Manager that listens to document/caret changes and computes a top candidate
     * from a candidate list.
     * It updates the GhostRSyntaxTextArea with the suggested remainder (candidate
     * minus already-typed prefix).
     *
     * Enhanced:
     * - case-insensitive matching
     * - dot/member segment matching
     * - shorthand snippet support (sout/sysout -> full snippet insertion)
     * - accepts a CompletionProvider (future hook to extract provider completions)
     */
    private static class GhostSuggestionManager implements javax.swing.event.DocumentListener, CaretListener {
        private final GhostRSyntaxTextArea area;
        private final java.util.List<String> candidates;
        private final CompletionProvider provider; // optional, may be null
        private final javax.swing.Timer recomputeTimer;

        public GhostSuggestionManager(GhostRSyntaxTextArea area, java.util.List<String> candidates,
                CompletionProvider provider) {
            this.area = area;
            this.candidates = new java.util.ArrayList<>();
            // copy and normalize candidates for case-insensitive matching
            if (candidates != null) {
                for (String c : candidates)
                    if (c != null && !c.isEmpty())
                        this.candidates.add(c);
            }
            this.provider = provider;
            recomputeTimer = new javax.swing.Timer(120, e -> computeAndShowSuggestion());
            recomputeTimer.setRepeats(false);
        }

        public void start() {
            area.getDocument().addDocumentListener(this);
            area.addCaretListener(this);
        }

        public void stop() {
            area.getDocument().removeDocumentListener(this);
            area.removeCaretListener(this);
            recomputeTimer.stop();
        }

        /**
         * Core logic that computes best suggestion based on typed prefix.
         */
        private void computeAndShowSuggestion() {
            try {
                int caret = area.getCaretPosition();
                String text = area.getText(0, caret);

                // find token: the word characters directly before caret (letters, digits, dot,
                // underscore)
                int i = text.length() - 1;
                while (i >= 0) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '.')
                        i--;
                    else
                        break;
                }
                int start = i + 1;
                if (start < 0 || start > text.length()) {
                    area.setGhostText(null);
                    clearSnippetProps();
                    return;
                }
                String prefix = text.substring(start);

                if (prefix == null || prefix.trim().isEmpty()) {
                    area.setGhostText(null);
                    clearSnippetProps();
                    return;
                }

                // Trim whitespace (defensive)
                prefix = prefix.trim();
                // Shorthand first: exact matches for 'sout'/'sysout' (case-insensitive)
                String lowerPrefix = prefix.toLowerCase();
                if (lowerPrefix.equals("sout") || lowerPrefix.equals("sysout")) {
                    // Prepare full insertion for the snippet and a caret offset to place inside
                    // parentheses
                    String fullInsert = "System.out.println();";
                    // cursor inside parentheses -> offset = length("System.out.println(")
                    int caretOffset = "System.out.println(".length();
                    area.putClientProperty("ghostFullInsert", fullInsert);
                    area.putClientProperty("ghostCursorOffset", caretOffset);
                    // Show ghost remainder based on typed prefix
                    // e.g., typed 'sout' -> remainder 'ystem.out.println();' but we will show the
                    // visible hint as
                    // the tail 'ystem.out.println();' would be too long; instead show like
                    // "ystem.out.println()"
                    // For simplicity show the part after first character to indicate the
                    // suggestion:
                    String visibleRemainder = fullInsert; // you might shorten but here we show full for clarity
                    // We want the ghost text to be the part that will be inserted if Tab pressed in
                    // non-snippet mode,
                    // but since we handle snippet insertion, it's ok to show a friendly short hint:
                    area.setGhostText(fullInsert); // is OK; Tab will insert fullInsert
                    return;
                }

                // If contains dot -> match last segment with member candidates
                if (prefix.contains(".")) {
                    String last = prefix.substring(prefix.lastIndexOf('.') + 1);
                    if (last.isEmpty()) {
                        area.setGhostText(null);
                        clearSnippetProps();
                        return;
                    }
                    String lastLower = last.toLowerCase();
                    // Search candidates for a member starting with last
                    for (String cand : candidates) {
                        if (cand == null)
                            continue;
                        if (cand.toLowerCase().startsWith(lastLower) && cand.length() > last.length()) {
                            // remainder relative to last
                            String remainder = cand.substring(last.length());
                            area.setGhostText(remainder);
                            clearSnippetProps();
                            return;
                        }
                    }
                    // optionally check provider completions (if provider is
                    // DefaultCompletionProvider)
                    // We don't rely on reflection — keep it simple. If no candidate matched, clear.
                    area.setGhostText(null);
                    clearSnippetProps();
                    return;
                }

                // No dot: match prefix with candidates (case-insensitive)
                String prefLower = prefix.toLowerCase();
                for (String cand : candidates) {
                    if (cand == null)
                        continue;
                    if (cand.toLowerCase().startsWith(prefLower) && cand.length() > prefix.length()) {
                        String remainder = cand.substring(prefix.length());
                        area.setGhostText(remainder);
                        clearSnippetProps();
                        return;
                    }
                }

                // Nothing matches
                area.setGhostText(null);
                clearSnippetProps();
            } catch (BadLocationException ex) {
                area.setGhostText(null);
                clearSnippetProps();
            }
        }

        private void clearSnippetProps() {
            area.putClientProperty("ghostFullInsert", null);
            area.putClientProperty("ghostCursorOffset", null);
        }

        // DocumentListener methods
        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            recomputeTimer.restart();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            recomputeTimer.restart();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            recomputeTimer.restart();
        }

        // CaretListener
        @Override
        public void caretUpdate(CaretEvent e) {
            recomputeTimer.restart();
        }
    }

    private RSyntaxTextArea getCurrentTextArea() {
        JScrollPane sp = (JScrollPane) tabbedPane.getSelectedComponent();
        if (sp != null) {
            JViewport vp = sp.getViewport();
            return (RSyntaxTextArea) vp.getView();
        }
        return null;
    }

    /**
     * Format lines from startLine to endLine (inclusive).
     * Uses a simple brace-based indentation heuristic suitable for Java-like code.
     */
    private void formatRange(RSyntaxTextArea ta, int startLine, int endLine) {
        try {
            if (startLine < 0)
                startLine = 0;
            if (endLine >= ta.getLineCount())
                endLine = ta.getLineCount() - 1;
            if (startLine > endLine)
                return;

            // compute base indent level from start of document up to startLine
            int startOffset = ta.getLineStartOffset(startLine);
            int baseIndent = computeIndentLevelUpToOffset(ta, startOffset);

            // build new text for the lines range
            StringBuilder rebuilt = new StringBuilder();
            int indent = baseIndent;

            for (int ln = startLine; ln <= endLine; ln++) {
                int lineStart = ta.getLineStartOffset(ln);
                int lineEnd = ta.getLineEndOffset(ln);
                String lineText = ta.getText(lineStart, Math.max(0, lineEnd - lineStart));

                // strip newline at end (we will append single '\n' after line)
                String lineNoNL = lineText.replaceAll("\\r?\\n$", "");

                String trimmed = lineNoNL.trim();

                // If line starts with a closing brace decrease indent before printing
                if (trimmed.startsWith("}")) {
                    indent = Math.max(0, indent - 1);
                }

                // compose indented line
                String indents = "    ".repeat(Math.max(0, indent)); // 4 spaces per level
                rebuilt.append(indents).append(trimmed);

                // after a line that ends with "{" increase indent for next line
                if (trimmed.endsWith("{")) {
                    indent++;
                }

                // preserve newline (use system newline)
                if (ln < endLine)
                    rebuilt.append(System.lineSeparator());
            }

            // Replace the text for the lines range in document
            int replaceStart = ta.getLineStartOffset(startLine);
            int replaceEnd = ta.getLineEndOffset(endLine);
            ta.getDocument().remove(replaceStart, replaceEnd - replaceStart);
            ta.getDocument().insertString(replaceStart, rebuilt.toString(), null);

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Compute a naive indentation level by scanning the document from 0 to offset
     * and counting braces.
     * This is a heuristic and ignores strings/comments; it's sufficient for simple
     * Java code.
     */
    private int computeIndentLevelUpToOffset(RSyntaxTextArea ta, int offset) {
        try {
            String upto = ta.getText(0, Math.max(0, Math.min(offset, ta.getDocument().getLength())));
            int level = 0;
            for (int i = 0; i < upto.length(); i++) {
                char c = upto.charAt(i);
                if (c == '{')
                    level++;
                else if (c == '}')
                    level = Math.max(0, level - 1);
            }
            return Math.max(0, level);
        } catch (BadLocationException e) {
            return 0;
        }
    }
    
    /**
     * Convenience: format entire document
     */
    private void formatEntireDocument(RSyntaxTextArea ta) {
        formatRange(ta, 0, ta.getLineCount() - 1);
    }

    // ---------------- File Operations ----------------
    private void saveFile() {
        RSyntaxTextArea textArea = getCurrentTextArea();
        if (textArea == null)
            return;
        JFileChooser chooser = new JFileChooser(projectDir);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                try (FileWriter fw = new FileWriter(file)) {
                    textArea.write(fw);
                }
                int index = tabbedPane.getSelectedIndex();
                tabbedPane.setTitleAt(index, file.getName());
                buildFileTree((DefaultMutableTreeNode) fileTree.getModel().getRoot(), projectDir);

                // int index = tabbedPane.getSelectedIndex();
                // tabbedPane.setTitleAt(index, file.getName());
                // buildFileTree((DefaultMutableTreeNode) fileTree.getModel().getRoot(),
                // projectDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveAllFiles() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            JScrollPane sp = (JScrollPane) tabbedPane.getComponentAt(i);
            JViewport vp = sp.getViewport();
            RSyntaxTextArea textArea = (RSyntaxTextArea) vp.getView();
            String title = tabbedPane.getTitleAt(i);
            File file = new File(projectDir, title.endsWith(".java") ? title : title + ".java");
            try {
                try (FileWriter fw = new FileWriter(file)) {
                    textArea.write(fw);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        buildFileTree((DefaultMutableTreeNode) fileTree.getModel().getRoot(), projectDir);
        consoleArea.append("Auto-saved all files.\n");
    }

    private void openFile(File file) {
        if (file == null)
            return;

        // check existing tabs by filePath stored property (from earlier suggestion)
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof JScrollPane) {
                Object pathObj = ((JScrollPane) comp).getClientProperty("filePath");
                if (pathObj != null && file.getAbsolutePath().equals(pathObj.toString())) {
                    tabbedPane.setSelectedIndex(i);
                    // optional: refresh file content
                    try (FileReader fr = new FileReader(file)) {
                        JViewport vp = ((JScrollPane) comp).getViewport();
                        Component view = vp.getView();
                        if (view instanceof RSyntaxTextArea) {
                            ((RSyntaxTextArea) view).read(fr, null);
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    return;
                }
            }
        }

        // not open yet -> create new tab + header
        try {
            RSyntaxTextArea textArea = createCustomEditor();
            try (FileReader fr = new FileReader(file)) {
                textArea.read(fr, null);
            }
            RTextScrollPane sp = new RTextScrollPane(textArea);

            // store absolute path for duplicate detection
            sp.putClientProperty("filePath", file.getAbsolutePath());

            tabbedPane.addTab(file.getName(), sp);
            installTabHeader(sp, file.getName());
            tabbedPane.setSelectedComponent(sp);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to open file: " + e.getMessage(),
                    "Open Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------- Theme ----------------
    private void applyTheme(String theme) {
        // --- Modern palette (refined pastel-modern, slightly darker text for
        // readability) ---
        final Color modernPanelBg = new Color(237, 232, 240);
        final Color modernEditorBg = new Color(245, 241, 250);
        final Color modernText = new Color(35, 28, 45);
        final Color modernMuted = new Color(90, 80, 105);
        final Color modernKeyword = new Color(165, 110, 230);
        final Color modernModifier = new Color(185, 100, 230);
        final Color modernString = new Color(220, 150, 180);
        final Color modernNumber = new Color(110, 170, 150);
        final Color modernComment = new Color(120, 125, 135);
        final Color modernOperator = new Color(45, 35, 60);
        final Color modernSelection = new Color(223, 205, 245);
        final Color modernLineHL = new Color(232, 227, 237);

        // --- Default light/dark colors (kept compatible) ---
        Color bg, fg, caret, selection, currentLine;

        if ("modern".equalsIgnoreCase(theme)) {
            bg = modernPanelBg;
            fg = modernText;
            caret = modernText;
            selection = modernSelection;
            currentLine = modernLineHL;
        } else if ("light".equalsIgnoreCase(theme)) {
            bg = Color.WHITE;
            fg = Color.BLACK;
            caret = Color.BLACK;
            selection = new Color(200, 220, 255);
            currentLine = new Color(240, 240, 240);
        } else { // dark (default)
            bg = Color.BLACK;
            fg = Color.WHITE;
            caret = Color.WHITE;
            selection = new Color(60, 60, 120);
            currentLine = new Color(25, 25, 25);
        }

        // Apply to each open tab editor
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (!(comp instanceof JScrollPane))
                continue;
            JScrollPane sp = (JScrollPane) comp;
            JViewport vp = sp.getViewport();
            Component view = vp.getView();
            if (!(view instanceof RSyntaxTextArea))
                continue;
            RSyntaxTextArea ta = (RSyntaxTextArea) view;

            // Editor background & basic colors
            if ("modern".equalsIgnoreCase(theme)) {
                ta.setBackground(modernEditorBg);
                ta.setForeground(fg);
                ta.setCaretColor(caret);
                ta.setSelectionColor(selection);
                ta.setCurrentLineHighlightColor(currentLine);
            } else {
                ta.setBackground(bg);
                ta.setForeground(fg);
                ta.setCaretColor(caret);
                ta.setSelectionColor(selection);
                ta.setCurrentLineHighlightColor(currentLine);
            }

            // Update syntax scheme per theme (clone to avoid mutating shared scheme)
            SyntaxScheme scheme = (SyntaxScheme) ta.getSyntaxScheme().clone();

            if ("modern".equalsIgnoreCase(theme)) {
                safeSetStyleForeground(scheme, Token.RESERVED_WORD, modernKeyword);
                safeSetStyleForeground(scheme, Token.RESERVED_WORD_2, modernModifier);
                safeSetStyleForeground(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, modernString);
                safeSetStyleForeground(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, modernNumber);
                safeSetStyleForeground(scheme, Token.COMMENT_EOL, modernComment);
                safeSetStyleForeground(scheme, Token.COMMENT_MULTILINE, modernComment);
                safeSetStyleForeground(scheme, Token.IDENTIFIER, modernMuted);
                safeSetStyleForeground(scheme, Token.OPERATOR, modernOperator);
                safeSetStyleForeground(scheme, Token.FUNCTION, modernModifier);
                safeSetStyleForeground(scheme, Token.DATA_TYPE, modernKeyword);
                safeSetStyleForeground(scheme, Token.NULL, fg);
            } else if ("light".equalsIgnoreCase(theme)) {
                safeSetStyleForeground(scheme, Token.RESERVED_WORD, new Color(0, 0, 180));
                safeSetStyleForeground(scheme, Token.RESERVED_WORD_2, new Color(140, 0, 140));
                safeSetStyleForeground(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, new Color(163, 21, 21));
                safeSetStyleForeground(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, new Color(9, 134, 88));
                safeSetStyleForeground(scheme, Token.COMMENT_EOL, new Color(0, 128, 0));
                safeSetStyleForeground(scheme, Token.COMMENT_MULTILINE, new Color(0, 128, 0));
                safeSetStyleForeground(scheme, Token.OPERATOR, Color.BLACK);
                safeSetStyleForeground(scheme, Token.IDENTIFIER, Color.BLACK);
            } else {
                safeSetStyleForeground(scheme, Token.RESERVED_WORD, new Color(86, 156, 214));
                safeSetStyleForeground(scheme, Token.RESERVED_WORD_2, new Color(197, 134, 192));
                safeSetStyleForeground(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, new Color(206, 145, 120));
                safeSetStyleForeground(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, new Color(181, 206, 168));
                safeSetStyleForeground(scheme, Token.COMMENT_EOL, new Color(87, 166, 74));
                safeSetStyleForeground(scheme, Token.COMMENT_MULTILINE, new Color(87, 166, 74));
                safeSetStyleForeground(scheme, Token.IDENTIFIER, Color.WHITE);
                safeSetStyleForeground(scheme, Token.OPERATOR, Color.WHITE);
            }

            ta.setSyntaxScheme(scheme);
        }

        // Update consoleArea and inputPanel styling so entire app feels coherent
        if ("modern".equalsIgnoreCase(theme)) {
            consoleArea.setBackground(new Color(240, 236, 245));
            consoleArea.setForeground(new Color(35, 28, 45));
            consoleArea.setCaretColor(new Color(35, 28, 45));

            if (inputPanel != null) {
                inputPanel.setBackground(modernPanelBg);
                inputField.setBackground(new Color(240, 236, 245));
                inputField.setForeground(modernText);
                inputField.setCaretColor(modernText);
            }
        } else if ("light".equalsIgnoreCase(theme)) {
            consoleArea.setBackground(Color.WHITE);
            consoleArea.setForeground(Color.BLACK);
            if (inputPanel != null) {
                inputPanel.setBackground(new Color(245, 245, 245));
                inputField.setBackground(Color.WHITE);
                inputField.setForeground(Color.BLACK);
            }
        } else {
            consoleArea.setBackground(Color.BLACK);
            consoleArea.setForeground(Color.WHITE);
            if (inputPanel != null) {
                inputPanel.setBackground(new Color(20, 20, 20));
                inputField.setBackground(Color.DARK_GRAY);
                inputField.setForeground(Color.WHITE);
            }
        }

        // --------- FINAL: Recreate / re-style tab headers so titles always follow
        // theme ---------
        Color titleColor;
        if ("dark".equalsIgnoreCase(theme)) {
            titleColor = Color.WHITE;
        } else if ("modern".equalsIgnoreCase(theme)) {
            titleColor = new Color(35, 28, 45);
        } else {
            titleColor = Color.BLACK;
        }

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            // get tab component + title
            Component contentComp = tabbedPane.getComponentAt(i);
            String title = tabbedPane.getTitleAt(i);

            // If content is RTextScrollPane (usual case) reinstall header using helper
            if (contentComp instanceof RTextScrollPane) {
                RTextScrollPane sp = (RTextScrollPane) contentComp;
                installTabHeader(sp, title); // reinstalls header and stores tabLabel on sp
                Object stored = sp.getClientProperty("tabLabel");
                if (stored instanceof JLabel) {
                    ((JLabel) stored).setForeground(titleColor);
                }
            } else if (contentComp instanceof JScrollPane) {
                // fallback for plain JScrollPane - attempt to update stored label if present
                JScrollPane jsp = (JScrollPane) contentComp;
                Object stored = jsp.getClientProperty("tabLabel");
                if (stored instanceof JLabel) {
                    ((JLabel) stored).setForeground(titleColor);
                }
            }

            // Update the visible header panel (if any) as an extra safety net
            Component header = tabbedPane.getTabComponentAt(i);
            if (header instanceof JPanel) {
                JPanel headerPanel = (JPanel) header;
                for (Component c : headerPanel.getComponents()) {
                    if (c instanceof JLabel) {
                        c.setForeground(titleColor);
                    } else if (c instanceof JButton) {
                        if ("dark".equalsIgnoreCase(theme)) {
                            c.setForeground(Color.LIGHT_GRAY);
                        } else if ("modern".equalsIgnoreCase(theme)) {
                            c.setForeground(new Color(110, 100, 120));
                        } else {
                            c.setForeground(new Color(100, 100, 100));
                        }
                    }
                }
            }
        }

        // Final UI refresh on EDT
        SwingUtilities.invokeLater(() -> {
            tabbedPane.revalidate();
            tabbedPane.repaint();
            consoleArea.revalidate();
            consoleArea.repaint();
        });
    }

    /**
     * Helper: set style foreground safely (some token indexes may be null).
     */
    private void safeSetStyleForeground(SyntaxScheme scheme, int token, Color color) {
        try {
            if (scheme.getStyle(token) != null) {
                scheme.getStyle(token).foreground = color;
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- File Tree ----------------
    private void buildFileTree(DefaultMutableTreeNode node, File dir) {
        node.removeAllChildren();
        for (File file : dir.listFiles()) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(file.getName());
            node.add(child);
            if (file.isDirectory())
                buildFileTree(child, file);
        }
        ((DefaultTreeModel) fileTree.getModel()).reload();
    }

    // ---------------- Run Java ----------------
    // private OutputStreamWriter programInputWriter;
    // private JPanel inputPanel;
    // private JTextField inputField;

    private void runCurrentFile() {
        RSyntaxTextArea textArea = getCurrentTextArea();
        if (textArea == null)
            return;

        try {
            String code = textArea.getText();
            String className = "TempRun";
            for (String line : code.split("\n")) {
                line = line.trim();
                if (line.startsWith("public class ")) {
                    className = line.split("\\s+")[2];
                    if (className.contains("{"))
                        className = className.substring(0, className.indexOf("{"));
                    break;
                }
            }
            // User-selected or tab filename
            String tabTitle = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
            File actualFile = new File(projectDir, tabTitle);

            // Correct filename based on public class
            File correctFile = new File(projectDir, className + ".java");

            // If file name mismatches public class name → ask user
            if (!actualFile.getName().equals(correctFile.getName())) {

                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "Filename (" + actualFile.getName() + ") does not match the public class name (" + className
                                + ").\n" +
                                "Do you want to rename the file to " + className + ".java ?",
                        "Filename Mismatch",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        // Ensure editor content is flushed to the ORIGINAL file (close writer
                        // immediately)
                        // (this prevents an open writer from keeping the file locked)
                        try (FileWriter fw = new FileWriter(actualFile)) {
                            textArea.write(fw);
                        }

                        // Now perform the move (will replace if target exists)
                        java.nio.file.Path src = actualFile.toPath();
                        java.nio.file.Path dst = correctFile.toPath();
                        java.nio.file.Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        actualFile = correctFile;

                        // Update tab title
                        tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), correctFile.getName());

                        // Rebuild file tree and expand/select the renamed file
                        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) fileTree.getModel().getRoot();
                        buildFileTree(rootNode, projectDir);
                        expandAll(fileTree, true);
                        selectFileInTree(correctFile);

                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this,
                                "Failed to rename file: " + ex.getMessage(),
                                "Rename Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }

            }

            // Save code to whichever file we chose
            textArea.write(new FileWriter(actualFile));

            // Use final filename for compilation
            File tempFile = actualFile;

            // Compile
            Process compile = new ProcessBuilder("javac", tempFile.getAbsolutePath())
                    .redirectErrorStream(true).start();
            BufferedReader compileOutput = new BufferedReader(new InputStreamReader(compile.getInputStream()));
            consoleArea.setText("");
            String line;
            while ((line = compileOutput.readLine()) != null)
                consoleArea.append(line + "\n");
            compile.waitFor();

            if (compile.exitValue() == 0) {
                Process run = new ProcessBuilder("java", "-cp", projectDir.getAbsolutePath(), className)
                        .redirectErrorStream(true).start();

                programInputWriter = new OutputStreamWriter(run.getOutputStream());

                // --- Input Field Setup ---
                if (inputPanel == null) {
                    inputPanel = new JPanel(new BorderLayout());
                    inputField = new JTextField();
                    inputPanel.add(new JLabel("Input: "), BorderLayout.WEST);
                    inputPanel.add(inputField, BorderLayout.CENTER);
                    inputPanel.setVisible(false);
                    add(inputPanel, BorderLayout.NORTH); // You can move it to SOUTH if preferred
                    revalidate();
                    repaint();

                    inputField.addActionListener(e -> {
                        String userInput = inputField.getText();
                        try {
                            if (programInputWriter != null) {
                                programInputWriter.write(userInput + "\n");
                                programInputWriter.flush();
                            }
                            consoleArea.append(userInput + "\n");
                            inputField.setText("");
                            inputPanel.setVisible(false);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                }

                // --- Output Thread ---
                Thread outputThread = new Thread(() -> {
                    try (InputStream in = run.getInputStream()) {
                        int ch;
                        StringBuilder buffer = new StringBuilder();
                        while ((ch = in.read()) != -1) {
                            final char c = (char) ch;
                            SwingUtilities.invokeLater(() -> {
                                consoleArea.append(String.valueOf(c));
                                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
                            });

                            buffer.append(c);
                            if (buffer.length() > 200)
                                buffer.delete(0, buffer.length() - 200); // Keep last 200 chars

                            String text = buffer.toString().trim();
                            if (text.endsWith(":") || text.endsWith("?")) {
                                SwingUtilities.invokeLater(() -> {
                                    inputPanel.setVisible(true);
                                    inputField.requestFocus();
                                });
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
                outputThread.start();

            } else {
                consoleArea.append("Compilation failed.\n");
            }
        } catch (Exception e) {
            consoleArea.append("Error: " + e.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new CodeEditor().setVisible(true));
    }

    // Expand or collapse entire tree
    private void expandAll(JTree tree, boolean expand) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        expandAll(new TreePath(root), expand, tree);
    }

    private void expandAll(TreePath parent, boolean expand, JTree tree) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements();) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                expandAll(path, expand, tree);
            }
        }
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }

    // Find a tree node by file name and select it
    private void selectFileInTree(File fileToSelect) {
        DefaultTreeModel model = (DefaultTreeModel) fileTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        // BFS to find a node whose toString() equals file name (this matches
        // buildFileTree)
        DefaultMutableTreeNode found = null;
        java.util.Queue<DefaultMutableTreeNode> q = new java.util.LinkedList<>();
        q.add(root);
        while (!q.isEmpty()) {
            DefaultMutableTreeNode node = q.remove();
            if (fileToSelect.getName().equals(node.getUserObject().toString())) {
                found = node;
                break;
            }
            for (int i = 0; i < node.getChildCount(); i++)
                q.add((DefaultMutableTreeNode) node.getChildAt(i));
        }

        if (found != null) {
            TreePath path = new TreePath(found.getPath());
            fileTree.setSelectionPath(path);
            fileTree.scrollPathToVisible(path);
        }
    }

}

// Run Command:
// java -cp ".;lib/rsyntaxtextarea-3.3.4.jar" CodeEditor
