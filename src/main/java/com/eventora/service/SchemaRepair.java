package com.eventora.service;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Self-healing database. Runs once at start-up, before the demo data seeder.
 *
 * Old versions of Eventora (or half-finished table changes) leave two kinds of damage in MySQL that Hibernate's
 * "ddl-auto=update" never repairs:
 *   1. Leftover NOT NULL columns that the current code no longer fills (e.g. tickets.price, tickets.available_qty)
 *      -> "Field 'price' doesn't have a default value" when issuing tickets.
 *   2. Rows whose foreign key points at nothing (user_id = 0, created when a NOT NULL column was added to a table that
 *      already had rows) -> "Unable to find AppUser with id 0" on the admin page.
 * This repair makes the leftovers nullable and removes orphaned rows. It never touches valid data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaRepair implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaRepair.class);
    private final DataSource ds;
    private final EntityManagerFactory emf;

    public SchemaRepair(DataSource ds, EntityManagerFactory emf) { this.ds = ds; this.emf = emf; }

    private static String snake(String s) { return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(); }
    private static String tableOf(Class<?> c) { Table t = c.getAnnotation(Table.class); return t != null ? t.name() : snake(c.getSimpleName()); }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            if (!product.contains("mysql") && !product.contains("mariadb")) return;
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                relaxLeftoverColumns(st);
                fixLegacyEnumValues(st);
                removeOrphans(st);
            } finally { st.execute("SET FOREIGN_KEY_CHECKS=1"); }
        } catch (Exception e) {
            log.warn("Schema repair skipped: {}", e.getMessage());
        }
    }

    private void relaxLeftoverColumns(Statement st) throws Exception {
        for (EntityType<?> et : emf.getMetamodel().getEntities()) {
            String table = tableOf(et.getJavaType());
            Set<String> mapped = new HashSet<>(List.of("id"));
            for (Attribute<?, ?> a : et.getAttributes()) {
                mapped.add(snake(a.getName()));
                if (a.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE) mapped.add(snake(a.getName()) + "_id");
            }
            List<String[]> fix = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SHOW COLUMNS FROM `" + table + "`")) {
                while (rs.next()) {
                    String col = rs.getString("Field"), type = rs.getString("Type");
                    boolean notNull = "NO".equalsIgnoreCase(rs.getString("Null"));
                    boolean noDefault = rs.getString("Default") == null;
                    boolean pk = "PRI".equalsIgnoreCase(rs.getString("Key"));
                    String extra = String.valueOf(rs.getString("Extra")).toLowerCase();
                    if (notNull && noDefault && !pk && !extra.contains("auto_increment") && !mapped.contains(col.toLowerCase()))
                        fix.add(new String[]{col, type});
                }
            } catch (Exception missing) { continue; }       // table does not exist yet
            for (String[] f : fix) {
                st.execute("ALTER TABLE `" + table + "` MODIFY `" + f[0] + "` " + f[1] + " NULL");
                log.warn("Schema repair: {}.{} is no longer used by the app - made nullable.", table, f[0]);
            }
        }
    }

    /** Old values written by earlier versions of Eventora -> the value the current code understands. */
    private static final Map<String, String> LEGACY = Map.ofEntries(
        Map.entry("PENDING", "PENDING_APPROVAL"), Map.entry("WAITING", "PENDING_APPROVAL"), Map.entry("SUBMITTED", "PENDING_APPROVAL"),
        Map.entry("ACTIVE", "PUBLISHED"), Map.entry("LIVE", "PUBLISHED"), Map.entry("OPEN", "PUBLISHED"),
        Map.entry("CANCELED", "SUSPENDED"), Map.entry("CANCELLED", "SUSPENDED"), Map.entry("DECLINED", "REJECTED"));

    /**
     * Fixes "No enum constant ...EventStatus.PENDING": turns MySQL ENUM(...) columns into plain VARCHAR (so new values can
     * always be stored) and rewrites old text values that no longer exist in the Java enums.
     */
    private void fixLegacyEnumValues(Statement st) {
        for (EntityType<?> et : emf.getMetamodel().getEntities()) {
            String table = tableOf(et.getJavaType());
            for (Attribute<?, ?> a : et.getAttributes()) {
                Class<?> t = a.getJavaType();
                if (!t.isEnum()) continue;
                String col = snake(a.getName());
                try {
                    String type = null; boolean notNull = false;
                    try (ResultSet rs = st.executeQuery("SHOW COLUMNS FROM `" + table + "` LIKE '" + col + "'")) {
                        if (rs.next()) { type = rs.getString("Type"); notNull = "NO".equalsIgnoreCase(rs.getString("Null")); }
                    }
                    if (type == null) continue;
                    if (type.toLowerCase().startsWith("enum"))
                        st.execute("ALTER TABLE `" + table + "` MODIFY `" + col + "` VARCHAR(40)" + (notNull ? " NOT NULL" : " NULL"));
                    List<String> valid = new ArrayList<>();
                    for (Object o : t.getEnumConstants()) valid.add(((Enum<?>) o).name());
                    String in = "'" + String.join("','", valid) + "'";
                    List<String> bad = new ArrayList<>();
                    try (ResultSet rs = st.executeQuery("SELECT DISTINCT `" + col + "` FROM `" + table + "` WHERE `" + col + "` IS NOT NULL AND `" + col + "` NOT IN (" + in + ")")) {
                        while (rs.next()) bad.add(rs.getString(1));
                    }
                    for (String old : bad) {
                        String to = LEGACY.get(old.toUpperCase());
                        if (to == null || !valid.contains(to)) to = valid.get(0);
                        st.executeUpdate("UPDATE `" + table + "` SET `" + col + "`='" + to + "' WHERE `" + col + "`='" + old.replace("'", "''") + "'");
                        log.warn("Schema repair: {}.{} value '{}' is no longer valid - changed to '{}'.", table, col, old, to);
                    }
                } catch (Exception ignored) { }      // table/column not present yet - nothing to repair
            }
        }
    }

    private void removeOrphans(Statement st) throws Exception {
        for (int pass = 0; pass < 6; pass++) {
            int removed = 0;
            for (EntityType<?> et : emf.getMetamodel().getEntities()) {
                String table = tableOf(et.getJavaType());
                for (Attribute<?, ?> a : et.getAttributes()) {
                    if (a.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_ONE) continue;
                    String col = snake(a.getName()) + "_id";
                    String target = tableOf(((SingularAttribute<?, ?>) a).getType().getJavaType());
                    try {
                        int n = st.executeUpdate("DELETE FROM `" + table + "` WHERE `" + col + "` IS NOT NULL AND (`" + col + "` = 0 OR NOT EXISTS (SELECT 1 FROM `" + target + "` x WHERE x.id = `" + table + "`.`" + col + "`))");
                        if (n > 0) log.warn("Schema repair: removed {} broken row(s) from {} (missing {}).", n, table, target);
                        removed += n;
                    } catch (Exception ignored) { }       // column/table not present - nothing to repair
                }
            }
            if (removed == 0) break;
        }
    }
}
