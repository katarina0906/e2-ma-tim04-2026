package com.example.slagalicatim04.repositories;

import com.example.slagalicatim04.models.SkockoGameResult;
import com.google.android.gms.tasks.Task;

public interface SkockoRepository {
    Task<Void> saveCompletedGame(SkockoGameResult result);
}
