package com.eventora;

import java.awt.Desktop;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. In VS Code just click "Run" above main() (or press F5).
 * Eventora starts and opens itself in your default browser automatically.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Main {

    public static void main(String[] args) {
        System.setProperty("net.bytebuddy.experimental", "true");   // allows very new JDKs (e.g. Java 25)
        ensureDatabase();

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Main.class).headless(false).run(args);
        String port = ctx.getEnvironment().getProperty("server.port", "8080");
        String url = "http://localhost:" + port;
        System.out.println("\n  Eventora is running ->  " + url + "\n");
        if (ctx.getEnvironment().getProperty("eventora.open-browser", Boolean.class, true)) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) { /* open the URL manually */ }
        }
    }

    /**
     * Checks MySQL quickly (4 s). If it is not reachable (not started / wrong password) the app falls back to a
     * local file database (./data) so it still starts. Fix MySQL settings in application.properties to use MySQL.
     */
    private static void ensureDatabase() {
        if (System.getProperty("spring.datasource.url") != null || System.getenv("SPRING_DATASOURCE_URL") != null) return;
        try {
            Properties p = new Properties();
            try (InputStream in = Main.class.getResourceAsStream("/application.properties")) { p.load(in); }
            DriverManager.setLoginTimeout(4);
            try (Connection c = DriverManager.getConnection(p.getProperty("spring.datasource.url"),
                    p.getProperty("spring.datasource.username"), p.getProperty("spring.datasource.password"))) {
                System.out.println("\n  [Eventora] Connected to MySQL.\n");
            }
        } catch (Exception e) {
            String why = String.valueOf(e.getMessage()).split("\n")[0];
            System.out.println("\n  =====================================================================");
            System.out.println("  [Eventora] MySQL is not reachable: " + why);
            System.out.println("  Start MySQL and set spring.datasource.username/password in");
            System.out.println("  src/main/resources/application.properties to use MySQL.");
            System.out.println("  Falling back to a local database file (./data) so the app still runs.");
            System.out.println("  =====================================================================\n");
            System.setProperty("spring.datasource.url", "jdbc:h2:file:./data/eventora;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE");
            System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.password", "");
            System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        }
    }
}
