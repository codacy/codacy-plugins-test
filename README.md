[![Codacy Badge](https://api.codacy.com/project/badge/grade/77e0473f417446a78758f02785a705b8)](https://www.codacy.com/app/Codacy/codacy-plugins-test)
[![Build Status](https://circleci.com/gh/codacy/codacy-plugins-test.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/codacy/codacy-plugins-test)

# Codacy Plugins Test

Provide a testing interface for the external docker tools.

## Test definition

The test files should be placed in `/docs/tests` on the docker of the tool being tested.

**Definition**

```javascript
//#Patterns: <PATTERN_NAME> : { "<PARAMETER_NAME>": "<PARAMETER_VALUE>" }


var people={};
//#<PATTERN_LEVEL>: <PATTERN_NAME>
for (var i = 0, person; person = people[i]; i++) {

}

var variable;
function test() {
//#<PATTERN_LEVEL>: <PATTERN_NAME>
    return variable = 'test';
}
```

**Example:**

```javascript
//#Patterns: boss


var people={};
//#Warn: boss
for (var i = 0, person; person = people[i]; i++) {

}

var variable;
function test() {
//#Warn: boss
    return variable = 'test';
}
```

You can also look for multiple patterns in the same file, just separate them
with a comma:

**Example:**

```javascript
//#Patterns: big, boss
var people={};
//#Warn: big
for (var i = 0, person; person = people[i]; i++) {

}

var variable;
function test() {
//#Warn: boss
    return variable = 'test';
}
```

Instead of commenting in the line before the error, you can alternatively 
specify the line of the warning with this syntax:

```
<LANGUAGE_COMMENT>#Issue: {"severity": "<ERROR_LEVEL>", "line": <LINE_NUMBER_WITH_ISSUE>, "patternId": "PATTERN_ID"}
```

**Example:**

```javascript
//#Patterns: design_tag_todo
//#Issue: {"severity": "Info", "line": 3, "patternId": "design_tag_todo"}

var people={};
//TODO: remove empty for
for (var i = 0, person; person = people[i]; i++) {

}

var variable;
```

## Usage

> JsonTests

Checks if the patterns definitions are in the correct format

```sh
sbt "runMain codacy.plugins.DockerTest json <DOCKER_NAME>:<DOCKER_VERSION>"
```

**Options:**

* `codacy.tests.ignore.descriptions` - if this variable is defined we do not check if all the patterns have descriptions

> PluginsTests

Checks if all the patterns have an occurrence in the test files

```sh
sbt "runMain codacy.plugins.DockerTest plugin <DOCKER_NAME>:<DOCKER_VERSION>"
```

> PatternTests

Check if all the patterns defined in the test files occur in the specified line

```sh
sbt "runMain codacy.plugins.DockerTest pattern <DOCKER_NAME>:<DOCKER_VERSION>"
```

> MetricsTests

Check if the metrics defined in the test files match with same complexieties in the specified lines

```sh
sbt "runMain codacy.plugins.DockerTest metrics <DOCKER_NAME>:<DOCKER_VERSION>"
```

**Options:**

* `codacy.tests.threads` - number of parallel threads to run the tests

* `codacy.tests.languages` - languages supported by the tool. If this option isn't provided, the languages
will be inferred from the test files. Example: `-Dcodacy.tests.languages=ruby,java,javascript`

Alternatively, you can run a specific test file:

```sh
sbt "runMain codacy.plugins.DockerTest pattern <DOCKER_NAME>:<DOCKER_VERSION> no-curly-brackets"
```

> All

```sh
sbt "runMain codacy.plugins.DockerTest all <DOCKER_NAME>:<DOCKER_VERSION>"
```

> Debug

If you need to debug the output of the dockers after the tests you can request the runner to not remove them with:

**Options:**

* `codacy.tests.noremove` - do not remove dockers after running test

## Docs

Information about the integration with external analysis tools at Codacy available [here](https://github.com/codacy/codacy-engine-scala-seed#docs).

## Troubleshooting

> OSx

Change the java tmp dir to your home so that boot2docker can access the tmp files

```sh
-Djava.io.tmpdir=$HOME/tmp
```

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features:

- Identify new Static Analysis issues
- Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
- Auto-comments on Commits and Pull Requests
- Integrations with Slack, HipChat, Jira, YouTrack
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.

## License

Licensed under the MIT License terms.
