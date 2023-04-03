---
title: "Coding standards"
date: 2019-02-23T17:24:10-05:00
draft: false
weight: 5
---


This project has adopted the following coding standards:

* Code will be formated using Oracle / WebLogic standards, which are identical to the [Google Java Style](https://google.github.io/styleguide/javaguide.html).
* Javadoc must be provided for all public packages, classes, and methods, and must include all parameters and returns.  Javadoc is not required for methods that override or implement methods that are already documented.
* All non-trivial methods should include `LOGGER.entering()` and `LOGGER.exiting()` calls.
* The `LOGGER.exiting()` call should include the value that is going to be returned from the method, unless that value includes a credential or other sensitive information.
* All logged messages must be internationalized using the resource bundle `src/main/resources/Operator.properties` and using a key itemized in `src/main/java/oracle/kubernetes/operator/logging/MessageKeys.java`.
* After operator initialization, all operator work must be implemented using the asynchronous call model (described below).  In particular, worker threads must not use `sleep()` or IO or lock-based blocking methods.

### Code formatting plugins

The following IDE plugins are available to assist with following the code formatting standards

#### IntelliJ

An [IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527) is available from the plugin repository.

The plugin will be enabled by default. To disable it in the current project, go to `File > Settings... > google-java-format Settings` (or `IntelliJ IDEA > Preferences... > Other Settings > google-java-format Settings` on macOS) and uncheck the "Enable google-java-format" checkbox.

To disable it by default in new projects, use `File > Other Settings > Default Settings...`.

When enabled, it will replace the normal "Reformat Code" action, which can be triggered from the "Code" menu or with the Ctrl-Alt-L (by default) keyboard shortcut.

The import ordering is not handled by this plugin, unfortunately. To fix the import order, download the [IntelliJ Java Google Style file](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml) and import it into File→Settings→Editor→Code Style.

#### Eclipse

An [Eclipse plugin](https://github.com/google/google-java-format/releases/download/google-java-format-1.3/google-java-format-eclipse-plugin-1.3.0.jar) can be downloaded from the releases page. Drop it into the Eclipse [drop-ins folder](http://help.eclipse.org/neon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fmisc%2Fp2_dropins_format.html) to activate the plugin.

The plugin adds a google-java-format formatter implementation that can be configured in `Eclipse > Preferences > Java > Code Style > Formatter > Formatter Implementation`.
