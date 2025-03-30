package com.apartment.bot;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class ParariusScraper {

    private static final List<String> CITIES = Arrays.asList("wageningen", "ede", "arnhem", "bennekom", "veenendaal");
    private static final int MAX_RENT = 1300;

    private static final String DB_URL = "jdbc:postgresql://dpg-cvknac24d50c73dtk7ag-a.frankfurt-postgres.render.com/apartment_search";
    private static final String DB_USER = "apartment_search";
    private static final String DB_PASSWORD = "Il6u68sRDJ1dgOMsXo9FmspMqXgMpjaC";

    private static final String PUSHOVER_USER_KEY = "u1kkpk442tbarr5dz1egtdfuumrngn";
    private static final String PUSHOVER_API_TOKEN = "ak1cvhpycz66kaymmiobmdyr6rbnpe";

    public static void main(String[] args) {
        if (args.length > 0) {
            String action = args[0];
            switch (action) {
                case "SEARCH" -> CITIES.forEach(ParariusScraper::searchCity);
                case "HEARTBEAT" -> heartbeat();
                case "RESET" -> clearHistory();
                default -> System.out.printf("Invalid action: %s. Expected one of SEARCH | HEARTBEAT | RESET", action);
            }
        } else {
            System.out.println("Usage: ParariusScraper SEARCH | HEARTBEAT | RESET");
        }
    }

    private static void clearHistory() {
        String deleteSQL = "DELETE FROM notified_apartments";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {

            int rowsAffected = stmt.executeUpdate();
            String message = "\uD83D\uDDD1\uFE0F Deleted " + rowsAffected + " records from history.";
            System.out.println(message);
            sendPushoverNotification(message);

        } catch (SQLException e) {
            String message = "❌ Failed to clear history: " + e.getMessage();
            System.out.println(message);
            sendPushoverNotification(message);
        }
    }

    private static void heartbeat() {
        if (testDatabaseConnection()) {
            sendPushoverNotification("❤️ I love searching apartments!");
        } else {
            sendPushoverNotification("❌ Unable to connect to the database!");
        }
    }

    private static boolean testDatabaseConnection() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            Optional.ofNullable(sendMail(conn)).ifPresent(errorMessage -> sendPushoverNotification("❌ " + errorMessage));
            return true;
        } catch (SQLException e) {
            System.out.println("❌ Database connection failed: " + e.getMessage());
            return false;
        }
    }

    private static void searchCity(String city) {
        String url = String.format("https://www.pararius.com/apartments/%s/200-%d", city, MAX_RENT);
        System.out.println("\n🔍 Searching apartments in: " + capitalize(city));
        try {
            scrapeCity(city, url);
        } catch (IOException e) {
            System.out.println("❗ Error fetching " + city + ": " + e.getMessage());
        }
    }

    private static void scrapeCity(String city, String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(10000)
                .get();

        Elements listings = doc.select("section.listing-search-item");
        if (listings.isEmpty()) {
            System.out.println("No listings found.");
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
                    System.out.println("⏭ Skipping already notified: " + link);
                    continue;
                }

                String message = String.format("🌍 %s\n🏠 %s\n📍 %s\n💰 %s\n🔗 %s", capitalize(city), title, address, price, link);
                System.out.println(message);
                System.out.println("----------------------------");

                if (sendPushoverNotification(message)) {
                    storeNotifiedApartment(conn, capitalize(city), title, address, price, link);
                }
            }
        } catch (SQLException e) {
            System.out.println("❗ Database error: " + e.getMessage());
        }
    }

    private static String sendMail(Connection conn) {
        try {
            sendEmail(getAll(conn));
            return null;
        } catch (MessagingException | SQLException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private static List<Map<String, String>> getAll(Connection conn) throws SQLException {
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

    private static boolean isUrlNotified(Connection conn, String url) throws SQLException {
        String query = "SELECT 1 FROM notified_apartments WHERE link = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, url);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private static void storeNotifiedApartment(Connection conn, String city, String place, String location, String price, String link) throws SQLException {
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

    private static boolean sendPushoverNotification(String message) {
        try {
            String urlString = "https://api.pushover.net/1/messages.json";
            String params = String.format("token=%s&user=%s&message=%s",
                    URLEncoder.encode(PUSHOVER_API_TOKEN, StandardCharsets.UTF_8),
                    URLEncoder.encode(PUSHOVER_USER_KEY, StandardCharsets.UTF_8),
                    URLEncoder.encode(message, StandardCharsets.UTF_8));

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("✅ Pushover notification sent successfully!");
                return true;
            } else {
                String responseMessage = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("❗ Failed to send Pushover notification, response code: " + responseCode);
                System.out.println("🔎 Pushover Response: " + responseMessage);
            }
        } catch (IOException e) {
            System.out.println("❗ Error sending Pushover notification: " + e.getMessage());
        }
        return false;
    }

    private static String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private static String generateHtmlTable(List<Map<String, String>> apartments) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<table border='1' cellpadding='5' cellspacing='0' style='border-collapse: collapse; width: 100%;'>");
        html.append("<tr>")
                .append("<th>ID</th>")
                .append("<th>City</th>")
                .append("<th>Place</th>")
                .append("<th>Location</th>")
                .append("<th>Price</th>")
                .append("<th>Details</th>")  // Link column
                .append("</tr>");

        for (Map<String, String> apartment : apartments) {
            html.append("<tr>")
                    .append("<td>").append(apartment.get("id")).append("</td>")
                    .append("<td>").append(apartment.get("city")).append("</td>")
                    .append("<td>").append(apartment.get("place")).append("</td>")
                    .append("<td>").append(apartment.get("location")).append("</td>")
                    .append("<td>").append(apartment.get("price")).append("</td>")
                    .append("<td><a href='").append(apartment.get("link"))
                    .append("' target='_blank'>Details</a></td>")
                    .append("</tr>");
        }

        html.append("</table></body></html>");
        return html.toString();
    }

    private static void sendEmail(List<Map<String, String>> apartments) throws MessagingException {
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
        message.setSubject("Your apartment search history");

        String htmlContent = generateHtmlTable(apartments);
        message.setContent(htmlContent, "text/html; charset=utf-8");

        Transport.send(message);
        System.out.println("Email sent successfully to " + to);
    }

}