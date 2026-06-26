package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.regions.OpenStreetRegionMapStyler;
import com.example.slagalicatim04.regions.OpenStreetRegionResolver;
import com.example.slagalicatim04.regions.RegionDashboard;
import com.example.slagalicatim04.regions.RegionInfo;
import com.example.slagalicatim04.regions.RegionPlayerPoint;
import com.example.slagalicatim04.regions.RegionRepository;
import com.example.slagalicatim04.regions.RegionStats;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class RegionsFragment extends Fragment {
    private MapView mapView;
    private TextView selectedRegionTitle;
    private TextView selectedRegionStats;
    private TextView cycleText;
    private LinearLayout rankingList;
    private RegionDashboard dashboard;
    private AuthUser currentUser;
    private RegionInfo selectedRegion = RegionInfo.SUMADIJA;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_regions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = view.findViewById(R.id.serbiaRegionsMap);
        selectedRegionTitle = view.findViewById(R.id.selectedRegionTitle);
        selectedRegionStats = view.findViewById(R.id.selectedRegionStats);
        cycleText = view.findViewById(R.id.regionCycleText);
        rankingList = view.findViewById(R.id.regionRankingList);
        view.findViewById(R.id.openRegionChatButton).setOnClickListener(v -> openRegionChat());

        OpenStreetRegionMapStyler.configure(requireContext(), mapView, 7.0);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null) {
            selectedRegion = RegionInfo.byName(currentUser.getRegion());
        }
        cycleText.setText("Mesecni ciklus: " + RegionRepository.currentCycle());
        showStats(selectedRegion);
        drawMap();
        loadDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    private void loadDashboard() {
        String currentRegion = currentUser == null ? "" : currentUser.getRegion();
        new Thread(() -> {
            try {
                RegionDashboard loaded = new RegionRepository().loadDashboard(currentRegion);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    dashboard = loaded;
                    renderRanking();
                    showStats(RegionInfo.byName(currentRegion));
                    drawMap();
                });
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Regioni trenutno ne mogu da se ucitaju: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showStats(RegionInfo region) {
        if (region == null) {
            return;
        }
        selectedRegion = region;
        selectedRegionTitle.setText(region.iconLabel + "  " + region.name);
        RegionStats stats = dashboard == null ? null : dashboard.statsByRegionKey.get(region.key);
        if (stats == null) {
            selectedRegionStats.setText("Prva mesta: 0\nDruga mesta: 0\nTreca mesta: 0\n"
                    + "Aktivni igraci: 0\nRegistrovani igraci: 0");
            return;
        }
        selectedRegionStats.setText("Prva mesta: " + stats.firstPlaces
                + "\nDruga mesta: " + stats.secondPlaces
                + "\nTreca mesta: " + stats.thirdPlaces
                + "\nAktivni igraci: " + stats.activePlayers
                + "\nRegistrovani igraci: " + stats.totalPlayers);
    }

    private void drawMap() {
        if (mapView == null) {
            return;
        }
        mapView.getOverlays().clear();
        OpenStreetRegionMapStyler.addRegionOverlays(mapView, selectedRegion.key);
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                RegionInfo region = OpenStreetRegionResolver.regionForLocation(
                        point.getLatitude(), point.getLongitude());
                if (region == null) {
                    Toast.makeText(requireContext(),
                            "Izaberi lokaciju unutar Srbije.",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                showStats(region);
                drawMap();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));

        if (dashboard != null) {
            for (RegionPlayerPoint point : dashboard.playerPoints) {
                Marker marker = new Marker(mapView);
                marker.setPosition(new GeoPoint(point.latitude, point.longitude));
                marker.setTitle(point.regionKey);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(marker);
            }
        }
        OpenStreetRegionMapStyler.focusRegion(mapView, selectedRegion, 7.1);
        mapView.invalidate();
    }

    private void renderRanking() {
        rankingList.removeAllViews();
        if (dashboard == null || dashboard.ranking == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < dashboard.ranking.size(); i++) {
            com.example.slagalicatim04.regions.RegionRankItem item = dashboard.ranking.get(i);
            View row = inflater.inflate(R.layout.item_region_rank, rankingList, false);
            TextView place = row.findViewById(R.id.regionRankPlace);
            TextView icon = row.findViewById(R.id.regionRankIcon);
            TextView name = row.findViewById(R.id.regionRankName);
            TextView stars = row.findViewById(R.id.regionRankStars);
            TextView badge = row.findViewById(R.id.regionRankCurrentBadge);
            place.setText(String.valueOf(i + 1));
            icon.setText(item.region.iconLabel);
            name.setText(item.region.name);
            stars.setText(item.monthlyStars + " zvezda");
            badge.setVisibility(item.currentPlayerRegion ? View.VISIBLE : View.GONE);
            row.setBackgroundColor(item.currentPlayerRegion ? 0xFFEDE7F6 : 0xFFFFFFFF);
            rankingList.addView(row);
        }
    }

    private void openRegionChat() {
        RegionInfo region = currentUser == null
                ? selectedRegion
                : RegionInfo.byName(currentUser.getRegion());
        Bundle args = new Bundle();
        args.putString(RegionChatFragment.ARG_REGION_KEY, region.key);
        Navigation.findNavController(requireView()).navigate(R.id.regionChatFragment, args);
    }
}
