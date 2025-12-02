// Import file handling classes
import java.io.*;
// Import file path utilities
import java.nio.file.*;
// Import date & time classes
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// Import collections like Map, List, ArrayList, etc.
import java.util.*;

public class BankSystem {

    // ==============================================================
    // ACCOUNT MODEL (Represents one bank account)
    // ==============================================================
    static class Account {
        private String accountNumber;       // Unique account number (ACC1, ACC2)
        private String customerName;        // Owner name
        private double balance;             // Current balance
        private double dailyWithdrawLimit = 20000; // Max withdraw per day
        private double withdrawnToday = 0;  // Track amount withdrawn for the day
        private String lastInterestApplied = "";   // Tracks last month interest applied

        // Constructor: for new accounts
        public Account(String acc, String name, double bal) {
            this.accountNumber = acc;       // Set account number
            this.customerName = name;       // Set owner name
            this.balance = bal;             // Set initial balance
        }

        // Constructor: used when loading from file
        public Account(String acc, String name, double bal, double w, String last) {
            this.accountNumber = acc;       // Account number
            this.customerName = name;       // Owner name
            this.balance = bal;             // Balance
            this.withdrawnToday = w;        // Withdraw today
            this.lastInterestApplied = last == null ? "" : last; // Month interest last applied
        }

        // GETTERS
        public String getAccountNumber() { return accountNumber; }  // Return account #
        public String getCustomerName() { return customerName; }    // Return name
        public double getBalance() { return balance; }              // Return balance
        public double getWithdrawnToday() { return withdrawnToday; }// Return withdrawn today

        // Deposit money to balance
        public void deposit(double amt) { balance += amt; }

        // Try withdrawing money (returns error message if fail)
        public String tryWithdraw(double amt) {
            if (amt <= 0) return "Invalid amount."; // Negative or zero not allowed
            if (amt > balance) return "Not enough balance."; // Insufficient funds
            if (withdrawnToday + amt > dailyWithdrawLimit)  // Limit check
                return "Daily withdrawal limit reached.";

            balance -= amt;          // Reduce balance
            withdrawnToday += amt;   // Track today's withdrawal
            return null;             // Null = success
        }

        // Apply interest once per month
        public void applyMonthlyInterest(double rate, String ym) {
            if (ym.equals(lastInterestApplied)) return; // Already applied this month
            balance += balance * rate;                  // Add interest
            lastInterestApplied = ym;                   // Update record
        }

        // Convert account to file string format
        public String toFileString() {
            return accountNumber + "|" + customerName + "|" + balance + "|" +
                    withdrawnToday + "|" + lastInterestApplied; // Saveable format
        }

        // Load account from file string
        public static Account fromFileString(String line) {
            try {
                String[] p = line.split("\\|", -1);     // Split fields
                return new Account(p[0], p[1], Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]), p[4]); // Build account object
            } catch (Exception e) { return null; }       // Return null if invalid
        }
    }

    // ==============================================================
    // TRANSACTION MODEL (Represents one transaction)
    // ==============================================================
    static class Transaction {
        String acc;      // Account number
        String type;     // Type: DEPOSIT, WITHDRAW, INTEREST
        double amt;      // Amount
        String time;     // Timestamp
        String remark;   // Remarks

        // Constructor
        public Transaction(String a, String t, double am, String ti, String r) {
            acc = a; type = t; amt = am; time = ti; remark = r;
        }

        // Return current timestamp
        public static String now() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // Convert transaction to saveable format
        public String toFileString() {
            return acc + "|" + type + "|" + amt + "|" + time + "|" + remark;
        }

        // Read transaction from file
        public static Transaction fromFileString(String line) {
            String[] p = line.split("\\|", -1);
            return new Transaction(p[0], p[1], Double.parseDouble(p[2]), p[3], p[4]);
        }

        // Human readable transaction
        public String toString() {
            return "[" + time + "] " + type + " - " + amt + (remark.isEmpty() ? "" : " (" + remark + ")");
        }
    }

    // ==============================================================
    // FILE SERVICE (Saving & loading accounts and transactions)
    // ==============================================================
    static class FileService {
        static final Path DIR = Paths.get(System.getProperty("user.dir"), "data"); // Folder
        static final Path AFILE = DIR.resolve("accounts.txt"); // Account file
        static final Path TFILE = DIR.resolve("transactions.txt"); // Transaction file

        // Create folder/files if not exist
        static void ensure() {
            try {
                if (!Files.exists(DIR)) Files.createDirectories(DIR); // Create folder
                if (!Files.exists(AFILE)) Files.createFile(AFILE);     // Create acc file
                if (!Files.exists(TFILE)) Files.createFile(TFILE);     // Create tx file
            } catch (Exception ignored) {}
        }

        // Load accounts from file
        static Map<String, Account> loadAccounts() {
            ensure();
            Map<String, Account> map = new LinkedHashMap<>();
            try (BufferedReader br = Files.newBufferedReader(AFILE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Account a = Account.fromFileString(line); // Convert
                    if (a != null) map.put(a.getAccountNumber(), a); // Add to map
                }
            } catch (Exception ignored) {}
            return map;
        }

        // Load transactions from file
        static List<Transaction> loadTx() {
            ensure();
            List<Transaction> list = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(TFILE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Transaction t = Transaction.fromFileString(line); // Convert
                    if (t != null) list.add(t);                      // Add
                }
            } catch (Exception ignored) {}
            return list;
        }

        // Save accounts to file
        static void saveAccounts(Collection<Account> accs) {
            try (BufferedWriter bw = Files.newBufferedWriter(AFILE)) {
                for (Account a : accs) bw.write(a.toFileString() + "\n"); // Write each account
            } catch (Exception ignored) {}
        }

        // Save transaction history
        static void saveTx(Collection<Transaction> tx) {
            try (BufferedWriter bw = Files.newBufferedWriter(TFILE)) {
                for (Transaction t : tx) bw.write(t.toFileString() + "\n"); // Write each tx
            } catch (Exception ignored) {}
        }
    }

    // ==============================================================
    // BANK SERVICE (Main system logic)
    // ==============================================================
    static class BankService {
        Map<String, Account> accounts;    // Stores all accounts
        List<Transaction> history;        // Stores all transactions
        Scanner sc = new Scanner(System.in); // Scanner input

        final double INTEREST = 0.01;     // Monthly interest (1%)
        final double FRAUD = 0.80;        // Fraud warning if >80% balance withdraw

        // Constructor loads saved data
        public BankService() {
            accounts = FileService.loadAccounts();
            history = FileService.loadTx();
        }

        // Save all files
        void save() {
            FileService.saveAccounts(accounts.values());
            FileService.saveTx(history);
        }

        // -------------------- Select account by name --------------------
        private Account selectAccount() {
            if (accounts.isEmpty()) {                 // No accounts
                System.out.println("No accounts available!");
                return null;
            }

            List<Account> list = new ArrayList<>(accounts.values()); // Convert map to list

            System.out.println("\nSelect account:");
            for (int i = 0; i < list.size(); i++) {
                System.out.println((i + 1) + ". " + list.get(i).getCustomerName()); // Display names
            }

            System.out.print("Choose: ");
            int n;
            try {
                n = Integer.parseInt(sc.nextLine()); // User choice
            } catch (Exception e) {
                System.out.println("Invalid choice.");
                return null;
            }

            // Invalid input
            if (n < 1 || n > list.size()) {
                System.out.println("Invalid choice.");
                return null;
            }

            return list.get(n - 1); // Return chosen account
        }

        // -------------------- Create new account --------------------
        public void createAccount() {
            System.out.print("Enter owner name: ");
            String name = sc.nextLine();

            String accNum = "ACC" + (accounts.size() + 1); // Auto account number

            accounts.put(accNum, new Account(accNum, name, 0)); // Add to map

            save();
            System.out.println("Successfully created! Account Number: " + accNum);
        }

        // -------------------- Deposit money --------------------
        public void deposit() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.print("Enter amount: ");
            double amt = Double.parseDouble(sc.nextLine()); // Input amount

            a.deposit(amt); // Add balance

            history.add(new Transaction(a.getAccountNumber(), "DEPOSIT", amt, Transaction.now(), "")); // Save history

            save();
            System.out.println("Deposit successful!");
        }

        // -------------------- Withdraw money --------------------
        public void withdraw() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.print("Enter amount: ");
            double amt = Double.parseDouble(sc.nextLine());

            // Fraud check
            if (amt > a.getBalance() * FRAUD)
                System.out.println("⚠ POSSIBLE FRAUD DETECTED!");

            String msg = a.tryWithdraw(amt); // Try withdrawing
            if (msg != null) {
                System.out.println(msg); // Print error
                return;
            }

            history.add(new Transaction(a.getAccountNumber(), "WITHDRAW", amt, Transaction.now(), "")); // Log
            save();
            System.out.println("Withdraw successful!");
        }

        // -------------------- Check balance --------------------
        public void checkBalance() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.println("Balance of " + a.getCustomerName() + ": " + a.getBalance());
        }

        // -------------------- View Transaction History --------------------
        public void viewHistory() {
            Account a = selectAccount();
            if (a == null) return;

            for (Transaction t : history) {
                if (t.acc.equals(a.getAccountNumber())) System.out.println(t); // Print only their tx
            }
        }

        // -------------------- Apply monthly interest --------------------
        public void applyInterest() {
            String ym = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM")); // Year-Month

            for (Account a : accounts.values()) {
                a.applyMonthlyInterest(INTEREST, ym); // Apply interest
                history.add(new Transaction(
                        a.getAccountNumber(),
                        "INTEREST",
                        a.getBalance(),
                        Transaction.now(),
                        "Monthly interest"
                ));
            }

            save();
            System.out.println("Interest applied!");
        }

        // -------------------- List all accounts --------------------
        public void listAccounts() {
            System.out.println("\n=== ACCOUNTS ===");
            for (Account a : accounts.values()) {
                System.out.println(a.getAccountNumber() + " - " + a.getCustomerName() +
                        " | Balance: " + a.getBalance());
            }
        }

        // -------------------- Main Menu Loop --------------------
        public void start() {
            while (true) {
                System.out.println("\n===== BANK SYSTEM =====");
                System.out.println("1. Create Account");
                System.out.println("2. Deposit");
                System.out.println("3. Withdraw");
                System.out.println("4. Check Balance");
                System.out.println("5. Transaction History");
                System.out.println("6. Apply Monthly Interest");
                System.out.println("7. List Accounts");
                System.out.println("8. Save & Exit");
                System.out.print("Choose: ");

                String x = sc.nextLine();

                // Switch menu
                switch (x) {
                    case "1" -> createAccount();
                    case "2" -> deposit();
                    case "3" -> withdraw();
                    case "4" -> checkBalance();
                    case "5" -> viewHistory();
                    case "6" -> applyInterest();
                    case "7" -> listAccounts();
                    case "8" -> {
                        System.out.println("Exit");
                        save();
                        return; // Exit system
                    }
                    default -> System.out.println("Invalid option."); // Invalid input
                }
            }
        }
    }

    // ==============================================================
    // MAIN ENTRY (Program start)
    // ==============================================================
    public static void main(String[] args) {
        new BankService().start(); // Run system
    }
}
