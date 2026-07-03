package com.example.slagalicatim04.regions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CityRegionResolverTest {
    @Test
    public void regionForCity_resolvesKnownCities() {
        assertEquals(RegionInfo.BELGRADE, CityRegionResolver.regionForCity("Beograd"));
        assertEquals(RegionInfo.VOJVODINA, CityRegionResolver.regionForCity("Novi Sad"));
        assertEquals(RegionInfo.SUMADIJA, CityRegionResolver.regionForCity("Kragujevac"));
        assertEquals(RegionInfo.JUG_ISTOK, CityRegionResolver.regionForCity("Nis"));
        assertEquals(RegionInfo.KOSOVO, CityRegionResolver.regionForCity("Pristina"));
    }

    @Test
    public void regionForCity_returnsNullForUnknownCity() {
        assertNull(CityRegionResolver.regionForCity("Nepoznat grad"));
    }
}
