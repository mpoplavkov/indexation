# Indexation
JetBrains test assignment.

## Things to improve

- implement TODOs
- add terms transformer
- make it all type safe
- tests (event listener, index service)
- docs
- files filter
- compaction for the index (garbage collection)
- check all access modifiers
- add logging
- copy files and directories before indexing
- improve tests quality using different junit features

## Notes

- it doesn't track deleted files and directories, so that if they will be recreated, they will no longer be indexed.

## Possible features

- add consistency for complex queries to the index, such that each part of the query executes on the same snapshot of the index.