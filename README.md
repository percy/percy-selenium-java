# percy-java-selenium

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.percy/percy-java-selenium/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.percy/percy-java-selenium)
![Test](https://github.com/percy/percy-java-selenium/workflows/Test/badge.svg)

[Percy](https://percy.io) visual testing for Java Selenium.

## Development

Install/update `@percy/cli` dev dependency (requires Node 14+):

```sh-session
$ npm install --save-dev @percy/cli
```

Install maven:

```sh-session
$ brew install mvn
```

Run tests:

```
npm test
```

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
  <version>1.2.0</version>
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

## Running Percy on Automate
`percy.screenshot(driver, name, options)` [ needs @percy/cli 1.27.0-beta.0+ ];

This is an example test using the `percy.screenshot` method.

``` java
// import ...
import io.percy.selenium.Percy;

public class Example {

  public static void main(String[] args) throws MalformedURLException, InterruptedException {
    DesiredCapabilities caps = new DesiredCapabilities();
    // Add caps here

    WebDriver driver = new RemoteWebDriver(new URL(URL), caps);

    Percy percy = new Percy(driver);
    percy.screenshot("Screenshot 1");
    driver.quit();
  }
}
```

- `driver` (**required**) - A selenium driver instance
- `name` (**required**) - The screenshot name; must be unique to each screenshot
- `options` (**optional**) - There are various options supported by percy.screenshot to server further functionality.
  - `freezeAnimation` - Boolean value by default it falls back to `false`, you can pass `true` and percy will freeze image based animations.
  - `percyCSS` - Custom CSS to be added to DOM before the screenshot being taken. Note: This gets removed once the screenshot is taken.
  - `ignoreRegionXpaths` - elements in the DOM can be ignored using xpath
  - `ignoreRegionSelectors` - elements in the DOM can be ignored using selectors.
  - `ignoreRegionSeleniumElements` - elements can be ignored using selenium_elements.
  - `customIgnoreRegions` - elements can be ignored using custom boundaries
    - IgnoreRegion:-
      - Description: This class represents a rectangular area on a screen that needs to be ignored for visual diff.

      - Constructor:
        ```
        init(self, top, bottom, left, right)
        ```
      - Parameters:
        `top` (int): Top coordinate of the ignore region.
        `bottom` (int): Bottom coordinate of the ignore region.
        `left` (int): Left coordinate of the ignore region.
        `right` (int): Right coordinate of the ignore region.
      - Raises:ValueError: If top, bottom, left, or right is less than 0 or top is greater than or equal to bottom or left is greater than or equal to right.
      - valid: Ignore region should be within the boundaries of the screen.

### Creating Percy on automate build
Note: Automate Percy Token starts with `auto` keyword. The command can be triggered using `exec` keyword.
```sh-session
$ export PERCY_TOKEN=[your-project-token]
$ percy exec -- [python test command]
[percy] Percy has started!
[percy] [Python example] : Starting automate screenshot ...
[percy] Screenshot taken "Python example"
[percy] Stopping percy...
[percy] Finalized build #1: https://percy.io/[your-project]
[percy] Done!
```

Refer to docs here: [Percy on Automate](https://docs.percy.io/docs/integrate-functional-testing-with-visual-testing)
