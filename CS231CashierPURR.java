package cs231cashierpurr;

import java.sql.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// This is to certify that this project is my work based on my personal
// efforts in studying and applying the concepts learned. I have 
// constructed the functions, their respective algorithms, and 
// corresponding code by myself. The program was run, tested, and debugged 
// by my efforts. I further certify that I have not copied in part or 
// whole or otherwise plagiarized the work of other students and/or 
// persons.
// <Kezzia Naomi>, CCC ID# <2024-01314> 
// Last modified: Deember 4, 2025
// Acknowledgements: Java Swing library, standard VAT/Discount calculation models, MySQL JDBC.


/**
 * Helper class for managing the MySQL database connection.
 */
class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/purrcashiercs231";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // 

    /**
     * Establishes and returns a new database connection.
     * @return A valid Connection object, or null if connection fails.
     */
    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            System.err.println("Database Connection Failed: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "Database connection failed. Please check your credentials and ensure MySQL is running.", 
                "DB Error", 
                JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}

/**
 * Represents a single item in the menu.
 */
class Item {
    private final int id;
    private final double priceWithVAT; 
    private final String name;
    static final double VAT_RATE = 0.12; 

    public Item(int id, String name, double priceWithVAT) {
        this.id = id;
        this.name = name;
        this.priceWithVAT = priceWithVAT;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPriceWithVAT() { return priceWithVAT; }

    /**
     * Calculates the price before VAT.
     * Base Price = PriceWithVAT / (1 + VAT_RATE)
     */
    public double getBasePrice() {
        return priceWithVAT / (1 + VAT_RATE);
    }
}

/**
 * Handles all menu loading and financial calculations.
 */
class FinancialCalculator {
    private static final double SENIOR_PWD_DISCOUNT_RATE = 0.15;
    private final Map<Integer, Item> menu;

    public FinancialCalculator() {
        menu = loadMenuFromDatabase();
        if (menu.isEmpty()) {
            JOptionPane.showMessageDialog(null, 
                "Failed to load menu from database. Using hardcoded defaults.", 
                "Menu Load Error", 
                JOptionPane.WARNING_MESSAGE);
            
            menu.put(1, new Item(1, "Item 1", 950.00)); 
            menu.put(2, new Item(2, "Item 2", 399.00)); 
            menu.put(3, new Item(3, "Item 3", 250.50)); 
            menu.put(4, new Item(4, "Item 4", 89.90)); 
        }
    }
    
    /**
     * Loads the menu items from the 'items' table in the database.
     */
    private Map<Integer, Item> loadMenuFromDatabase() {
        Map<Integer, Item> dbMenu = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn == null) return dbMenu;

            String sql = "SELECT item_id, item_name, price_with_vat FROM items ORDER BY item_id";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    int id = rs.getInt("item_id");
                    String name = rs.getString("item_name");
                    double price = rs.getDouble("price_with_vat");
                    dbMenu.put(id, new Item(id, name, price));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading menu from DB: " + e.getMessage());
        }
        return dbMenu;
    }

    public Item getItem(int id) { return menu.get(id); }
    public Map<Integer, Item> getMenu() { return menu; }

    /**
     * Calculates the total financial breakdown based on quantities and discount status.
     * Applies discount or VAT, but never both.
     */
    public Map<String, Double> calculateTransaction(Map<Integer, Integer> quantities, boolean isDiscounted) {
        Map<String, Double> results = new HashMap<>();
        double totalBasePrice = 0.0; // Total price of items, exclusive of VAT

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            Item item = menu.get(entry.getKey());
            if (item != null) {
                totalBasePrice += item.getBasePrice() * entry.getValue();
            }
        }

        double discountAmount = 0.0;
        double vatAmount = 0.0;

        if (isDiscounted) {
            // Senior/PWD discount (15%) is applied to the Base Price (VAT-exempt logic)
            discountAmount = totalBasePrice * SENIOR_PWD_DISCOUNT_RATE;
        } else {
            // VAT is applied to the Base Price
            vatAmount = totalBasePrice * Item.VAT_RATE;
        }

        // Total = Base Price - Discount + VAT (Note: Discount and VAT are mutually exclusive)
        double total = totalBasePrice - discountAmount + vatAmount;

        results.put("subtotal", totalBasePrice);
        results.put("discount_amount", discountAmount);
        results.put("vat_amount", vatAmount);
        results.put("total", total);

        return results;
    }
}

/**
 * Main application class extending JFrame.
 */
public class cs231cashierpurr extends JFrame {
    private final FinancialCalculator calculator = new FinancialCalculator();
    private final DecimalFormat df = new DecimalFormat("#,##0.00"); 

    private JTextField[] quantityFields;
    private JCheckBox seniorPwdDiscount;
    private JTextField paymentField;
    private JButton processButton, logoutButton;
    private JLabel totalLabel;
    
    private String currentCashierName = null;
    private int transactionCounter = 0; 

    public cs231cashierpurr() {
        setTitle("Cashier Simulator - Logged Out");
        
        setSize(650, 650); 

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (currentCashierName != null) {
                    JOptionPane.showMessageDialog(cs231cashierpurr.this, "Please log out first before exiting.", "Cannot Exit", JOptionPane.WARNING_MESSAGE);
                } else {
                    System.exit(0);
                }
            }
        });
        
        setupUI();
        addActionListeners();
        
        setVisible(false);
        showLoginScreen(); 
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(25, 25)); // Increased gaps
        mainPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        // --- HEADER PANEL FOR STORE NAME (NORTH) ---
        JLabel storeNameLabel = new JLabel("☕ PURR COFFEE SHOP", SwingConstants.CENTER);
        storeNameLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        storeNameLabel.setForeground(new Color(100, 50, 0)); // Coffee color
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(storeNameLabel, BorderLayout.CENTER);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0)); 

        mainPanel.add(headerPanel, BorderLayout.NORTH); 
        // --- END HEADER PANEL ---
        
        // --- INPUT FIELDS PANEL (CENTER) ---
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 15, 15)); 
        inputPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "🛒 Transaction Input", 0, 2, new Font("SansSerif", Font.BOLD, 14)));
        
        // Dynamically create fields based on the menu loaded from DB/Fallback
        int menuSize = calculator.getMenu().size();
        quantityFields = new JTextField[menuSize];
        
        // Use an array index to link fields to items
        int i = 0;
        for (Item item : calculator.getMenu().values()) {
            String itemLabel = "<html><b>#" + item.getId() + " " + item.getName() + "</b><br>(₱" + df.format(item.getPriceWithVAT()) + ") Qty:</html>";
            inputPanel.add(new JLabel(itemLabel));
            quantityFields[i] = new JTextField("0");
            quantityFields[i].setFont(new Font("SansSerif", Font.BOLD, 16));
            inputPanel.add(quantityFields[i]);
            i++;
        }
        
        seniorPwdDiscount = new JCheckBox("Apply Senior/PWD Discount (15%)");
        inputPanel.add(seniorPwdDiscount);
        inputPanel.add(new JLabel("")); // Spacer

        // Customer Paid Input
        inputPanel.add(new JLabel("Customer Paid (₱):"));
        paymentField = new JTextField();
        paymentField.setFont(new Font("SansSerif", Font.BOLD, 16));
        inputPanel.add(paymentField);

        // TOTAL DUE Label
        inputPanel.add(new JLabel("TOTAL DUE:"));
        totalLabel = new JLabel("₱0.00", SwingConstants.RIGHT); // Align right for amount
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 26)); 
        totalLabel.setForeground(Color.RED);
        inputPanel.add(totalLabel);
        
        mainPanel.add(inputPanel, BorderLayout.CENTER); 
        // --- END INPUT FIELDS PANEL ---


        // --- BOTTOM BUTTONS PANEL (SOUTH) ---
        JPanel bottomPanel = new JPanel(new GridLayout(2, 1, 0, 10)); // 2 rows, 1 column, 10px vertical gap
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); 

        processButton = new JButton("💳 Process Transaction & Print Receipt");
        processButton.setFont(new Font("SansSerif", Font.BOLD, 16)); 
        
        logoutButton = new JButton("🚪 Log Out");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 16)); 

        // Primary action button (Process) takes the first row
        bottomPanel.add(processButton);
        
        // Secondary action button (Log Out) takes the second row
        bottomPanel.add(logoutButton);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        // --- END BOTTOM BUTTONS PANEL ---
        
        add(mainPanel);
        setLocationRelativeTo(null);
    }

    private void addActionListeners() {
        processButton.addActionListener(e -> processTransaction());
        logoutButton.addActionListener(e -> logOut());
        
        ActionListener recalculateListener = e -> calculateTotalDisplay();
        for (JTextField field : quantityFields) {
            field.addActionListener(recalculateListener);
            field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { recalculateListener.actionPerformed(null); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { recalculateListener.actionPerformed(null); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { recalculateListener.actionPerformed(null); }
            });
        }
        seniorPwdDiscount.addActionListener(recalculateListener);
    }
    
    private void calculateTotalDisplay() {
        Map<Integer, Integer> quantities = getValidatedQuantities(false);
        if (quantities == null) {
            totalLabel.setText("Error");
            return;
        }
        
        boolean isDiscounted = seniorPwdDiscount.isSelected();
        Map<String, Double> financials = calculator.calculateTransaction(quantities, isDiscounted);
        double totalDue = financials.get("total");
        
        totalLabel.setText("₱" + df.format(totalDue));
    }

    private void showLoginScreen() {
        Color darkBrown = new Color(111, 78, 55); 

        while (currentCashierName == null) {
            
            JDialog loginDialog = new JDialog(this, "Cashier Login", true); 
            loginDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            loginDialog.setLocationRelativeTo(null); 
            
            // Title Label
            JLabel titleLabel = new JLabel("USER LOGIN", SwingConstants.CENTER);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 60)); 
            titleLabel.setForeground(Color.getHSBColor(52, 45, 93));
            
            // 1. Password Field Setup 
            JPasswordField passwordField = new JPasswordField(10); 
            passwordField.setFont(new Font("SansSerif", Font.PLAIN, 28 )); 

            JCheckBox showPassword = new JCheckBox("Show Password");
            showPassword.setFont(new Font("SansSerif", Font.PLAIN, 20));
            showPassword.setForeground(Color.getHSBColor(52, 45, 93)); 
            showPassword.setBackground(darkBrown); 

            showPassword.addActionListener(e -> {
                if (showPassword.isSelected()) {
                    passwordField.setEchoChar((char) 0); 
                } else {
                    passwordField.setEchoChar('*'); 
                }
            });
            
            // Panel to combine the password field and checkbox
            JPanel passwordEntryPanel = new JPanel(new BorderLayout(15, 0)); 
            passwordEntryPanel.add(passwordField, BorderLayout.CENTER);
            passwordEntryPanel.add(showPassword, BorderLayout.EAST); 
            passwordEntryPanel.setBackground(darkBrown); 

            // 2. Username Field Setup 
            JTextField usernameField = new JTextField(10); 
            usernameField.setFont(new Font("SansSerif", Font.PLAIN, 28)); 
            
            // 4. The main input panel using GridBagLayout
            JPanel inputPanel = new JPanel(new GridBagLayout()); 
            inputPanel.setBackground(darkBrown); 
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(25, 25, 25, 25); // Padding around components

            // --- First Row (Username Label) ---
            gbc.gridx = 0; 
            gbc.gridy = 0; 
            gbc.anchor = GridBagConstraints.EAST; 
            JLabel usernameLabel = new JLabel("Cashier Username:");
            usernameLabel.setFont(new Font("SansSerif", Font.PLAIN, 28));
            usernameLabel.setForeground(Color.getHSBColor(52, 45, 93));
            inputPanel.add(usernameLabel, gbc);

            // --- First Row (Username Field) ---
            gbc.gridx = 1; 
            gbc.gridy = 0; 
            gbc.anchor = GridBagConstraints.WEST; 
            gbc.fill = GridBagConstraints.HORIZONTAL; 
            gbc.weightx = 1.0; 
            inputPanel.add(usernameField, gbc); 

            // --- Second Row (Password Label) ---
            gbc.gridx = 0; 
            gbc.gridy = 1; 
            gbc.anchor = GridBagConstraints.EAST; 
            JLabel passwordLabel = new JLabel("Password:");
            passwordLabel.setFont(new Font("SansSerif", Font.PLAIN, 28));
            passwordLabel.setForeground(Color.getHSBColor(52, 45, 93)); 
            inputPanel.add(passwordLabel, gbc);

            // --- Second Row (Password Field Panel) ---
            gbc.gridx = 1; 
            gbc.gridy = 1; 
            gbc.anchor = GridBagConstraints.WEST; 
            gbc.fill = GridBagConstraints.HORIZONTAL; 
            gbc.weightx = 1.0; 
            inputPanel.add(passwordEntryPanel, gbc); 


            JPanel centerPanel = new JPanel(new GridBagLayout()); 
            centerPanel.add(inputPanel);
            centerPanel.setBackground(darkBrown); 

            JButton loginButton = new JButton("Login");
            loginButton.setFont(new Font("SansSerif", Font.BOLD, 28));
            JButton exitButton = new JButton("Exit");
            exitButton.setFont(new Font("SansSerif", Font.BOLD, 28));

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 20)); 
            buttonPanel.add(loginButton);
            buttonPanel.add(exitButton);
            buttonPanel.setBackground(darkBrown); 
            
            JPanel mainLoginPanel = new JPanel(new BorderLayout(0, 50));
            mainLoginPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
            mainLoginPanel.add(titleLabel, BorderLayout.NORTH);
            mainLoginPanel.add(centerPanel, BorderLayout.CENTER);
            mainLoginPanel.add(buttonPanel, BorderLayout.SOUTH); 
            mainLoginPanel.setBackground(darkBrown); 
            
            loginDialog.getContentPane().setBackground(darkBrown);
            loginDialog.add(mainLoginPanel);
            
            loginDialog.pack(); 
            
            // Exit Button Action
            exitButton.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(loginDialog, "Do you want to Exit the program?", "Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            });

            // Login Button Action (uses database for validation)
            final boolean[] loggedIn = {false};
            loginButton.addActionListener(e -> {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                
                try (Connection conn = DatabaseManager.getConnection()) {
                    if (conn == null) {
                        return; // Error already shown by DatabaseManager
                    }
                    
                    // Simple check against the users table. 
                    String sql = "SELECT username FROM users WHERE username = ? AND password_hash = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        stmt.setString(2, password);
                        
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                currentCashierName = rs.getString("username"); // Get username from DB
                                loggedIn[0] = true;
                                loginDialog.dispose(); 
                            } else {
                                JOptionPane.showMessageDialog(loginDialog, "Invalid credentials. Please try again.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(loginDialog, "Database error during login: " + ex.getMessage(), "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            loginDialog.setVisible(true); 
            
            if (loggedIn[0]) {
                setTitle("Cashier Simulator - Logged In as: " + currentCashierName);
                setVisible(true);
                calculateTotalDisplay();
                break;
            }
        }
    }

    private void logOut() {
        if (currentCashierName == null) return;

        // 1. Generate and display the sales summary before logging out
        generateSalesSummary(); 
        
        // 2. Complete logout process
        currentCashierName = null;
        transactionCounter = 0; 
        
        setTitle("Cashier Simulator - Logged Out");
        setVisible(false);
        showLoginScreen(); 
    }
    
    /**
     * Retrieves and displays the sales summary for the current cashier from the database.
     */
    private void generateSalesSummary() {
        if (currentCashierName == null) return;

        int totalOrders = 0;
        double totalEarnings = 0.0;
        Map<String, Integer> itemBreakdown = new HashMap<>();

        // --- 1. Query Summary Totals (Total Orders and Total Earnings) ---
        String summarySql = "SELECT COUNT(transaction_id) AS total_orders, SUM(total_due) AS total_earnings FROM transactions WHERE cashier_username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(summarySql)) {
            
            stmt.setString(1, currentCashierName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalOrders = rs.getInt("total_orders");
                    totalEarnings = rs.getDouble("total_earnings");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error fetching sales summary totals from DB: " + e.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- 2. Query Item Breakdown (Total Items Purchased and Item Quantities) ---
        // Joins transactions (to filter by cashier), transaction_items (to get item qty), and items (to get item name)
        String breakdownSql = "SELECT i.item_name, SUM(ti.quantity) AS total_qty FROM transactions t JOIN transaction_items ti ON t.transaction_id = ti.transaction_id JOIN items i ON ti.item_id = i.item_id WHERE t.cashier_username = ? GROUP BY i.item_name ORDER BY i.item_id";
        
        int totalItemsPurchased = 0;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(breakdownSql)) {
            
            stmt.setString(1, currentCashierName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String itemName = rs.getString("item_name");
                    int qty = rs.getInt("total_qty");
                    itemBreakdown.put(itemName, qty);
                    totalItemsPurchased += qty;
                }
            }

            // --- 3. Format and Display the Sales Summary ---
            StringBuilder summary = new StringBuilder();
            summary.append("CLOSING CASH REGISTER REPORT\n");
            summary.append("========================================\n");
            summary.append("Closing cash register manned by ").append(currentCashierName).append("\n");
            summary.append("Total orders: ").append(totalOrders).append("\n");
            summary.append("Total items purchased: ").append(totalItemsPurchased).append("\n");
            summary.append("----------------------------------------\n");
            summary.append("Breakdown of items purchased:\n");
            
            // Append breakdown 
            for (Map.Entry<String, Integer> entry : itemBreakdown.entrySet()) {
                summary.append(String.format("  - %-20s %d pcs\n", entry.getKey(), entry.getValue()));
            }

            summary.append("----------------------------------------\n");
            summary.append(String.format("Total earnings: ₱%s\n", df.format(totalEarnings)));
            summary.append("========================================\n");

            // Display using JOptionPane
            JTextArea summaryArea = new JTextArea(summary.toString());
            summaryArea.setEditable(false);
            summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 14)); 

            JOptionPane.showMessageDialog(this, 
                new JScrollPane(summaryArea), 
                "Sales Summary: " + currentCashierName, 
                JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error fetching item breakdown from DB: " + e.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private Map<Integer, Integer> getValidatedQuantities(boolean showErrors) {
        Map<Integer, Integer> quantities = new HashMap<>();
        try {
            int i = 0;
            // Iterate over the menu keys (Item IDs) to maintain order and linkage with text fields
            for (Map.Entry<Integer, Item> entry : calculator.getMenu().entrySet()) {
                String text = quantityFields[i].getText().trim();
                int qty = text.isEmpty() ? 0 : Integer.parseInt(text);
                
                if (qty < 0) {
                    if (showErrors) JOptionPane.showMessageDialog(this, "Quantity for " + entry.getValue().getName() + " cannot be negative.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
                quantities.put(entry.getKey(), qty);
                i++;
            }
            return quantities;
        } catch (NumberFormatException ex) {
            if (showErrors) JOptionPane.showMessageDialog(this, "Invalid input for item quantity. Please enter numbers only.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void processTransaction() {
        if (currentCashierName == null) {
            JOptionPane.showMessageDialog(this, "Please log in first.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<Integer, Integer> quantities = getValidatedQuantities(true);
        if (quantities == null) return;

        if (quantities.values().stream().mapToInt(Integer::intValue).sum() == 0) {
              JOptionPane.showMessageDialog(this, "Please enter a quantity for at least one item.", "Input Error", JOptionPane.ERROR_MESSAGE);
              return;
        }
        
        boolean isDiscounted = seniorPwdDiscount.isSelected();
        Map<String, Double> financials = calculator.calculateTransaction(quantities, isDiscounted);
        double totalDue = financials.get("total");
        
        double customerPayment = 0.0;
        try {
            String text = paymentField.getText().trim();
            if (text.isEmpty()) throw new NumberFormatException();
            customerPayment = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid payment amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (customerPayment < totalDue) {
            JOptionPane.showMessageDialog(this, "Payment (₱" + df.format(customerPayment) + ") is less than the total amount due (₱" + df.format(totalDue) + ").", "Insufficient Payment", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double change = customerPayment - totalDue;
        
        // **DATABASE: SAVE TRANSACTION**
        int transactionId = saveTransactionToDatabase(quantities, financials, customerPayment, change, isDiscounted);
        
        if (transactionId > 0) {
             transactionCounter++;
             showReceiptDialog(quantities, financials, customerPayment, change);
        } else {
             JOptionPane.showMessageDialog(this, "Failed to save transaction to database. Receipt not generated.", "DB Error", JOptionPane.ERROR_MESSAGE);
             return;
        }
        
        // Reset inputs
        for (JTextField field : quantityFields) {
            field.setText("0");
        }
        seniorPwdDiscount.setSelected(false);
        paymentField.setText("");
        calculateTotalDisplay();
    }

    /**
     * Helper method to save the transaction and its items to the database.
     * @return The generated transaction_id, or -1 on failure.
     */
    private int saveTransactionToDatabase(Map<Integer, Integer> quantities, Map<String, Double> financials, double cash, double change, boolean isDiscounted) {
        int transactionId = -1;
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            if (conn == null) return -1;
            conn.setAutoCommit(false); // Start transaction block
            
            // 1. Insert into transactions table
            String insertTransSql = "INSERT INTO transactions (cashier_username, is_discounted, subtotal_base, vat_amount, discount_amount, total_due, cash_paid, change_due) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertTransSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, currentCashierName);
                stmt.setBoolean(2, isDiscounted);
                stmt.setDouble(3, financials.get("subtotal"));
                stmt.setDouble(4, financials.get("vat_amount"));
                stmt.setDouble(5, financials.get("discount_amount"));
                stmt.setDouble(6, financials.get("total"));
                stmt.setDouble(7, cash);
                stmt.setDouble(8, change);
                
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        transactionId = rs.getInt(1);
                    } else {
                        throw new SQLException("Transaction insertion failed, no ID obtained.");
                    }
                }
            }

            // 2. Insert into transaction_items table
            String insertItemsSql = "INSERT INTO transaction_items (transaction_id, item_id, quantity, price_at_sale) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertItemsSql)) {
                for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
                    int qty = entry.getValue();
                    if (qty > 0) {
                        Item item = calculator.getItem(entry.getKey());
                        // Price recorded is the price used for line total on the receipt
                        double itemPrice = isDiscounted ? item.getBasePrice() : item.getPriceWithVAT();
                        
                        stmt.setInt(1, transactionId);
                        stmt.setInt(2, entry.getKey());
                        stmt.setInt(3, qty);
                        stmt.setDouble(4, itemPrice);
                        stmt.addBatch(); // Batch insertion
                    }
                }
                stmt.executeBatch();
            }
            
            conn.commit(); // Commit the transaction
            return transactionId;
            
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                if (conn != null) conn.rollback(); // Rollback on error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return -1;
        } finally {
            try {
                if (conn != null) conn.close(); // Close connection
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Displays the official receipt in a separate, modal dialog.
     */
    private void showReceiptDialog(Map<Integer, Integer> quantities, Map<String, Double> financials, double cash, double change) {
        // Use the actual transaction ID from the database for the receipt title if available
        int orderNumber = transactionCounter; 
        JDialog receiptDialog = new JDialog(this, "Official Receipt - Order #" + orderNumber, true);
        receiptDialog.setLayout(new BorderLayout());
        
        JTextArea receiptArea = new JTextArea(25, 35);
        receiptArea.setEditable(false);
        receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 14)); 
        receiptArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Generate the receipt content
        StringBuilder receipt = generateReceiptContent(quantities, financials, cash, change);
        receiptArea.setText(receipt.toString());
        
        // Button to close the dialog
        JButton printButton = new JButton("✅ OK / Close Receipt");
        printButton.addActionListener(e -> receiptDialog.dispose());

        receiptDialog.add(new JScrollPane(receiptArea), BorderLayout.CENTER);
        receiptDialog.add(printButton, BorderLayout.SOUTH);
        
        receiptDialog.pack();
        receiptDialog.setLocationRelativeTo(this);
        receiptDialog.setVisible(true);
    }

    /**
     * Generates the formatted receipt text.
     */
    private StringBuilder generateReceiptContent(Map<Integer, Integer> quantities, Map<String, Double> financials, double cash, double change) {
        StringBuilder receipt = new StringBuilder();
        boolean isDiscounted = seniorPwdDiscount.isSelected(); 

        // Headers (Matches sample: EMTC1CT Business, Address, Cashier Name)
        receipt.append("          Purr Coffee Shop\n"); // Matches sample header
        receipt.append("          Calamba Laguna\n\n"); // Updated address
        receipt.append("Cashier name: ").append(currentCashierName).append("\n"); // Matches sample
        receipt.append("\n"); // Extra line break for spacing

        // Item List (Matches sample: Item X x Y: Z.ZZ)
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            int qty = entry.getValue();
            if (qty > 0) {
                Item item = calculator.getItem(entry.getKey());
                
                // Item price in line totals: 
                double itemPrice = isDiscounted ? item.getBasePrice() : item.getPriceWithVAT();
                double lineTotal = itemPrice * qty;
                
                // Formatting to match: Item X x Y: Z.ZZ
                receipt.append(String.format("%s x %d: %s\n", item.getName(), qty, df.format(lineTotal))); 
            }
        }
        
        receipt.append("\n"); // Extra line break for spacing

        // Totals Breakdown (Matches sample formatting)
        receipt.append(String.format("%-20s %s\n", "Subtotal:", df.format(financials.get("subtotal")))); 
        
        // Discount/VAT Lines
        if (isDiscounted) {
            // Senior/PWD Discount (15% on base price)
            receipt.append(String.format("%-20s %s\n", "Senior/PWD discount:", df.format(financials.get("discount_amount"))));
            // VAT amount is 0.00 since it is waived [cite: 16]
            receipt.append(String.format("%-20s %s\n", "VAT amount:", df.format(0.00))); 
        } else {
            // Discount is 0.00
            receipt.append(String.format("%-20s %s\n", "Senior/PWD discount:", df.format(0.00)));
            // VAT is applied [cite: 8]
            receipt.append(String.format("%-20s %s\n", "VAT amount:", df.format(financials.get("vat_amount"))));
        }

        receipt.append("======\n"); // Separator line matching the sample
        
        // Total and Payment Lines
        int totalItems = quantities.values().stream().mapToInt(Integer::intValue).sum();
        receipt.append(String.format("%-20s %d\n", "Items Purchased:", totalItems)); // Added for completeness, if not needed remove this line
        receipt.append(String.format("%-20s %s\n", "Total:", df.format(financials.get("total")))); 
        receipt.append(String.format("%-20s %s\n", "Cash:", df.format(cash)));
        receipt.append(String.format("%-20s %s\n", "Change:", df.format(change)));
        receipt.append("===================================\n"); // Final separator
        
        return receipt;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(cs231cashierpurr::new);
    }
}