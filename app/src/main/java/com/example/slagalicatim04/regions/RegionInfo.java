package com.example.slagalicatim04.regions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RegionInfo {
    public static final RegionInfo BELGRADE = new RegionInfo(
            "belgrade", "Beogradski region", "🏛", 0xFF6F4BB2);
    public static final RegionInfo VOJVODINA = new RegionInfo(
            "vojvodina", "Region Vojvodine", "🌾", 0xFF1565C0);
    public static final RegionInfo SUMADIJA = new RegionInfo(
            "sumadija_zapad", "Region Sumadije i Zapadne Srbije", "🌲", 0xFF2E7D32);
    public static final RegionInfo JUG_ISTOK = new RegionInfo(
            "jug_istok", "Region Juzne i Istocne Srbije", "⛰", 0xFFEF6C00);
    public static final RegionInfo KOSOVO = new RegionInfo(
            "kosovo_metohija", "Region Kosova i Metohije", "☀", 0xFF8E24AA);

    private static final List<RegionInfo> ALL = Collections.unmodifiableList(Arrays.asList(
            BELGRADE, VOJVODINA, SUMADIJA, JUG_ISTOK, KOSOVO
    ));

    public final String key;
    public final String name;
    public final String iconLabel;
    public final int color;

    private RegionInfo(String key, String name, String iconLabel, int color) {
        this.key = key;
        this.name = name;
        this.iconLabel = iconLabel;
        this.color = color;
    }

    public static List<RegionInfo> all() {
        return ALL;
    }

    public static RegionInfo byName(String name) {
        if (name == null) {
            return SUMADIJA;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (RegionInfo region : ALL) {
            if (region.name.toLowerCase(Locale.ROOT).equals(normalized)
                    || region.key.equals(normalized)) {
                return region;
            }
        }
        return SUMADIJA;
    }
}
