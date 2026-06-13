package com.example.slagalicatim04.notifications;

import android.os.Bundle;

import com.example.slagalicatim04.fragments.NotificationTargetFragment;

public final class NotificationRouter {

    public static final String ACTION_CHAT = "chat_thread";
    public static final String ACTION_RANKING = "rank_detail";
    public static final String ACTION_REWARD = "rewards_claim";
    public static final String ACTION_FRIEND_REQUEST = "friend_accept";
    public static final String ACTION_GAME_INVITE = "game_invite";
    public static final String ACTION_LEAGUE = "league_info";
    public static final String ACTION_CHALLENGE = "challenge_result";
    public static final String ACTION_TOURNAMENT = "tournament_result";

    private NotificationRouter() {
    }

    public static Bundle targetArgs(InAppNotification item) {
        return targetArgs(item.actionHint, item.title, item.message, item.targetId);
    }

    public static Bundle targetArgs(String action, String title, String message, String targetId) {
        Bundle args = new Bundle();
        String safeAction = action == null ? "" : action;
        args.putString(NotificationTargetFragment.ARG_ACTION, safeAction);
        args.putString(NotificationTargetFragment.ARG_TARGET_ID, valueOrEmpty(targetId));

        switch (safeAction) {
            case ACTION_CHAT:
                fill(args, "Cet", "Poruke igraca iz tvog regiona", message);
                break;
            case ACTION_RANKING:
                fill(args, "Rang lista", "Nedeljni i mesecni plasman", message);
                break;
            case ACTION_REWARD:
                fill(args, "Nagrade", "Osvojeni tokeni i nagrade", message);
                break;
            case ACTION_FRIEND_REQUEST:
                fill(args, "Prijatelji", "Zahtevi i lista prijatelja", message);
                break;
            case ACTION_GAME_INVITE:
                fill(args, "Poziv za partiju", "Prihvati ili odbij poziv", message);
                break;
            case ACTION_LEAGUE:
                fill(args, "Liga", "Napredovanje kroz lige", message);
                break;
            case ACTION_CHALLENGE:
                fill(args, "Izazov", "Rezultat izazova", message);
                break;
            case ACTION_TOURNAMENT:
                fill(args, "Turnir", "Rezultat turnira", message);
                break;
            default:
                fill(args, "Obavestenje", title, message);
                break;
        }
        return args;
    }

    private static void fill(Bundle args, String title, String subtitle, String message) {
        args.putString(NotificationTargetFragment.ARG_TITLE, valueOrEmpty(title));
        args.putString(NotificationTargetFragment.ARG_SUBTITLE, valueOrEmpty(subtitle));
        args.putString(NotificationTargetFragment.ARG_MESSAGE, valueOrEmpty(message));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
