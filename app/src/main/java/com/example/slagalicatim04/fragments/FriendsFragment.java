package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.friends.FriendItem;
import com.example.slagalicatim04.friends.FriendQr;
import com.example.slagalicatim04.friends.FriendsAdapter;
import com.example.slagalicatim04.friends.FriendsRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

public class FriendsFragment extends Fragment {
    private FriendsAdapter adapter;
    private TextView emptyText;
    private TextView countText;
    private TextInputEditText searchInput;
    private AuthUser currentUser;
    private final ActivityResultLauncher<ScanOptions> qrScanner =
            registerForActivityResult(new ScanContract(), this::handleQrResult);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyText = view.findViewById(R.id.friendsEmptyText);
        countText = view.findViewById(R.id.friendsCountText);
        searchInput = view.findViewById(R.id.friendSearchInput);
        RecyclerView list = view.findViewById(R.id.friendsList);
        adapter = new FriendsAdapter(this::startGameWithFriend);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        view.findViewById(R.id.addFriendByUsernameButton)
                .setOnClickListener(v -> addByUsername());
        view.findViewById(R.id.scanFriendQrButton)
                .setOnClickListener(v -> startQrScan());
        loadFriends();
    }

    private void loadFriends() {
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            showFriends(new java.util.ArrayList<>());
            return;
        }
        new Thread(() -> {
            try {
                List<FriendItem> friends = new FriendsRepository().loadFriends(currentUser.getId());
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> showFriends(friends));
            } catch (Exception error) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Prijatelji trenutno ne mogu da se ucitaju: " + error.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showFriends(List<FriendItem> friends) {
        adapter.submit(friends);
        int count = friends == null ? 0 : friends.size();
        countText.setText("Ukupno prijatelja: " + count);
        emptyText.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void addByUsername() {
        String username = searchInput.getText() == null ? "" : searchInput.getText().toString();
        new Thread(() -> {
            try {
                FriendsRepository repository = new FriendsRepository();
                FriendItem friend = repository.findByUsername(username);
                if (friend == null) {
                    throw new IllegalArgumentException("Korisnik nije pronadjen.");
                }
                addFriend(repository, friend.id);
            } catch (Exception error) {
                showError(error);
            }
        }).start();
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Skeniraj QR kod prijatelja");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        qrScanner.launch(options);
    }

    private void handleQrResult(ScanIntentResult result) {
        if (result.getContents() == null) {
            return;
        }
        String friendId = FriendQr.userIdFromContents(result.getContents());
        new Thread(() -> {
            try {
                addFriend(new FriendsRepository(), friendId);
            } catch (Exception error) {
                showError(error);
            }
        }).start();
    }

    private void addFriend(FriendsRepository repository, String friendId)
            throws Exception {
        if (currentUser == null) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }
        FriendItem friend = repository.addFriend(currentUser.getId(), friendId);
        List<FriendItem> friends = repository.loadFriends(currentUser.getId());
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            searchInput.setText("");
            showFriends(friends);
            Toast.makeText(requireContext(),
                    friend.username + " je dodat u prijatelje.",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void startGameWithFriend(FriendItem friend) {
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            try {
                FriendsRepository repository = new FriendsRepository();
                String roomId = repository.startGameWithFriend(
                        currentUser.getId(),
                        currentUser.getUsername(),
                        friend);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putString("roomId", roomId);
                    Toast.makeText(requireContext(),
                            "Partija sa " + friend.username + " je kreirana.",
                            Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView())
                            .navigate(R.id.stepByStepWaitingRoomFragment, args);
                });
            } catch (Exception error) {
                showError(error);
            }
        }).start();
    }

    private void showError(Exception error) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show());
    }
}
