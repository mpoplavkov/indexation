# Indexation
JetBrains test assignment.

The repository contains a library for text files indexation, and a simple HTTP client for interacting with it. 
The library allows to subscribe to various files and directories in the file system and execute queries to retrieve a set of files that meet the query criteria. 
The library puts all subscribed entities into the index, monitors them and updates according to events from the file system.

The library is designed for safe concurrent use. 
It allows to execute queries in parallel and process file system updates on multiple threads as well.

The library is extendable. 
It allows to implement additional words extraction, path filtration and words transformation logic.
For example, if you want to index lemmas instead of original words, you should implement TermsTransformer and provide it when you create the index service. 

Properties:

* Index is in-memory, so all its information will be lost after a restart.
* It takes some time to respond to file system events, usually a few seconds.

## How to
### Run the HTTP client
To run the application locally, execute the following command from the root folder:
```
mvn -pl indexation-api spring-boot:run
```
After that, the API will be available on the ```localhost:8080```.

### Run integration tests
Integration tests are disabled in order to not execute them during the project build, because they take a long time to run.
To enable them, go to ```src/test/java/ru/mpoplavkov/indexation/integration/IntegrationIndexServiceTest.java``` and remove the ```@Disabled``` junit annotation.
After that, integration tests will run at the *test* maven phase: 
```
mvn clean test
```

### Run concurrency tests
There is a separate module for concurrency tests in the project - *indexation-concurrency-tests*. The [jcstress](https://openjdk.java.net/projects/code-tools/jcstress/) library is used for concurrency tests.

To run them, do the following from the project root:
```
mvn -pl indexation-concurrency-tests clean verify
java -jar indexation-concurrency-tests/target/jcstress.jar -r dir_for_report
```
This will execute concurrency tests and place the generated test report *index.html* in the *dir_for_report* directory.

## Overview of the API
The current client provides an HTTP API:

* **POST /subscribe** - subscribes to the given path (a directory or a file). Query parameters:
    * *path* - the path to subscribe.
* **POST /unsubscribe** - unsubscribes from the given path (a directory or a file). Query parameters:
    * *path* - the path to unsubscribe from.
* **GET /search** - returns a set of found files. Query parameters:
    * *word* - the word to search in subscribed files.
  
### Example usage of the API

1. ```curl --request POST 'localhost:8080/subscribe?path=.'``` - subscribe to events in the current directory and index all its files.
2. ```curl --request GET 'localhost:8080/search?word=public'``` - retrieve a set of files containing the word *public*. Result for ```.``` directory:
    * ```["/Users/mpoplavkov/IdeaProjects/indexation/indexation-api/src/main/java/ru/mpoplavkov/indexation/config/AppConfiguration.java","/Users/mpoplavkov/IdeaProjects/indexation/indexation-api/src/main/java/ru/mpoplavkov/indexation/api/SimpleIndexationController.java","/Users/mpoplavkov/IdeaProjects/indexation/indexation-api/src/main/java/ru/mpoplavkov/indexation/SimpleIndexationApi.java"]```
3. ```curl --request POST 'localhost:8080/unsubscribe?path=.'``` - unsubscribe from events in the directory and remove all its files from the index.

## Things to improve

* implement TODOs
* improve tests quality using different junit features

## Possible features

* implement complex queries (and, or, not)

## Thoughts

##### Possible implementations of the VersionedTermIndex.search() method

* Two identical requests (implemented).
* Track of the *updateId* for each update operation and keeping this id along with the version of the value. History of updates of each value should be tracked.
* Repeatable reads of *valueVersions*.
