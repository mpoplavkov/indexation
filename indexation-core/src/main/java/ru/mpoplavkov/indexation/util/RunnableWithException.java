package ru.mpoplavkov.indexation.util;

@FunctionalInterface
public interface RunnableWithException {

    void run() throws Exception;

}
