[![Codacy Badge](https://api.codacy.com/project/badge/grade/77e0473f417446a78758f02785a705b8)](https://www.codacy.com/app/Codacy/codacy-plugins-test)
[![Build Status](https://circleci.com/gh/codacy/codacy-plugins-test.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/codacy/codacy-plugins-test)

# Codacy Plugins Test

Provide a testing interface for the external docker tools.

## Test definition

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

## Usage

> JsonTests

Checks if the patterns definitions are in the correct format

```sh
sbt "run-main codacy.plugins.DockerTest json codacy/jshint:1.0.3"
```

**Options:**

* `codacy.tests.ignore.descriptions` - if this variable is defined we do not check if all the patterns have descriptions

> PluginsTests

Checks if all the patterns have an occurrence in the test files

```sh
sbt "run-main codacy.plugins.DockerTest plugin codacy/jshint:1.0.3"
```

> PatternTests

Check if all the patterns defined in the test files occur in the specified line

```sh
sbt "run-main codacy.plugins.DockerTest pattern codacy/jshint:1.0.3"
sbt test
```

**Options:**

* `codacy.tests.threads` - number of parallel threads to run the tests

Alternatively, you can run a specific test file:

```sh
sbt "run-main codacy.plugins.DockerTest pattern codacy/jshint:1.0.3 no-curly-brackets"
sbt test
```

> All

```sh
sbt "run-main codacy.plugins.DockerTest all codacy/jshint:1.0.3"
```

> Debug

If you need to debug the output of the dockers after the tests you can request the runner to not remove them with:

**Options:**

* `codacy.tests.noremove` - do not remove dockers after running test

## Docs

[Tool Developer Guide](http://docs.codacy.com/v1.5/docs/tool-developer-guide)

[Tool Developer Guide - Using Scala](http://docs.codacy.com/v1.5/docs/tool-developer-guide-using-scala)

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
