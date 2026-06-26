package com.example.slagalicatim04.regions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CityRegionResolver {
    private static final Map<String, CityInfo> CITIES = buildCities();

    private CityRegionResolver() {
    }

    public static RegionInfo regionForCity(String cityName) {
        CityInfo city = cityForName(cityName);
        return city == null ? null : city.region;
    }

    public static CityInfo cityForName(String cityName) {
        if (cityName == null) {
            return null;
        }
        return CITIES.get(normalize(cityName));
    }

    private static Map<String, CityInfo> buildCities() {
        Map<String, CityInfo> cities = new HashMap<>();
        put(cities, RegionInfo.BELGRADE, "Beograd", 44.8125, 20.4612);
        put(cities, RegionInfo.BELGRADE, "Obrenovac", 44.6549, 20.2002);
        put(cities, RegionInfo.BELGRADE, "Lazarevac", 44.3853, 20.2557);
        put(cities, RegionInfo.BELGRADE, "Mladenovac", 44.4386, 20.6992);
        put(cities, RegionInfo.BELGRADE, "Grocka", 44.6724, 20.7177);
        put(cities, RegionInfo.BELGRADE, "Surcin", 44.7931, 20.2806);

        put(cities, RegionInfo.VOJVODINA, "Novi Sad", 45.2671, 19.8335);
        put(cities, RegionInfo.VOJVODINA, "Subotica", 46.1005, 19.6651);
        put(cities, RegionInfo.VOJVODINA, "Zrenjanin", 45.3836, 20.3819);
        put(cities, RegionInfo.VOJVODINA, "Pancevo", 44.8706, 20.6403);
        put(cities, RegionInfo.VOJVODINA, "Sombor", 45.7742, 19.1122);
        put(cities, RegionInfo.VOJVODINA, "Kikinda", 45.8297, 20.4653);
        put(cities, RegionInfo.VOJVODINA, "Sremska Mitrovica", 44.9764, 19.6122);
        put(cities, RegionInfo.VOJVODINA, "Vrsac", 45.1167, 21.3036);
        put(cities, RegionInfo.VOJVODINA, "Ruma", 45.0081, 19.8222);
        put(cities, RegionInfo.VOJVODINA, "Backa Palanka", 45.2497, 19.3919);
        put(cities, RegionInfo.VOJVODINA, "Indjija", 45.0492, 20.0817);
        put(cities, RegionInfo.VOJVODINA, "Apatin", 45.6711, 18.9842);

        put(cities, RegionInfo.SUMADIJA, "Kragujevac", 44.0128, 20.9114);
        put(cities, RegionInfo.SUMADIJA, "Cacak", 43.8914, 20.3497);
        put(cities, RegionInfo.SUMADIJA, "Uzice", 43.8558, 19.8425);
        put(cities, RegionInfo.SUMADIJA, "Kraljevo", 43.7258, 20.6894);
        put(cities, RegionInfo.SUMADIJA, "Novi Pazar", 43.1367, 20.5122);
        put(cities, RegionInfo.SUMADIJA, "Valjevo", 44.2720, 19.8874);
        put(cities, RegionInfo.SUMADIJA, "Sabac", 44.7489, 19.6908);
        put(cities, RegionInfo.SUMADIJA, "Loznica", 44.5333, 19.2258);
        put(cities, RegionInfo.SUMADIJA, "Jagodina", 43.9771, 21.2612);
        put(cities, RegionInfo.SUMADIJA, "Krusevac", 43.5800, 21.3267);
        put(cities, RegionInfo.SUMADIJA, "Prijepolje", 43.3903, 19.6486);
        put(cities, RegionInfo.SUMADIJA, "Arandjelovac", 44.3069, 20.5600);

        put(cities, RegionInfo.JUG_ISTOK, "Nis", 43.3209, 21.8958);
        put(cities, RegionInfo.JUG_ISTOK, "Leskovac", 42.9981, 21.9461);
        put(cities, RegionInfo.JUG_ISTOK, "Vranje", 42.5514, 21.9003);
        put(cities, RegionInfo.JUG_ISTOK, "Pirot", 43.1531, 22.5861);
        put(cities, RegionInfo.JUG_ISTOK, "Zajecar", 43.9019, 22.2738);
        put(cities, RegionInfo.JUG_ISTOK, "Bor", 44.0749, 22.0959);
        put(cities, RegionInfo.JUG_ISTOK, "Prokuplje", 43.2342, 21.5881);
        put(cities, RegionInfo.JUG_ISTOK, "Smederevo", 44.6644, 20.9276);
        put(cities, RegionInfo.JUG_ISTOK, "Pozarevac", 44.6213, 21.1878);
        put(cities, RegionInfo.JUG_ISTOK, "Kladovo", 44.6067, 22.6114);
        put(cities, RegionInfo.JUG_ISTOK, "Negotin", 44.2264, 22.5308);
        put(cities, RegionInfo.JUG_ISTOK, "Aleksinac", 43.5417, 21.7078);

        put(cities, RegionInfo.KOSOVO, "Pristina", 42.6629, 21.1655);
        put(cities, RegionInfo.KOSOVO, "Prizren", 42.2139, 20.7397);
        put(cities, RegionInfo.KOSOVO, "Pec", 42.6591, 20.2883);
        put(cities, RegionInfo.KOSOVO, "Kosovska Mitrovica", 42.8914, 20.8660);
        put(cities, RegionInfo.KOSOVO, "Gnjilane", 42.4635, 21.4694);
        put(cities, RegionInfo.KOSOVO, "Urosevac", 42.3706, 21.1553);
        put(cities, RegionInfo.KOSOVO, "Djakovica", 42.3803, 20.4308);
        put(cities, RegionInfo.KOSOVO, "Gracanica", 42.6011, 21.1958);
        return Collections.unmodifiableMap(cities);
    }

    private static void put(Map<String, CityInfo> cities, RegionInfo region, String city,
                            double latitude, double longitude) {
        cities.put(normalize(city), new CityInfo(city, region, latitude, longitude));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class CityInfo {
        public final String name;
        public final RegionInfo region;
        public final double latitude;
        public final double longitude;

        private CityInfo(String name, RegionInfo region, double latitude, double longitude) {
            this.name = name;
            this.region = region;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
