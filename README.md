# percy-java-selenium

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.percy/percy-java-selenium/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.percy/percy-java-selenium)
![Test](https://github.com/percy/percy-java-selenium/workflows/Test/badge.svg)

[Percy](https://percy.io) visual testing for Java Selenium.

## Installation

npm install `@percy/cli`:

```sh-session
$ npm install --save-dev @percy/cli
```

Add percy-java-selenium to your project dependencies. If you're using Maven:

``` xml
<dependency>
  <groupId>io.percy</groupId>
  <artifactId>percy-java-selenium</artifactId>
  <version>1.0.0</version>
</dependency>
```

If you're using a different build system, see https://search.maven.org/artifact/io.percy/percy-java-selenium for details for your specific system.

## Usage

This is an example test using the `percy.snapshot` function.

``` java
// import ...
import io.percy.selenium.Percy;

public class Example {
  private static WebDriver driver;
  private static Percy percy;

  public static void main(String[] args) {
    FirefoxOptions options = new FirefoxOptions();
    options.setHeadless(true);
    driver = new FirefoxDriver(options);
    percy = new Percy(driver);

    driver.get("https://example.com");
    percy.snapshot("Java example");
  }
}
```

Running the test above normally will result in the following log:

```sh-session
[percy] Percy is not running, disabling snapshots
```

When running with [`percy
exec`](https://github.com/percy/cli/tree/master/packages/cli-exec#percy-exec), and your project's
`PERCY_TOKEN`, a new Percy build will be created and snapshots will be uploaded to your project.

```sh-session
$ export PERCY_TOKEN=[your-project-token]
$ percy exec -- [java test command]
[percy] Percy has started!
[percy] Created build #1: https://percy.io/[your-project]
[percy] Snapshot taken "Java example"
[percy] Stopping percy...
[percy] Finalized build #1: https://percy.io/[your-project]
[percy] Done!
```

## Configuration

The snapshot method arguments:

`percy.snapshot(name, widths[], minHeight, enableJavaScript, percyCSS, scope)`

- `name` (**required**) - The snapshot name; must be unique to each snapshot
- Additional snapshot options (overrides any project options):
  - `widths` - An array of widths to take screenshots at
  - `minHeight` - The minimum viewport height to take screenshots at
  - `enableJavaScript` - Enable JavaScript in Percy's rendering environment
  - `percyCSS` - Percy specific CSS only applied in Percy's rendering
    environment
  - `scope` - A CSS selector to scope the screenshot to


## Upgrading

### Automatically with `@percy/migrate`

We built a tool to help automate migrating to the new CLI toolchain! Migrating
can be done by running the following commands and following the prompts:

``` shell
$ npx @percy/migrate
? Are you currently using percy-java-selenium? Yes
? Install @percy/cli (required to run percy)? Yes
? Migrate Percy config file? Yes
```

This will automatically run the changes described below for you.

### Manually

#### Installing `@percy/cli` & removing `@percy/agent`

If you're coming from a pre-3.0 version of this package, make sure to install `@percy/cli` after
upgrading to retain any existing scripts that reference the Percy CLI
command. You will also want to uninstall `@percy/agent`, as it's been replaced
by `@percy/cli`.

```sh-session
$ npm uninstall @percy/agent
$ npm install --save-dev @percy/cli
```

### Migrating Config

If you have a previous Percy configuration file, migrate it to the newest version with the
[`config:migrate`](https://github.com/percy/cli/tree/master/packages/cli-config#percy-configmigrate-filepath-output) command:

```sh-session
$ percy config:migrate
```
