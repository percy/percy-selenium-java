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

`percy.snapshot(name, widths[], minHeight, enableJavaScript, percyCSS, scope, sync, responsiveSnapshotCapture)`

- `name` (**required**) - The snapshot name; must be unique to each snapshot
- Additional snapshot options (overrides any project options):
  - `widths` - An array of widths to take screenshots at
  - `minHeight` - The minimum viewport height to take screenshots at
  - `enableJavaScript` - Enable JavaScript in Percy's rendering environment
  - `percyCSS` - Percy specific CSS only applied in Percy's rendering
    environment
  - `scope` - A CSS selector to scope the screenshot to
  - `sync` - For getting syncronous results https://www.browserstack.com/docs/percy/advanced/sync-comparison-results
  - `responsiveSnapshotCapture` - For capturing snapshot of responsive websites


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
  - `sync` - Boolean value by default it falls back to false, Gives the processed result around screenshot [From CLI v1.28.0-beta.0+]
  - `fullPage` - Boolean value by default it falls back to `false`, Takes full page screenshot [From CLI v1.27.6+]
  - `freezeAnimatedImage` - Boolean value by default it falls back to `false`, you can pass `true` and percy will freeze image based animations.
  - `freezeImageBySelectors` - List of selectors. Images will be freezed which are passed using selectors. For this to work `freezeAnimatedImage` must be set to true.
  - `freezeImageByXpaths` - List of xpaths. Images will be freezed which are passed using xpaths. For this to work `freezeAnimatedImage` must be set to true.
  - `percyCSS` - Custom CSS to be added to DOM before the screenshot being taken. Note: This gets removed once the screenshot is taken.
  - `ignoreRegionXpaths` - List of xpaths. elements in the DOM can be ignored using xpath
  - `ignoreRegionSelectors` - List of selectors. elements in the DOM can be ignored using selectors.
  - `ignoreRegionSeleniumElements` - List of selenium web-element. elements can be ignored using selenium_elements.
  - `customIgnoreRegions` - List of custom objects. elements can be ignored using custom boundaries
    - Refer to example -
      - ```
          List<HashMap> customRegion = new ArrayList<>();
          HashMap<String, Integer> region1 = new HashMap<>();
          region1.put("top", 10);
          region1.put("bottom", 110);
          region1.put("right", 10);
          region1.put("left", 120);
          customRegion.add(region1);
          options.put("custom_ignore_regions", customRegion);
        ```
    - Parameters:
      - `top` (int): Top coordinate of the ignore region.
      - `bottom` (int): Bottom coordinate of the ignore region.
      - `left` (int): Left coordinate of the ignore region.
      - `right` (int): Right coordinate of the ignore region.
  - `considerRegionXpaths` - List of xpaths. elements in the DOM can be considered for diffing and will be ignored by Intelli Ignore using xpaths.
  - `considerRegionSelectors` - List of selectors. elements in the DOM can be considered for diffing and will be ignored by Intelli Ignore using selectors.
  - `considerRegionSeleniumElements` - List of selenium web-element. elements can be considered for diffing and will be ignored by Intelli Ignore using selenium_elements.
  - `customConsiderRegions` - List of custom objects. elements can be considered for diffing and will be ignored by Intelli Ignore using custom boundaries
    - Refer to example -
      - ```
          List<HashMap> customRegion = new ArrayList<>();
          HashMap<String, Integer> region2 = new HashMap<>();
          region2.put("top", 10);
          region2.put("bottom", 110);
          region2.put("right", 10);
          region2.put("left", 120);
          customRegion.add(region2);
          options.put("custom_consider_regions", customRegion);
        ```
      - Parameters:
        - `top` (int): Top coordinate of the consider region.
        - `bottom` (int): Bottom coordinate of the consider region.
        - `left` (int): Left coordinate of the consider region.
        - `right` (int): Right coordinate of the consider region.
    - `regions` parameter that allows users to apply snapshot options to specific areas of the page. This parameter is an array where each object defines a custom region with configurations.
      - Parameters:
        - `elementSelector` (mandatory, only one of the following must be provided)
            - `boundingBox` (object): Defines the coordinates and size of the region.
              - `x` (number): X-coordinate of the region.
              - `y` (number): Y-coordinate of the region.
              - `width` (number): Width of the region.
              - `height` (number): Height of the region.
            - `elementXpath` (string): The XPath selector for the element.
            - `elementCSS` (string): The CSS selector for the element.
        - `padding` (optional)
            - Specifies additional padding around the selected region.
            - Properties:
              - `top` (number): Padding from the top.
              - `left` (number): Padding from the left.
              - `right` (number): Padding from the right.
              - `bottom` (number): Padding from the bottom.
        - `algorithm` (mandatory)
            - Specifies the snapshot comparison algorithm.
            - Allowed values: `standard`, `layout`, `ignore`, `intelliignore`.
        - `configuration` (required for `standard` and `intelliignore` algorithms, ignored otherwise)
            - `diffSensitivity` (number): Sensitivity level for detecting differences.
            - `imageIgnoreThreshold` (number): Threshold for ignoring minor image differences.
            - `carouselsEnabled` (boolean): Whether to enable carousel detection.
            - `bannersEnabled` (boolean): Whether to enable banner detection.
            - `adsEnabled` (boolean): Whether to enable ad detection.
         - `assertion` (optional)
            - Defines assertions to apply to the region.
            - `diffIgnoreThreshold` (number): The threshold for ignoring minor differences.
### Example Usage for regions
```
        Map<String, Object> elementSelector = new HashMap<>();
        elementSelector.put("elementCSS", ".ad-banner");

        Map<String, Object> padding = new HashMap<>();
        padding.put("top", 10);
        padding.put("left", 20);
        padding.put("right", 15);
        padding.put("bottom", 10);

        Map<String, Object> configuration = new HashMap<>();
        configuration.put("diffSensitivity", 2);
        configuration.put("imageIgnoreThreshold", 0.2);
        configuration.put("carouselsEnabled", true);
        configuration.put("bannersEnabled", true);
        configuration.put("adsEnabled", true);

        Map<String, Object> assertion = new HashMap<>();
        assertion.put("diffIgnoreThreshold", 0.4);

        Map<String, Object> obj1 = new HashMap<>();
        obj1.put("elementSelector", elementSelector);
        obj1.put("padding", padding);
        obj1.put("algorithm", "intelliignore");
        obj1.put("configuration", configuration);
        obj1.put("assertion", assertion);

        List<Map<String, Object>> regions = Collections.singletonList(obj1);

        percy.snapshot("Homepage", regions); 

```

### Creating Percy on automate build
Note: Automate Percy Token starts with `auto` keyword. The command can be triggered using `exec` keyword.
```sh-session
$ export PERCY_TOKEN=[your-project-token]
$ percy exec -- [java test command]
[percy] Percy has started!
[percy] [Java example] : Starting automate screenshot ...
[percy] Screenshot taken "Java example"
[percy] Stopping percy...
[percy] Finalized build #1: https://percy.io/[your-project]
[percy] Done!
```

Refer to docs here: [Percy on Automate](https://www.browserstack.com/docs/percy/integrate/functional-and-visual)
