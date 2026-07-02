package com.example.slagalicatim04;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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
import com.example.slagalicatim04.notifications.NotificationRepository;
import com.example.slagalicatim04.notifications.NotificationRouter;
import com.example.slagalicatim04.notifications.SlagalicaMessagingService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private NavController navController;
    private boolean rewardAutoOpenInFlight;

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
                R.id.rankingFragment,
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
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openPendingRewardNotification();
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

    private void openPendingRewardNotification() {
        if (rewardAutoOpenInFlight || navController == null
                || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }
        int destinationId = navController.getCurrentDestination() == null
                ? 0 : navController.getCurrentDestination().getId();
        if (destinationId == R.id.loginFragment
                || destinationId == R.id.registerFragment
                || destinationId == R.id.emailVerificationFragment
                || destinationId == R.id.resetPasswordFragment
                || destinationId == R.id.notificationTargetFragment) {
            return;
        }
        rewardAutoOpenInFlight = true;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("notifications")
                .whereEqualTo("action", NotificationRouter.ACTION_REWARD)
                .whereEqualTo("read", false)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    rewardAutoOpenInFlight = false;
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return;
                    }
                    var document = task.getResult().getDocuments().get(0);
                    new NotificationRepository().markRead(document.getId(), () -> {
                    }, ignored -> {
                    });
                    navController.navigate(R.id.notificationTargetFragment,
                            NotificationRouter.targetArgs(
                                    document.getString("action"),
                                    document.getString("title"),
                                    document.getString("message"),
                                    document.getString("targetId")));
                });
    }
}
