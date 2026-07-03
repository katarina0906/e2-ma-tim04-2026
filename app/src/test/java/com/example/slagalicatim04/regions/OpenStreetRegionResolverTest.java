package com.example.slagalicatim04.regions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OpenStreetRegionResolverTest {
    @Test
    public void regionForLocation_resolvesRepresentativeCities() {
        assertEquals(RegionInfo.BELGRADE,
                OpenStreetRegionResolver.regionForLocation(44.8125, 20.4612));
        assertEquals(RegionInfo.VOJVODINA,
                OpenStreetRegionResolver.regionForLocation(45.2671, 19.8335));
        assertEquals(RegionInfo.SUMADIJA,
                OpenStreetRegionResolver.regionForLocation(44.0128, 20.9114));
        assertEquals(RegionInfo.JUG_ISTOK,
                OpenStreetRegionResolver.regionForLocation(43.3209, 21.8958));
        assertEquals(RegionInfo.KOSOVO,
                OpenStreetRegionResolver.regionForLocation(42.6629, 21.1655));
    }

    @Test
    public void regionForLocation_returnsNullOutsideSerbia() {
        assertNull(OpenStreetRegionResolver.regionForLocation(48.8566, 2.3522));
    }
}
