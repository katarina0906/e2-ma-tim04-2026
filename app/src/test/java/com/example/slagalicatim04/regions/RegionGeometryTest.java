package com.example.slagalicatim04.regions;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RegionGeometryTest {
    @Test
    public void randomPointForRegion_isInsideSelectedRegion() {
        for (RegionInfo region : RegionInfo.all()) {
            for (int i = 0; i < 100; i++) {
                float[] point = RegionGeometry.randomPointForRegion(region.name);

                assertTrue(
                        region.name + " point should be inside polygon",
                        RegionGeometry.contains(RegionGeometry.polygonFor(region.key), point[0], point[1])
                );
            }
        }
    }
}
