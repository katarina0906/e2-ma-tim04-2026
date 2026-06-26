package com.example.slagalicatim04.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicatim04.R;
import com.example.slagalicatim04.auth.AuthService;
import com.example.slagalicatim04.auth.AuthUser;
import com.example.slagalicatim04.regions.RegionChatAdapter;
import com.example.slagalicatim04.regions.RegionChatRepository;
import com.example.slagalicatim04.regions.RegionInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;

public class RegionChatFragment extends Fragment {

    public static final String ARG_REGION_KEY = "regionKey";

    private AuthUser currentUser;
    private RegionInfo currentRegion;
    private ListenerRegistration listenerRegistration;
    private RegionChatAdapter adapter;
    private RecyclerView messagesList;
    private TextView titleText;
    private TextView subtitleText;
    private TextView emptyText;
    private TextInputEditText input;
    private MaterialButton sendButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_region_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentUser = AuthService.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            return;
        }
        currentRegion = resolveRegion();

        titleText = view.findViewById(R.id.regionChatTitle);
        subtitleText = view.findViewById(R.id.regionChatSubtitle);
        emptyText = view.findViewById(R.id.regionChatEmpty);
        input = view.findViewById(R.id.regionChatInput);
        sendButton = view.findViewById(R.id.regionChatSendButton);
        messagesList = view.findViewById(R.id.regionChatList);

        adapter = new RegionChatAdapter(currentUser.getId());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        messagesList.setLayoutManager(layoutManager);
        messagesList.setAdapter(adapter);

        titleText.setText("Čet regiona");
        subtitleText.setText(currentRegion.iconLabel + " " + currentRegion.name);
        sendButton.setOnClickListener(v -> sendMessage());

        listenerRegistration = new RegionChatRepository().listen(currentRegion.key,
                new RegionChatRepository.Listener() {
                    @Override
                    public void onMessages(java.util.List<com.example.slagalicatim04.regions.RegionChatMessage> messages) {
                        if (!isAdded()) {
                            return;
                        }
                        adapter.submit(messages);
                        emptyText.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                        messagesList.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Čet nije dostupan: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private RegionInfo resolveRegion() {
        String keyFromArgs = getArguments() == null ? "" : getArguments().getString(ARG_REGION_KEY, "");
        if (keyFromArgs != null && !keyFromArgs.trim().isEmpty()) {
            return RegionInfo.byName(keyFromArgs);
        }
        return RegionInfo.byName(currentUser.getRegion());
    }

    private void sendMessage() {
        String text = input.getText() == null ? "" : input.getText().toString();
        sendButton.setEnabled(false);
        new RegionChatRepository().sendMessage(currentUser, text, () -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                input.setText("");
                sendButton.setEnabled(true);
            });
        }, error -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                sendButton.setEnabled(true);
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        super.onDestroyView();
    }
}
