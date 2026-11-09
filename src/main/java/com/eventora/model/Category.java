package com.eventora.model;

import java.awt.Color;

public enum Category {
    CONCERT("Music Concert", "#7c3aed", "#ec4899", "#f97316"),
    DJ_PARTY("DJ Party", "#0ea5e9", "#8b5cf6", "#ec4899"),
    TECH("Tech Conference", "#0891b2", "#3b82f6", "#6366f1"),
    WEDDING("Wedding", "#be185d", "#f472b6", "#fbbf24"),
    SPORTS("Sports", "#16a34a", "#22c55e", "#facc15"),
    CULTURAL("Cultural Festival", "#b45309", "#ea580c", "#dc2626"),
    FOOD("Food Festival", "#dc2626", "#f97316", "#facc15"),
    BUSINESS("Business", "#1e3a8a", "#2563eb", "#14b8a6");

    private final String label, c1, c2, c3;
    Category(String label, String c1, String c2, String c3) { this.label = label; this.c1 = c1; this.c2 = c2; this.c3 = c3; }
    public String getLabel() { return label; }
    /** Ticket theme colours (hex) used by the web ticket and the PDF. */
    public String getC1() { return c1; }
    public String getC2() { return c2; }
    public String getC3() { return c3; }
    public Color awt(int i) { return Color.decode(i == 1 ? c1 : i == 2 ? c2 : c3); }
}
