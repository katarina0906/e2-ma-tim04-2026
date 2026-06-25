package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthResult;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.regions.CityRegionResolver;
import com.example.slagalicatim04.regions.OpenStreetRegionMapStyler;
import com.example.slagalicatim04.regions.OpenStreetRegionResolver;
import com.example.slagalicatim04.regions.RegionInfo;
import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class RegisterFragment extends Fragment {
    private MapView mapView;
    private Marker selectedMarker;
    private Double selectedLatitude;
    private Double selectedLongitude;

    public RegisterFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);

        TextInputEditText emailInput = view.findViewById(R.id.registerEmailInput);
        TextInputEditText usernameInput = view.findViewById(R.id.registerUsernameInput);
        AutoCompleteTextView cityInput = view.findViewById(R.id.registerCityInput);
        AutoCompleteTextView regionInput = view.findViewById(R.id.registerRegionInput);
        mapView = view.findViewById(R.id.registerRegionsMap);
        TextInputEditText passwordInput = view.findViewById(R.id.registerPasswordInput);
        TextInputEditText confirmPasswordInput = view.findViewById(R.id.registerConfirmPasswordInput);
        AuthService authService = AuthService.getInstance(requireContext());
        configureOpenStreetMap(regionInput);
        ArrayAdapter<CharSequence> regionAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.serbia_regions,
                android.R.layout.simple_list_item_1
        );
        regionInput.setAdapter(regionAdapter);
        ArrayAdapter<CharSequence> cityAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.serbia_cities,
                android.R.layout.simple_list_item_1
        );
        cityInput.setAdapter(cityAdapter);

        regionInput.setOnItemClickListener((parent, selectedView, position, id) -> {
            selectedLatitude = null;
            selectedLongitude = null;
            if (selectedMarker != null) {
                mapView.getOverlays().remove(selectedMarker);
                selectedMarker = null;
                mapView.invalidate();
            }
        });
        cityInput.setOnItemClickListener((parent, selectedView, position, id) -> {
            CityRegionResolver.CityInfo city = CityRegionResolver.cityForName(textOf(cityInput));
            if (city != null) {
                selectRegion(regionInput, city.region);
                showSelectedLocation(city.latitude, city.longitude, city.name);
            }
        });
        cityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                CityRegionResolver.CityInfo city = CityRegionResolver.cityForName(text.toString());
                if (city != null) {
                    selectRegion(regionInput, city.region);
                    showSelectedLocation(city.latitude, city.longitude, city.name);
                }
            }
        });

        view.findViewById(R.id.registerButton).setOnClickListener(v -> {
            String email = textOf(emailInput);
            String username = textOf(usernameInput);
            String region = textOf(regionInput);
            String password = textOf(passwordInput);
            String confirmPassword = textOf(confirmPasswordInput);
            Double latitude = selectedLatitude;
            Double longitude = selectedLongitude;
            new Thread(() -> {
                AuthResult<AuthUser> result = authService.register(
                        email,
                        username,
                        region,
                        password,
                        confirmPassword,
                        latitude,
                        longitude
                );
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), result.getMessage(), Toast.LENGTH_LONG).show();
                    if (result.isSuccess()) {
                        Bundle arguments = new Bundle();
                        arguments.putString("identifier", result.getData().getEmail());
                        Navigation.findNavController(v)
                                .navigate(R.id.action_registerFragment_to_emailVerificationFragment, arguments);
                    }
                });
            }).start();
        });

        view.findViewById(R.id.backToLoginFromRegisterButton).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_registerFragment_to_loginFragment));

        return view;
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

    private String textOf(android.widget.TextView textView) {
        Editable text = textView.getEditableText();
        return text == null ? "" : text.toString();
    }

    private void configureOpenStreetMap(AutoCompleteTextView regionInput) {
        OpenStreetRegionMapStyler.configure(requireContext(), mapView, 7.0);
        OpenStreetRegionMapStyler.addRegionOverlays(mapView, null);
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
                selectRegion(regionInput, region);
                showSelectedLocation(point.getLatitude(), point.getLongitude(), region.name);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));
    }

    private void selectRegion(AutoCompleteTextView regionInput, RegionInfo region) {
        regionInput.setText(region.name, false);
    }

    private void showSelectedLocation(double latitude, double longitude, String title) {
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (selectedMarker == null) {
            selectedMarker = new Marker(mapView);
            selectedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(selectedMarker);
        }
        selectedMarker.setPosition(point);
        selectedMarker.setTitle(title);
        selectedLatitude = latitude;
        selectedLongitude = longitude;
        mapView.getController().animateTo(point);
        mapView.invalidate();
    }
}
