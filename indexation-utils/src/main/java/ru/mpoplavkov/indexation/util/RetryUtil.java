package ru.mpoplavkov.indexation.util;

import lombok.extern.java.Log;

import java.util.logging.Level;

@Log
public final class RetryUtil {

    private RetryUtil() {
    }

    public static void retry(RunnableWithException runnable, int retries) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.log(Level.SEVERE, e,
                    () -> String.format("Exception occurred. Remaining number of retries: %s", retries)
            );
            if (retries == 0) {
                throw new RuntimeException("Exception occurred even after retries", e);
            }
            retry(runnable, retries - 1);
        }
    }

}
