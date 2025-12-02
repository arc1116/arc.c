import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BankSystem {

    // ==============================================================
    // ACCOUNT MODEL
    // ==============================================================
    static class Account {
        private String accountNumber;
        private String customerName;
        private double balance;
        private double dailyWithdrawLimit = 20000;
        private double withdrawnToday = 0;
        private String lastInterestApplied = "";

        public Account(String acc, String name, double bal) {
            this.accountNumber = acc;
            this.customerName = name;
            this.balance = bal;
        }

        public Account(String acc, String name, double bal, double w, String last) {
            this.accountNumber = acc;
            this.customerName = name;
            this.balance = bal;
            this.withdrawnToday = w;
            this.lastInterestApplied = last == null ? "" : last;
        }

        public String getAccountNumber() { return accountNumber; }
        public String getCustomerName() { return customerName; }
        public double getBalance() { return balance; }
        public double getWithdrawnToday() { return withdrawnToday; }

        public void deposit(double amt) { balance += amt; }

        public String tryWithdraw(double amt) {
            if (amt <= 0) return "Invalid amount.";
            if (amt > balance) return "Not enough balance.";
            if (withdrawnToday + amt > dailyWithdrawLimit)
                return "Daily withdrawal limit reached.";

            balance -= amt;
            withdrawnToday += amt;
            return null;
        }

        public void applyMonthlyInterest(double rate, String ym) {
            if (ym.equals(lastInterestApplied)) return;
            balance += balance * rate;
            lastInterestApplied = ym;
            
        }

        public String toFileString() {
            return accountNumber + "|" + customerName + "|" + balance + "|" +
                    withdrawnToday + "|" + lastInterestApplied;
        }

        public static Account fromFileString(String line) {
            try {
                String[] p = line.split("\\|", -1);
                return new Account(p[0], p[1], Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]), p[4]);
            } catch (Exception e) { return null; }
        }
    }

    // ==============================================================
    // TRANSACTION MODEL
    // ==============================================================
    static class Transaction {
        String acc;
        String type;
        double amt;
        String time;
        String remark;

        public Transaction(String a, String t, double am, String ti, String r) {
            acc = a; type = t; amt = am; time = ti; remark = r;
        }

        public static String now() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String toFileString() {
            return acc + "|" + type + "|" + amt + "|" + time + "|" + remark;
        }

        public static Transaction fromFileString(String line) {
            String[] p = line.split("\\|", -1);
            return new Transaction(p[0], p[1], Double.parseDouble(p[2]), p[3], p[4]);
        }

        public String toString() {
            return "[" + time + "] " + type + " - " + amt + (remark.isEmpty() ? "" : " (" + remark + ")");
        }
    }

    // ==============================================================
    // FILE SERVICE
    // ==============================================================
    static class FileService {
        static final Path DIR = Paths.get(System.getProperty("user.dir"), "data");
        static final Path AFILE = DIR.resolve("accounts.txt");
        static final Path TFILE = DIR.resolve("transactions.txt");

        static void ensure() {
            try {
                if (!Files.exists(DIR)) Files.createDirectories(DIR);
                if (!Files.exists(AFILE)) Files.createFile(AFILE);
                if (!Files.exists(TFILE)) Files.createFile(TFILE);
            } catch (Exception ignored) {}
        }

        static Map<String, Account> loadAccounts() {
            ensure();
            Map<String, Account> map = new LinkedHashMap<>();
            try (BufferedReader br = Files.newBufferedReader(AFILE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Account a = Account.fromFileString(line);
                    if (a != null) map.put(a.getAccountNumber(), a);
                }
            } catch (Exception ignored) {}
            return map;
        }

        static List<Transaction> loadTx() {
            ensure();
            List<Transaction> list = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(TFILE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Transaction t = Transaction.fromFileString(line);
                    if (t != null) list.add(t);
                }
            } catch (Exception ignored) {}
            return list;
        }

        static void saveAccounts(Collection<Account> accs) {
            try (BufferedWriter bw = Files.newBufferedWriter(AFILE)) {
                for (Account a : accs) bw.write(a.toFileString() + "\n");
            } catch (Exception ignored) {}
        }

        static void saveTx(Collection<Transaction> tx) {
            try (BufferedWriter bw = Files.newBufferedWriter(TFILE)) {
                for (Transaction t : tx) bw.write(t.toFileString() + "\n");
            } catch (Exception ignored) {}
        }
    }

    // ==============================================================
    // BANK SERVICE
    // ==============================================================
    static class BankService {
        Map<String, Account> accounts;
        List<Transaction> history;
        Scanner sc = new Scanner(System.in);

        final double INTEREST = 0.01;
        final double FRAUD = 0.80;

        public BankService() {
            accounts = FileService.loadAccounts();
            history = FileService.loadTx();
        }

        void save() {
            FileService.saveAccounts(accounts.values());
            FileService.saveTx(history);
        }

        // -------------------- ACCOUNT SELECTION BY NAME --------------------
        private Account selectAccount() {
            if (accounts.isEmpty()) {
                System.out.println("No accounts available!");
                return null;
            }

            List<Account> list = new ArrayList<>(accounts.values());

            System.out.println("\nSelect account:");
            for (int i = 0; i < list.size(); i++) {
                System.out.println((i + 1) + ". " + list.get(i).getCustomerName());
            }

            System.out.print("Choose: ");
            int n;
            try {
                n = Integer.parseInt(sc.nextLine());
            } catch (Exception e) {
                System.out.println("Invalid choice.");
                return null;
            }

            if (n < 1 || n > list.size()) {
                System.out.println("Invalid choice.");
                return null;
            }

            return list.get(n - 1);
        }

        // -------------------- CREATE ACCOUNT --------------------
        public void createAccount() {
            System.out.print("Enter owner name: ");
            String name = sc.nextLine();

            String accNum = "ACC" + (accounts.size() + 1);

            accounts.put(accNum, new Account(accNum, name, 0));

            save();
            System.out.println("Successfully created! Account Number: " + accNum);
        }

        // -------------------- DEPOSIT --------------------
        public void deposit() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.print("Enter amount: ");
            double amt = Double.parseDouble(sc.nextLine());

            a.deposit(amt);

            history.add(new Transaction(a.getAccountNumber(), "DEPOSIT", amt, Transaction.now(), ""));

            save();
            System.out.println("Deposit successful!");
        }

        // -------------------- WITHDRAW --------------------
        public void withdraw() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.print("Enter amount: ");
            double amt = Double.parseDouble(sc.nextLine());

            if (amt > a.getBalance() * FRAUD)
                System.out.println("⚠ POSSIBLE FRAUD DETECTED!");

            String msg = a.tryWithdraw(amt);
            if (msg != null) {
                System.out.println(msg);
                return;
            }

            history.add(new Transaction(a.getAccountNumber(), "WITHDRAW", amt, Transaction.now(), ""));
            save();
            System.out.println("Withdraw successful!");
        }

        // -------------------- CHECK BALANCE --------------------
        public void checkBalance() {
            Account a = selectAccount();
            if (a == null) return;

            System.out.println("Balance of " + a.getCustomerName() + ": " + a.getBalance());
        }

        // -------------------- HISTORY --------------------
        public void viewHistory() {
            Account a = selectAccount();
            if (a == null) return;

            for (Transaction t : history) {
                if (t.acc.equals(a.getAccountNumber())) System.out.println(t);
            }
        }

        // -------------------- INTEREST --------------------
        public void applyInterest() {
            String ym = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

            for (Account a : accounts.values()) {
                a.applyMonthlyInterest(INTEREST, ym);
                history.add(new Transaction(a.getAccountNumber(), "INTEREST", a.getBalance(),
                        Transaction.now(), "Monthly interest"));
            }

            save();
            System.out.println("Interest applied!");
        }

        // -------------------- LIST ACCOUNTS --------------------
        public void listAccounts() {
            System.out.println("\n=== ACCOUNTS ===");
            for (Account a : accounts.values()) {
                System.out.println(a.getAccountNumber() + " - " + a.getCustomerName() +
                        " | Balance: " + a.getBalance());
            }
        }

        // -------------------- START MENU --------------------
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
                        return;
                    }
                    default -> System.out.println("Invalid option.");
                }
            }
        }
    }

    // ==============================================================
    // MAIN ENTRY
    // ==============================================================
    public static void main(String[] args) {
        new BankService().start();
    }
}
