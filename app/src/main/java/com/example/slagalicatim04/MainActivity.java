package com.example.slagalicatim04;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.slagalicatim04.databinding.ActivityMainBinding;
import com.example.slagalicatim04.friends.GameInviteRepository;
import com.example.slagalicatim04.notifications.InAppNotification;
import com.example.slagalicatim04.notifications.NotificationRepository;
import com.example.slagalicatim04.notifications.NotificationRouter;
import com.example.slagalicatim04.notifications.SlagalicaMessagingService;
import com.example.slagalicatim04.notifications.SystemNotificationPublisher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;
    private ListenerRegistration notificationRegistration;
    private final Set<String> surfacedGameInvites = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment_content_main);

        navController = navHostFragment.getNavController();

        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.gamesFragment,
                R.id.profileFragment,
                R.id.loginFragment
        ).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            boolean isAuthScreen = destinationId == R.id.loginFragment
                    || destinationId == R.id.registerFragment
                    || destinationId == R.id.emailVerificationFragment
                    || destinationId == R.id.resetPasswordFragment;

            if (isAuthScreen) {
                binding.toolbar.setVisibility(View.GONE);
                binding.bottomNavigation.setVisibility(View.GONE);
            } else {
                binding.toolbar.setVisibility(View.VISIBLE);
                binding.bottomNavigation.setVisibility(View.VISIBLE);
            }
        });

        requestNotificationPermission();
        listenForGameInvites();
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null
                || !intent.hasExtra(SlagalicaMessagingService.EXTRA_NOTIFICATION_ID)
                || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        String notificationId = intent.getStringExtra(
                SlagalicaMessagingService.EXTRA_NOTIFICATION_ID);
        String action = intent.getStringExtra(SlagalicaMessagingService.EXTRA_ACTION);
        String targetId = intent.getStringExtra(SlagalicaMessagingService.EXTRA_TARGET_ID);
        String title = intent.getStringExtra(SlagalicaMessagingService.EXTRA_TITLE);
        String message = intent.getStringExtra(SlagalicaMessagingService.EXTRA_MESSAGE);

        if (notificationId != null && !notificationId.isEmpty()) {
            new NotificationRepository().markRead(notificationId, () -> {
            }, ignored -> {
            });
        }
        intent.removeExtra(SlagalicaMessagingService.EXTRA_NOTIFICATION_ID);
        navController.navigate(R.id.notificationTargetFragment,
                NotificationRouter.targetArgs(action, title, message, targetId));
    }

    private void listenForGameInvites() {
        if (notificationRegistration != null) {
            notificationRegistration.remove();
        }
        notificationRegistration = new NotificationRepository().listen(new NotificationRepository.Listener() {
            @Override
            public void onChanged(java.util.List<InAppNotification> notifications) {
                for (InAppNotification item : notifications) {
                    if (shouldSurfaceGameInvite(item)) {
                        surfacedGameInvites.add(item.id);
                        SystemNotificationPublisher.show(MainActivity.this, item);
                        showGameInviteDialog(item);
                        scheduleInviteExpiration(item);
                    }
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private boolean shouldSurfaceGameInvite(InAppNotification item) {
        return NotificationRouter.ACTION_GAME_INVITE.equals(item.actionHint)
                && "pending".equals(item.inviteStatus)
                && !item.read
                && !surfacedGameInvites.contains(item.id);
    }

    private void showGameInviteDialog(InAppNotification item) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(inviteMessage(item))
                .setNegativeButton("Odbij", (ignoredDialog, which) -> declineGameInvite(item))
                .setPositiveButton("Prihvati", (ignoredDialog, which) -> acceptGameInvite(item))
                .show();
        startInviteDialogCountdown(dialog, item);
    }

    private void startInviteDialogCountdown(AlertDialog dialog, InAppNotification item) {
        Runnable[] ticker = new Runnable[1];
        ticker[0] = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            long secondsLeft = inviteSecondsLeft(item);
            dialog.setMessage(inviteMessage(item));
            if (secondsLeft <= 0L) {
                dialog.dismiss();
                expireGameInvite(item);
                return;
            }
            handler.postDelayed(ticker[0], 1000L);
        };
        dialog.setOnDismissListener(ignored -> handler.removeCallbacks(ticker[0]));
        handler.post(ticker[0]);
    }

    private String inviteMessage(InAppNotification item) {
        long secondsLeft = inviteSecondsLeft(item);
        return item.message + "\n\nPreostalo: " + secondsLeft + "s";
    }

    private long inviteSecondsLeft(InAppNotification item) {
        long expiresAt = longValue(item.data.get("expiresAt"));
        if (expiresAt <= 0L) {
            return 0L;
        }
        long millisLeft = Math.max(0L, expiresAt - System.currentTimeMillis());
        return (millisLeft + 999L) / 1000L;
    }

    private void acceptGameInvite(InAppNotification item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            try {
                String roomId = roomIdOf(item);
                String acceptedRoomId = new GameInviteRepository()
                        .acceptInvite(user.getUid(), roomId, item.id);
                runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putString("roomId", acceptedRoomId);
                    Toast.makeText(this, "Poziv je prihvacen.", Toast.LENGTH_SHORT).show();
                    navController.navigate(R.id.stepByStepWaitingRoomFragment, args);
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        messageOf(error), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void declineGameInvite(InAppNotification item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Korisnik nije prijavljen.", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            try {
                new GameInviteRepository().declineInvite(user.getUid(), roomIdOf(item), item.id);
                runOnUiThread(() -> Toast.makeText(this,
                        "Poziv je odbijen.", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        messageOf(error), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void scheduleInviteExpiration(InAppNotification item) {
        long expiresAt = longValue(item.data.get("expiresAt"));
        if (expiresAt <= 0L) {
            return;
        }
        long delay = Math.max(0L, expiresAt - System.currentTimeMillis());
        handler.postDelayed(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                return;
            }
            new Thread(() -> {
                try {
                    new GameInviteRepository().expireInviteIfNeeded(
                            user.getUid(), roomIdOf(item), item.id);
                } catch (Exception ignored) {
                }
            }).start();
        }, delay);
    }

    private void expireGameInvite(InAppNotification item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        new Thread(() -> {
            try {
                new GameInviteRepository().expireInviteIfNeeded(
                        user.getUid(), roomIdOf(item), item.id);
                runOnUiThread(() -> Toast.makeText(this,
                        "Poziv je istekao.", Toast.LENGTH_SHORT).show());
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String roomIdOf(InAppNotification item) {
        String roomId = item.data.get("roomId");
        return roomId == null || roomId.trim().isEmpty() ? item.targetId : roomId;
    }

    private long longValue(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String messageOf(Exception error) {
        return error.getMessage() == null ? "Greska pri obradi poziva." : error.getMessage();
    }

    @Override
    protected void onDestroy() {
        if (notificationRegistration != null) {
            notificationRegistration.remove();
            notificationRegistration = null;
        }
        super.onDestroy();
    }
}
