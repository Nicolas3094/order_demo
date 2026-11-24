# Pending authentication tests

These are the tests for the future auth service, HTTP endpoints, tokens and permissions.
They remain outside `src/test/java` because their production classes have intentionally
been removed to implement the feature step by step. Move each test back when working
on its corresponding feature. The security dependencies and test helper will also be
needed at that stage.

`UserAccountEntityTest` remains active in `src/test/java`. Its equality and hash-code
checks describe the remaining fixes in the current entity.
