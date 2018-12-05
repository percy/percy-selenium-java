## To make a release

1. One-time setup:
    1. Install GPG and import the private signing key.
    2. Create a local file `~/.m2/settings.xml` containing our OSSRH credentials.
    3. If you have 2-factor auth on your GitHub account, make sure you have a GitHub personal access token for authenticating on the command line over HTTPS.
2. Run `./release.sh`.
3. Follow the prompts to set new versions and tags. The Maven release process will make tag and release commits to GitHub on your behalf. You will also be prompted for the GPG key passphrase, and for
4. Verify that your new release has been successfully uploaded at https://oss.sonatype.org/content/repositories/releases/io/percy/
5. Verify the release tags on GitHub: https://github.com/percy/percy-java-selenium/releases

----------

For more details on the steps above, and for instructions on more manual release that allows you to inspect contents before promoting your staged release, see the sections below.

- [To make a release](#to-make-a-release)
- [Requirements](#requirements)
  - [Install GPG](#install-gpg)
  - [Import the private signing key](#import-the-private-signing-key)
  - [Create a local settings.xml with our Sonatype credentials](#create-a-local-settingsxml-with-our-sonatype-credentials)
  - [Create a GitHub personal access token](#create-a-github-personal-access-token)
- [Making a full release from the command line](#making-a-full-release-from-the-command-line)
- [Making a new deployment, inspecting and releasing manually in the Nexus Registry](#making-a-new-deployment-inspecting-and-releasing-manually-in-the-nexus-registry)
  - [Build the artifacts and upload to the Sonatype staging repository](#build-the-artifacts-and-upload-to-the-sonatype-staging-repository)
  - [Release your staging repository](#release-your-staging-repository)
- [Creating a development release (aka 'snapshot')](#creating-a-development-release-aka-snapshot)
- [Troubleshooting and more resources](#troubleshooting-and-more-resources)
  - [On GPG key management](#on-gpg-key-management)
  - [Maven Central / Sonatype OSSRH documentation](#maven-central--sonatype-ossrh-documentation)

## Requirements

### Install GPG

Maven will rely on your local GPG installation for signing the release. To install GPG on a Mac:

```bash
$ brew install gpg
```

You can also download installers from http://www.gnupg.org/download/ .

### Import the private signing key

Download the private key from 1password. It is a file attached to the secure note titled "Java release private key".

Import the key into your local gpg keyring:

```bash
$ gpg --import secret-percy-release-key.asc
```

It will prompt you for the key passphrase, which you can also find in 1password. Search for "Java release private key".

### Create a local settings.xml with our Sonatype credentials

Create a local `settings.xml` file, placed in `~/.m2/settings.xml`, which will contain the credentials to upload artifacts to OSSRH. The minimal contents of the file should be as follows:

```xml
<settings>
  <servers>
    <server>
      <!-- this server id has to match the id used in the repository section of our pom.xml -->
      <id>ossrh</id>
      <username>USERNAME</username>
      <password>USER_TOKEN</password>
    </server>
  </servers>
</settings>
```

The username and token can be generated from https://oss.sonatype.org. Log in with the percy-io credentials, then go to the top right menu (appears clicking on your username) > Profile > select "User token" from the dropdown that also has a "Summary" section. Hit "Access User Token" to get the username and token to use in this file. The token can also be regenerated from this UI, should that ever be necessary.

For detailed documentation on the format of `settings.xml`, see: http://maven.apache.org/ref/3.6.0/maven-settings/settings.html

### Create a GitHub personal access token

The release process will commit and push to GitHub updated version numbers and tags. For this, it will require your GitHub username and password. If you have two-factor authentication on your GitHub account, you will need to get a personal access token from GitHub, and use that instead of your password when prompted during the release process.

To create a GitHub personal access token, follow the instructions here: https://help.github.com/articles/creating-a-personal-access-token-for-the-command-line/

## Making a full release from the command line

To perform a new automated release, updating versions and tags:

```bash
$ ./release.sh
```

Follow the prompts to provide new version numbers and tags.

## Making a new deployment, inspecting and releasing manually in the Nexus Registry

### Build the artifacts and upload to the Sonatype staging repository

To make a new release, edit the version number in pom.xml to be the version number that you want, then run:

```bash
$ export GPG_TTY=$(tty) && mvn clean deploy
```

This will create a release candidate, and upload it to the staging servers for Maven Central.

### Release your staging repository

Log into Sonatype's Nexus Repository Manager to "close" your staging repository and then release it. The instructions on how to do so are here: https://central.sonatype.org/pages/releasing-the-deployment.html

## Creating a development release (aka 'snapshot')

Set the version number in the POM to something ending in `-SNAPSHOT` and then run `mvn deploy`. This will create the corresponding development snapshot version and upload it to the snapshots repository.

Since this is a development release, there is no need to "promote" the release or follow any additional steps. The package will be available for download from the snaphosts repository. You can check the pom.xml or the output of `mvn deploy` for the public URL to the repository, from which the JARs and other artifacts can be downloaded.

This can be useful for testing JARs without doing a full release.

## Troubleshooting and more resources

If something is not quite right, you can re-run any `mvn` command with the `-X` switch to see detailed debug logs and stack traces.

### On GPG key management

The private key has an expiry date. When it expires, it can be either extended, or a new key-pair can be created.

If the secret key or passphrase gets lost, a new keypair can be created and the new public key distributed.

Detailed instructions on creating a new keypair or extending the validity of an expired key can be found here: https://central.sonatype.org/pages/working-with-pgp-signatures.html

Should it be necessary, the private key's revocation certificate can also be found in 1password (search for "Java release private key").

### Maven Central / Sonatype OSSRH documentation

The full instructions for releasing artifacts to Maven Central can be found here: https://central.sonatype.org/pages/ossrh-guide.html
