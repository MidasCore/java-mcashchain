# How to Build

## Prepare dependencies

* JDK 1.8 (JDK 1.9+ are not supported yet)
* On Linux Ubuntu system (e.g. Ubuntu 18.04 LTS), ensure that the machine has [__Oracle JDK 8__](https://www.digitalocean.com/community/tutorials/how-to-install-java-with-apt-get-on-ubuntu-16-04), instead of having __Open JDK 8__ in the system. If you are building the source code by using __Open JDK 8__, you will get [__Build Failed__](https://github.com/MidasCore/java-mcashchain/issues/337) result.
* Open **UDP** ports for connection to the network
* **MINIMUM** 2 ENERGY Cores

## Getting the code with git

* Use Git from the Terminal, see the [Setting up Git](https://help.github.com/articles/set-up-git/) and [Fork a Repo](https://help.github.com/articles/fork-a-repo/) articles.
* develop branch: the newest code 
* master branch: more stable than develop.
In the shell command, type:
```bash
git clone https://github.com/MidasCore/java-mcashchain.git
git checkout -t origin/master
```

* For Mac, you can also install **[GitHub for Mac](https://mac.github.com/)** then **[fork and clone our repository](https://guides.github.com/activities/forking/)**. 

* If you'd rather not use Git, [Download the ZIP](https://github.com/MidasCore/java-mcashchain/archive/develop.zip)

## Including java-mcashchain as dependency

* If you don't want to checkout the code and build the project, you can include it directly as a dependency

**Using gradle:**

```
repositories {
   maven { url 'https://jitpack.io' }
}
dependencies {
   implementation 'com.github.MidasCore:java-mcashchain:develop-SNAPSHOT'
}
```
  
**Using maven:**

```xml
...
<repositories>
  <repository>    
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
</repositories>
...
<dependency>
    <groupId>com.github.MidasCore</groupId>
    <artifactId>java-mcashchain</artifactId>
    <version>develop-SNAPSHOT</version><!--You can use any of the tag/branch name available-->
</dependency>
```




## Building from source code

* Build in the Terminal

```bash
cd java-mcashchain
./gradlew build
```


* Build in [IntelliJ IDEA](https://www.jetbrains.com/idea/) (community version is enough):

  **Please run ./gradlew build once to build the protocol files**

  1. Start IntelliJ. Select `File` -> `Open`, then locate to the java-mcashchain folder which you have git cloned to your local drive. Then click `Open` button on the right bottom.
  2. Check on `Use auto-import` on the `Import Project from Gradle` dialog. Select JDK 1.8 in the `Gradle JVM` option. Then click `OK`.
  3. IntelliJ will open the project and start gradle syncing, which will take several minutes, depending on your network connection and your IntelliJ configuration
  4. Enable Annotations, `Preferences` -> Search `annotations` -> check `Enable Annotation Processing`.
  5. After the syncing finished, select `Gradle` -> `Tasks` -> `build`, and then double click `build` option.
  