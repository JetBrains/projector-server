# Contributing to Projector
Thank you for reading this: we welcome any contributions.

All Projector-related projects have the same contributing guidelines. This is the place where they are described.

## Creating an issue
You can create issues in our [issue tracker](https://youtrack.jetbrains.com/issues/PRJ).

If you **report a bug**, please either describe reproduction steps or mention that you don't know how to reproduce it stably. You can also **suggest a feature**.

If you've found the same ticket, please avoid creating a duplicate.

## Submitting changes
If you want to resolve an **issue submitted in the issue tracker** or propose a **minor edit**, open a Pull Request. Better don't include multiple changes in a single PR, create separate.

**Pre-commit actions**. Before committing, please ensure that code style is correct. If you use IntelliJ IDEA, you can just select the following checkboxes of "Before Commit" actions:
* Reformat code
* Rearrange code
* Optimize imports
* Cleanup

**Commits naming**. If there are issues that you address, please mention them in the prefix of commit messages. Usually, a commit name starts with a verb. Example: `PRJ-68 Make clipboard owners lose ownership when clipboard is changed by a client`.
