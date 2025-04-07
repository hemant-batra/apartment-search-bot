package com.apartment.bot;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ParariusScraper {

    private final List<String> HEARTBEAT_MESSAGES = List.of(
            "💤 मुझे सोना है",
            "🍵 अदरक वाली चाय",
            "🍦 आइसक्रीम खानी है",
            "🔍 घर ढूँढ रही हूँ",
            "😴 आराम ज़रूरी है",
            "😋 छोले भटूरे",
            "🙌 घर मिलना तय है",
            "🌙 सपनों में खो जाओ",
            "🍵 चाय और समोसा",
            "🐢 धीरे चलो, दूर तक जाओ",
            "🧸 टेडी को भी घर चाहिए",
            "🧘 शांति ही असली लग्ज़री है",
            "📅 एक दिन तो मिलेगा घर",
            "🧭 रस्ता लंबा है, दिल मजबूत है",
            "🌟 शुभकामनाएँ",
            "🏠 घर ज़रूर मिलेगा",
            "🍽️ भोजन का समय",
            "🌈 अच्छे दिन आएँगे",
            "😴 नींद आ रही है",
            "🍛 भूख लगी है",
            "💪 मैं घर ढूँढ लूँगा!");

    private final AtomicInteger counter = new AtomicInteger(0);

    private final List<String> CITIES = Arrays.asList("wageningen", "ede", "arnhem", "bennekom", "veenendaal");
    private final int MAX_RENT = 1300;

    private final String DB_URL = "jdbc:postgresql://dpg-cvknac24d50c73dtk7ag-a.frankfurt-postgres.render.com/apartment_search";
    private final String DB_USER = "apartment_search";
    private final String DB_PASSWORD = "Il6u68sRDJ1dgOMsXo9FmspMqXgMpjaC";

    private LogUtil logUtil;
    private PushoverNotifier notifier;

    public ParariusScraper(LogUtil logUtil, PushoverNotifier notifier) {
        this.logUtil = logUtil;
        this.notifier = notifier;
    }

    public List<String> perform(String action, Integer id) {
        logUtil.clearLogs();
        switch (action) {
            case "SEARCH" -> CITIES.forEach(this::searchCity);
            case "HEARTBEAT" -> heartbeat();
            case "RESET" -> clearHistory();
            case "DELETE" -> deleteRowWithId(id);
            default -> {
                String message = "Invalid action: %s. Expected one of SEARCH | HEARTBEAT | RESET | DELETE";
                logUtil.println(String.format(message, action));
            }
        }
        return logUtil.getLogs();
    }

    private void deleteRowWithId(Integer id) {
        if (id == null) {
            logUtil.println("❗ Id is null, deletion aborted.");
            return;
        }

        String deleteSQL = "DELETE FROM notified_apartments WHERE ID = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {

            stmt.setInt(1, id); // Use parameterized query to prevent SQL injection

            int rowsAffected = stmt.executeUpdate();
            String message = switch (rowsAffected) {
                case 0 -> "⚠ No records found with ID: " + id;
                case 1 -> "🗑️ Deleted record with ID " + id;
                default -> "⚠ Unexpected! Rows deleted = " + rowsAffected;
            };

            logUtil.println(message);
            notifier.queueNotification(message);

        } catch (SQLException e) {
            String message = "❌ Failed to delete record with ID " + id + " - Error: " + e.getMessage();
            logUtil.println(message);
            notifier.queueNotification(message);
        }
    }

    private void clearHistory() {
        String truncateSQL = "TRUNCATE TABLE notified_apartments RESTART IDENTITY";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(truncateSQL)) {

            stmt.executeUpdate(); // TRUNCATE does not return affected rows
            String message = "\uD83D\uDDD1\uFE0F Cleared history.";
            logUtil.println(message);
            notifier.queueNotification(message);

        } catch (SQLException e) {
            String message = "❌ Failed to clear history: " + e.getMessage();
            logUtil.println(message);
            notifier.queueNotification(message);
        }
    }

    private void heartbeat() {
        if (LocalTime.now().getHour() == 19 && LocalTime.now().getMinute() > 40) {
            if (testDatabaseConnection()) {
                int index = counter.getAndUpdate(i -> (i + 1) % HEARTBEAT_MESSAGES.size());
                notifier.queueNotification(HEARTBEAT_MESSAGES.get(index));
                //notifier.notifyDeveloper(HEARTBEAT_MESSAGES.get(index));
            } else {
                notifier.queueNotification("❌ Unable to connect to the database!");
            }
        }
    }

    private boolean testDatabaseConnection() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            Optional.ofNullable(sendMail(conn)).ifPresent(errorMessage -> notifier.queueNotification("❌ " + errorMessage));
            return true;
        } catch (SQLException e) {
            logUtil.println("❌ Database connection failed: " + e.getMessage());
            return false;
        }
    }

    private void searchCity(String city) {
        String url = String.format("https://www.pararius.com/apartments/%s/200-%d", city, MAX_RENT);
        logUtil.println("\n🔍 Searching apartments in: " + capitalize(city));
        try {
            scrapeCity(city, url);
        } catch (IOException e) {
            logUtil.println("❗ Error fetching " + city + ": " + e.getMessage());
        }
    }

    private void scrapeCity(String city, String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(10000)
                .get();

        Elements listings = doc.select("section.listing-search-item");
        if (listings.isEmpty()) {
            logUtil.println("No listings found.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (Element listing : listings) {
                Element titleElement = listing.selectFirst("a.listing-search-item__link--title");
                String title = titleElement != null ? titleElement.text().trim() : "N/A";
                String link = titleElement != null ? "https://www.pararius.com" + titleElement.attr("href") : "N/A";

                Element priceElement = listing.selectFirst("div.listing-search-item__price");
                String price = priceElement != null ? priceElement.text().trim() : "N/A";

                Element addressElement = listing.selectFirst("div[class^=listing-search-item__sub-title]");
                String address = addressElement != null ? addressElement.text().trim() : "N/A";

                if (isUrlNotified(conn, link)) {
                    logUtil.println("⏭ Skipping already notified: " + link);
                    continue;
                }

                String message = String.format("🌍 %s\n🏠 %s\n📍 %s\n💰 %s\n🔗 %s", capitalize(city), title, address, price, link);
                logUtil.println(message);
                logUtil.println("----------------------------");

                if (notifier.queueNotification(message)) {
                    storeNotifiedApartment(conn, capitalize(city), title, address, price, link);
                }
            }
        } catch (SQLException e) {
            logUtil.println("❗ Database error: " + e.getMessage());
        }
    }

    private String sendMail(Connection conn) {
        try {
            sendEmail(getAll(conn));
            return null;
        } catch (MessagingException | SQLException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private List<Map<String, String>> getAll(Connection conn) throws SQLException {
        List<Map<String, String>> apartments = new ArrayList<>();
        String query = "SELECT * FROM notified_apartments";

        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, String> apartment = new HashMap<>();
                apartment.put("id", String.valueOf(rs.getInt("id"))); // Convert id to String
                apartment.put("city", rs.getString("city"));
                apartment.put("place", rs.getString("place"));
                apartment.put("location", rs.getString("location"));
                apartment.put("price", rs.getString("price"));
                apartment.put("link", rs.getString("link"));
                apartments.add(apartment);
            }
        }
        return apartments;
    }

    private boolean isUrlNotified(Connection conn, String url) throws SQLException {
        String query = "SELECT 1 FROM notified_apartments WHERE link = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, url);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private void storeNotifiedApartment(Connection conn, String city, String place, String location, String price, String link) throws SQLException {
        String insert = "INSERT INTO notified_apartments (city, place, location, price, link) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            stmt.setString(1, city);
            stmt.setString(2, place);
            stmt.setString(3, location);
            stmt.setString(4, price);
            stmt.setString(5, link);
            stmt.executeUpdate();
        }
    }

    private String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private String generateHtmlTable(List<Map<String, String>> apartments) {
        TimeZone timeZone = TimeZone.getDefault(); // Gets the server's current timezone
        String tzName = timeZone.getDisplayName(false, TimeZone.LONG); // Full name of the timezone

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy hh:mm a"); // 12-hour format with AM/PM
        sdf.setTimeZone(timeZone);

        String timestamp = sdf.format(new Date()) + " (" + tzName + ")";

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
                .append("body { font-family: Arial, sans-serif; padding: 10px; }")
                .append("p { font-size: 14px; margin-bottom: 10px; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 14px; }")
                .append("th, td { border: 1px solid #dddddd; text-align: left; padding: 8px; }")
                .append("th { background-color: #f2f2f2; }")
                .append("tr:nth-child(even) { background-color: #f9f9f9; }")
                .append("a { color: #007BFF; text-decoration: none; }")
                .append("a:hover { text-decoration: underline; }")
                .append("</style></head><body>");

        html.append("<p><strong>Report generated on:</strong> ").append(timestamp).append("</p>");
        html.append("<table>");
        html.append("<tr>")
                .append("<th>ID</th>")
                .append("<th>City</th>")
                .append("<th>Place</th>")
                .append("<th>Location</th>")
                .append("<th>Price</th>")
                .append("</tr>");

        for (Map<String, String> apartment : apartments) {
            html.append("<tr>")
                    .append("<td><a href='").append(apartment.get("link")).append("' target='_blank'>").append(apartment.get("id")).append("</a></td>")
                    .append("<td>").append(apartment.get("city")).append("</td>")
                    .append("<td>").append(apartment.get("place")).append("</td>")
                    .append("<td>").append(apartment.get("location")).append("</td>")
                    .append("<td>").append(apartment.get("price").replaceAll("per month", "pm")).append("</td>")
                    .append("</tr>");
        }

        html.append("</table></body></html>");
        return html.toString();
    }

    private void sendEmail(List<Map<String, String>> apartments) throws MessagingException {
        String host = "smtp.gmail.com";
        //String password = "v9$Gx@3!LpZqW7#Y";
        String password = "ouml rerv wzyu mnbj";
        String from = "match4hb@gmail.com";
        String to = "batra.hemant@gmail.com, batra.aditi@gmail.com";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

        String subject = "%s apartments match criteria";
        message.setSubject(String.format(subject, apartments.size()));

        String htmlContent = generateHtmlTable(apartments);
        message.setContent(htmlContent, "text/html; charset=utf-8");

        Transport.send(message);
        logUtil.println("Email sent successfully to " + to);
    }

}
