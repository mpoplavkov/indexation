package ru.mpoplavkov.indexation.trigger.impl;

import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;

public class DoNothingTrigger implements FSEventTrigger {
    @Override
    public void onEvent(FileSystemEvent fileSystemEvent) {
    }
}
