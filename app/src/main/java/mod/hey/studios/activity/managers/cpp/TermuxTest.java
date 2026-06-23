package mod.hey.studios.activity.managers.cpp;

import android.content.Context;
import android.content.Intent;

public class TermuxTest {

    public static void run(Context context) {

        Intent intent = new Intent();

        intent.setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
        );

        intent.setAction("com.termux.RUN_COMMAND");

        intent.putExtra(
                "com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash"
        );

        intent.putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{
                        "-c",
                        "echo HELLO_FROM_SKETCHWARE > /sdcard/test.txt"
                }
        );

        intent.putExtra(
                "com.termux.RUN_COMMAND_BACKGROUND",
                true
        );

        context.startService(intent);
    }
}