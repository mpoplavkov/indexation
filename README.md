# Indexation
JetBrains test assignment.

## Things to improve

- implement TODOs
- tests (event listener, index service)
- check all access modifiers
- improve tests quality using different junit features

## Possible features

- implement complex queries (and, or, not)

## Thoughts

##### Possible implementations of the VersionedTermIndex.search() method

- Two identical requests (implemented).
- Track of the *updateId* for each update operation and keeping this id along with the version of the value. History of updates of each value should be tracked.
- Repeatable reads of *valueVersions*.