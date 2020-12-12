# Indexation
JetBrains test assignment.

## Things to improve

- implement TODOs
- make it all thread safe
- tests (event listener, index service)
- docs
- compaction for the index (garbage collection)
- check all access modifiers
- add logging
- improve tests quality using different junit features

## Notes

- it doesn't track deleted files and directories, so that if they will be recreated, they will no longer be indexed.

## Possible features

- implement complex queries (and, or, not)

## Thoughts

##### Possible implementations of the VersionedTermIndex.search() method

- Two identical requests (implemented).
- Track of the *updateId* for each update operation and keeping this id along with the version of the value. History of updates of each value should be tracked.
- Repeatable reads of *valueVersions*.