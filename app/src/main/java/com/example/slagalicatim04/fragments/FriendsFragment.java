package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.slagalicatim04.friends.GameInviteResult;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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
    private ListenerRegistration pendingInviteRegistration;
    private boolean inviteNavigationHandled;
    private GameInviteResult pendingInvite;
    private final Handler handler = new Handler(Looper.getMainLooper());
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
        adapter = new FriendsAdapter(this::startGameWithFriend, this::cancelGameInvite);
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
        if (friend != null && currentUser.getId().equals(friend.id)) {
            Toast.makeText(requireContext(),
                    "Ne mozes poslati zahtev za partiju samom sebi.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            try {
                FriendsRepository repository = new FriendsRepository();
                GameInviteResult invite = repository.startGameWithFriend(
                        currentUser.getId(),
                        currentUser.getUsername(),
                        friend);
                scheduleInviteExpiration(invite);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    pendingInvite = invite;
                    adapter.setPendingInviteFriendId(friend.id);
                    listenForInviteAnswer(invite, friend);
                    Toast.makeText(requireContext(),
                            "Poziv za partiju je poslat igracu " + friend.username + ".",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                showError(error);
            }
        }).start();
    }

    private void cancelGameInvite(FriendItem friend) {
        GameInviteResult invite = pendingInvite;
        if (currentUser == null || invite == null) {
            clearPendingInviteUi();
            return;
        }
        new Thread(() -> {
            try {
                new FriendsRepository().cancelGameInvite(
                        currentUser.getId(),
                        invite.roomId,
                        invite.notificationId);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    closeInviteListener();
                    clearPendingInviteUi();
                    Toast.makeText(requireContext(),
                            "Zahtev za partiju je prekinut.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                showError(error);
            }
        }).start();
    }

    private void scheduleInviteExpiration(GameInviteResult invite) {
        handler.postDelayed(() -> new Thread(() -> {
            try {
                new FriendsRepository().expireGameInvite(invite.roomId, invite.notificationId);
            } catch (Exception ignored) {
            }
        }).start(), FriendsRepository.GAME_INVITE_TIMEOUT_MS);
    }

    private void listenForInviteAnswer(GameInviteResult invite, FriendItem friend) {
        if (pendingInviteRegistration != null) {
            pendingInviteRegistration.remove();
            pendingInviteRegistration = null;
        }
        inviteNavigationHandled = false;
        pendingInviteRegistration = FirebaseFirestore.getInstance()
                .collection("stepByStepMatches")
                .document(invite.roomId)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    String status = snapshot.getString("inviteStatus");
                    if ("accepted".equals(status)) {
                        navigateSenderToWaitingRoom(invite.roomId, friend.username);
                    } else if ("declined".equals(status) || "expired".equals(status)) {
                        closeInviteListener();
                        clearPendingInviteUi();
                        String message = "declined".equals(status)
                                ? friend.username + " je odbio poziv za partiju."
                                : "Poziv za partiju je istekao.";
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    } else if ("cancelled".equals(status)) {
                        closeInviteListener();
                        clearPendingInviteUi();
                        Toast.makeText(requireContext(),
                                "Zahtev za partiju je prekinut.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateSenderToWaitingRoom(String roomId, String friendName) {
        if (inviteNavigationHandled) {
            return;
        }
        inviteNavigationHandled = true;
        closeInviteListener();
        clearPendingInviteUi();
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        Toast.makeText(requireContext(),
                friendName + " je prihvatio poziv.",
                Toast.LENGTH_SHORT).show();
        Navigation.findNavController(requireView())
                .navigate(R.id.stepByStepWaitingRoomFragment, args);
    }

    private void closeInviteListener() {
        if (pendingInviteRegistration != null) {
            pendingInviteRegistration.remove();
            pendingInviteRegistration = null;
        }
    }

    private void clearPendingInviteUi() {
        pendingInvite = null;
        if (adapter != null) {
            adapter.setPendingInviteFriendId("");
        }
    }

    private void showError(Exception error) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroyView() {
        closeInviteListener();
        super.onDestroyView();
    }
}
