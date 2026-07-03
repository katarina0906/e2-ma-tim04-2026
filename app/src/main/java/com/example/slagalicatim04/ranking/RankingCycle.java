package com.example.slagalicatim04.ranking;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RankingCycle {
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";

    public final String id;
    public final String type;
    public final Date startAt;
    public final Date endAt;

    public RankingCycle(String id, String type, Date startAt, Date endAt) {
        this.id = id;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static RankingCycle current(String type) {
        Calendar calendar = Calendar.getInstance(Locale.ROOT);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        if (WEEKLY.equals(type)) {
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            startOfDay(calendar);
            Date start = calendar.getTime();
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
            Date end = calendar.getTime();
            return new RankingCycle(String.format(Locale.ROOT, "weekly_%04d_%02d",
                    calendarYearForWeek(start), weekOfYear(start)), WEEKLY, start, end);
        }
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        startOfDay(calendar);
        Date start = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        Date end = calendar.getTime();
        return new RankingCycle(String.format(Locale.ROOT, "monthly_%04d_%02d",
                year(start), month(start)), MONTHLY, start, end);
    }

    public static RankingCycle fromSnapshot(DocumentSnapshot snapshot, String fallbackType) {
        Date start = dateValue(snapshot, "startAt");
        Date end = dateValue(snapshot, "endAt");
        String type = snapshot.getString("type");
        return new RankingCycle(snapshot.getId(), type == null ? fallbackType : type, start, end);
    }

    public String label() {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy.", Locale.ROOT);
        return format.format(startAt) + " - " + format.format(new Date(endAt.getTime() - 1));
    }

    private static Date dateValue(DocumentSnapshot snapshot, String key) {
        Timestamp timestamp = snapshot.getTimestamp(key);
        return timestamp == null ? new Date() : timestamp.toDate();
    }

    private static void startOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static int calendarYearForWeek(Date date) {
        Calendar calendar = Calendar.getInstance(Locale.ROOT);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        calendar.setTime(date);
        return calendar.getWeekYear();
    }

    private static int weekOfYear(Date date) {
        Calendar calendar = Calendar.getInstance(Locale.ROOT);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setMinimalDaysInFirstWeek(4);
        calendar.setTime(date);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

    private static int year(Date date) {
        Calendar calendar = Calendar.getInstance(Locale.ROOT);
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    private static int month(Date date) {
        Calendar calendar = Calendar.getInstance(Locale.ROOT);
        calendar.setTime(date);
        return calendar.get(Calendar.MONTH) + 1;
    }
}
