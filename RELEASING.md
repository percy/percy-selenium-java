# Release instructions

- [Release instructions](#release-instructions)
  - [Requirements](#requirements)
    - [Install GPG](#install-gpg)
    - [Import the private signing key](#import-the-private-signing-key)
    - [Create a local settings.xml with our Sonatype credentials and GPG passphrase](#create-a-local-settingsxml-with-our-sonatype-credentials-and-gpg-passphrase)
  - [Making a new release](#making-a-new-release)
    - [Build the artifacts and upload to the Sonatype staging repository](#build-the-artifacts-and-upload-to-the-sonatype-staging-repository)
    - [Release your staging repository](#release-your-staging-repository)
  - [Creating a development release (aka 'snapshot')](#creating-a-development-release-aka-snapshot)
  - [Troubleshooting notes and other resources](#troubleshooting-notes-and-other-resources)
    - [On GPG key management](#on-gpg-key-management)
    - [Full Maven Central / Sonatype documentation](#full-maven-central--sonatype-documentation)

## Requirements

### Install GPG

Maven will rely on your local GPG installation for signing the release. On a Mac:

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

It will prompt you for the key passphrase, which you can also find in 1password.

### Create a local settings.xml with our Sonatype credentials and GPG passphrase

Create a local `settings.xml` file, placed by default in `~/.m2/settings.xml`, which will contain the credentials you'll need to upload artifacts to OSSRH. The minimal contents of the file should be as follows:

```xml
<settings>
  <servers>
    <server>
      <!-- this server id has to match the id used in the repository section of our pom.xml -->
      <id>ossrh</id>
      <username>USERNAME</username>
      <password>USER_TOKEN</password>
    </server>
    <server>
      <id>gpg.passphrase</id>
      <passphrase>PASSPHRASE</passphrase>
    </server>
  </servers>
</settings>
```

The username and token can be generated from https://oss.sonatype.org. Log in with the percy-io credentials, then go to the top right menu (appears clicking on your username) > Profile > select "User token" from the dropdown that also has a "Summary" section. Hit "Access User Token" to get the username and token to use in this file. The token can also be regenerated from this UI, should that ever be necessary.

The passhprase is the GPG secret key's passphrase, which you can find in 1password.

For detailed documentation on the format of `settings.xml`, see: http://maven.apache.org/ref/3.6.0/maven-settings/settings.html

## Making a new release

### Build the artifacts and upload to the Sonatype staging repository

To make a new release, edit the version number in pom.xml to be the version number that you want, then run:

```bash
$ mvn clean deploy
```

This will create a release candidate, and upload it to the staging servers for Maven Central.

TODO: add instructions for logging in, closing the staging repo and promoting it to release.
TODO: add link to video that demonstrates how to do that.

### Release your staging repository

You'll need to log into Sonatype's Nexus Repository Manager to "close" your staging repository and then release it. The instructions on how to do so are here: https://central.sonatype.org/pages/releasing-the-deployment.html

## Creating a development release (aka 'snapshot')

Setting the version number in the POM to something ending in `-SNAPSHOT` and then running `mvn deploy` will create the corresponding development snapshot version and upload it to the snapshots repository. This can be useful for distributing a development version of the library without doing a full release.

Since this is a development release, there is no need to "promote" the release or follow any additional steps. The package will be available for download from the snaphosts repository. You can check the pom.xml or the output of `mvn deploy` for the public URL to the repository, from which the JARs and other artifacts can be downloaded.

## Troubleshooting notes and other resources

If something is not quite right, you can re-run any `mvn` command with the `-X` switch to see detailed debug logs and stack traces.

### On GPG key management

The key has an expiry date. When it expires, it can be either extended, or a new key-pair can be created.

If the secret key or passphrase gets lost, a new keypair can be created and the new public key distributed.

Detailed instructions on creating a new keypair or extending the validity of an expired key can be found here: https://central.sonatype.org/pages/working-with-pgp-signatures.html

### Full Maven Central / Sonatype documentation

The full instructions for releasing artifacts to Maven Central can be found here: https://central.sonatype.org/pages/ossrh-guide.html
